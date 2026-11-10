# ============================================================
# P1 冒烟测试：社区团购（开团 / 参团 / 成团 / 核销 / 流团退款）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/smoke-p1.ps1
# 前置：ResiHub 在 8082 启动；建议先执行 db/init.sql 重置数据
# ============================================================
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8082'
$redis = 'C:\Program Files\Redis\redis-cli.exe'
$mysql = 'D:\Mysql\mysql-8.0.44-winx64\bin\mysql.exe'
$pass = 0; $fail = 0

function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Host ("  [PASS] {0}" -f $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  [FAIL] {0} -> {1}" -f $name, ($detail | Out-String).Trim()) -ForegroundColor Red }
}

function Api($method, $path, $token, $body) {
    $p = @{ Uri = "$base$path"; Method = $method; TimeoutSec = 20; UseBasicParsing = $true }
    if ($token) { $p.Headers = @{ authorization = $token } }
    if ($body) { $p.Body = ($body | ConvertTo-Json -Compress); $p.ContentType = 'application/json; charset=utf-8' }
    try {
        $r = Invoke-WebRequest @p
        return [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    } catch {
        $resp = $_.Exception.Response
        if ($resp) { return "HTTP$([int]$resp.StatusCode)" }
        return "ERR:$($_.Exception.Message)"
    }
}

function Json($method, $path, $token, $body) {
    $raw = Api $method $path $token $body
    try { return $raw | ConvertFrom-Json } catch { Write-Host ("    !! 非 JSON: {0}" -f $raw) -ForegroundColor DarkYellow; return $null }
}

function Login($phone) {
    Api 'POST' "/user/code?phone=$phone" | Out-Null
    $code = (& $redis -p 6380 -n 1 get "login:code:$phone").Trim()
    $r = Json 'POST' '/user/login' $null @{ phone = $phone; code = $code }
    if ($null -eq $r) { return $null }
    return $r.data
}

function EnsureResident($phone, $building, $room, $managerToken) {
    $t = Login $phone
    Json 'POST' "/user/apply?communityId=1&building=$building&room=$room" $t | Out-Null
    $me = (Json 'GET' '/user/me' $t).data
    if ($me.audit -ne 1) {
        $pending = (Json 'GET' '/user/audit/list' $managerToken).data
        if ($pending | Where-Object { $_.id -eq $me.id }) {
            Json 'POST' "/user/audit/$($me.id)/true" $managerToken | Out-Null
        }
    }
    return $t
}

function Sql($q) {
    return (& $mysql -uroot -p990829 -D resihub -N -B -e $q 2>&1 | Where-Object { $_ -notmatch 'Warning' }) -join ' '
}

Write-Host "`n== 0. 准备账号" -ForegroundColor Cyan
$tManager = Login '13900000002'
$tZhang = Login '13900000004'
$tLi = Login '13900000005'
$tWang = EnsureResident '13900007777' '7幢' '1201' $tManager
Check '四个账号登录成功' (($tManager -and $tZhang -and $tLi -and $tWang) -ne $null) ''

Write-Host "`n== 1. 商品列表与详情" -ForegroundColor Cyan
$products = (Json 'GET' '/product/list' $tZhang).data
Check '本小区商品 5 个' ($products.Count -eq 5) $products.Count
$stock5Before = [int](Sql "select stock from tb_product where id=5")
$p3a = (Json 'GET' '/product/3' $tZhang).data
$p3b = (Json 'GET' '/product/3' $tZhang).data
Check '商品详情名称正确' ($p3a.name -eq '有机绿叶蔬菜套餐') $p3a.name
Check '第二次走缓存仍正确' ($p3b.id -eq 3) $p3b.id
$cacheHit = (& $redis -p 6380 -n 1 exists 'cache:community:product:3').Trim()
Check '商品详情已写入 Redis 缓存' ($cacheHit -eq '1') $cacheHit

Write-Host "`n== 2. 开团规则" -ForegroundColor Cyan
$dup = Json 'POST' '/group-buy?productId=1&minUsers=2' $tZhang
Check '同商品已有进行中团购时拒绝重复开团' ($dup.success -eq $false -and $dup.errorMsg -like '*进行中*') $dup.errorMsg
$openRes = Json 'POST' '/group-buy?productId=5&minUsers=99&hours=1&pickupPoint=北门快递柜' $tZhang
Check '开团成功（商品 5）' ($openRes.success -eq $true) ($openRes | ConvertTo-Json -Compress)
$gbFail = $openRes.data
$gbFailDetail = (Json 'GET' "/group-buy/$gbFail" $tZhang).data
Check '团详情含商品信息' ($gbFailDetail.product.name -eq '手工鲜肉水饺') $gbFailDetail.product.name
Check '新团状态=进行中(0)' ($gbFailDetail.status -eq 0) $gbFailDetail.status
Check '自提点已保存' ($gbFailDetail.pickupPoint -eq '北门快递柜') $gbFailDetail.pickupPoint

Write-Host "`n== 3. 参团（Lua 扣名额 + 异步落库）" -ForegroundColor Cyan
$joinRes = Json 'POST' "/group-buy/$gbFail/join" $tZhang
Check '参团接口返回订单号' ($joinRes.success -eq $true -and $joinRes.data.Length -gt 10) ($joinRes | ConvertTo-Json -Compress)
$orderNo = $joinRes.data
Start-Sleep -Seconds 2
$myOrders = (Json 'GET' '/order/my' $tZhang).data
$myOrder = $myOrders | Where-Object { $_.orderNo -eq $orderNo }
Check '异步落库生成订单' ($null -ne $myOrder) (($myOrders | ForEach-Object { $_.orderNo }) -join ',')
Check '订单初始状态=待成团(0)' ($myOrder.status -eq 0) $myOrder.status
Check '订单带 6 位自提码' ($myOrder.pickupCode.Length -eq 6) $myOrder.pickupCode
$dupJoin = Json 'POST' "/group-buy/$gbFail/join" $tZhang
Check '同一用户重复参团被拒' ($dupJoin.success -eq $false -and $dupJoin.errorMsg -like '*参加过*') $dupJoin.errorMsg
$gbFailDetail2 = (Json 'GET' "/group-buy/$gbFail" $tZhang).data
Check '团进度 currentUsers=1' ($gbFailDetail2.currentUsers -eq 1) $gbFailDetail2.currentUsers
Check '当前用户 joined=true' ($gbFailDetail2.joined -eq $true) $gbFailDetail2.joined

Write-Host "`n== 4. 成团（3 人团）" -ForegroundColor Cyan
$stockBefore = [int](Sql "select stock from tb_product where id=3")
$join1 = (Json 'POST' '/group-buy/3/join' $tZhang).data
$join2 = (Json 'POST' '/group-buy/3/join' $tLi).data
$join3raw = Json 'POST' '/group-buy/3/join' $tWang
$join3 = $join3raw.data
if (-not $join3) { Write-Host ("    第三人参团返回: " + ($join3raw | ConvertTo-Json -Compress)) -ForegroundColor DarkYellow }
Check '三人参团均返回订单号' ($join1 -and $join2 -and $join3) "$join1 / $join2 / $join3"
Start-Sleep -Seconds 3
$gb3 = (Json 'GET' '/group-buy/3' $tZhang).data
Check '达到人数后团状态=已成团(1)' ($gb3.status -eq 1) $gb3.status
Check '团进度 currentUsers=3' ($gb3.currentUsers -eq 3) $gb3.currentUsers
$stockAfter = [int](Sql "select stock from tb_product where id=3")
Check '商品库存扣减 3' ($stockAfter -eq $stockBefore - 3) "$stockBefore -> $stockAfter"
$orders3 = (Json 'GET' '/order/my' $tZhang).data | Where-Object { $_.groupBuyId -eq 3 }
Check '成团后订单状态=待自提(1)' ($orders3.status -eq 1) $orders3.status
$noti = (Json 'GET' '/notification/list' $tZhang).data
Check '收到成团通知' (@($noti | Where-Object { $_.title -like '*成团*' }).Count -ge 1) (($noti | ForEach-Object { $_.title }) -join ',')

Write-Host "`n== 5. 自提核销（一次性）" -ForegroundColor Cyan
$deny = Json 'POST' "/order/$($orders3.orderNo)/verify" $tWang
Check '非团长非管家不能核销' ($deny.success -eq $false -and $deny.errorMsg -like '*团长*') $deny.errorMsg
$v1 = Json 'POST' "/order/$($orders3.orderNo)/verify" $tLi
Check '团长核销成功' ($v1.success -eq $true) ($v1 | ConvertTo-Json -Compress)
$v2 = Json 'POST' "/order/$($orders3.orderNo)/verify" $tLi
Check '重复核销被拒' ($v2.success -eq $false) $v2.errorMsg
$after = (Json 'GET' "/order/$($orders3.orderNo)" $tZhang).data
Check '核销后订单状态=已完成(2)' ($after.status -eq 2) $after.status

Write-Host "`n== 6. 流团退款（把截团时间改到过去，等定时任务）" -ForegroundColor Cyan
Sql "update tb_group_buy set deadline = date_sub(now(), interval 1 minute) where id=$gbFail" | Out-Null
Write-Host "    等待定时任务（最多 25 秒）..."
$gbStatus = 0
for ($i = 0; $i -lt 25; $i++) {
    Start-Sleep -Seconds 1
    $gbStatus = [int](Sql "select status from tb_group_buy where id=$gbFail")
    if ($gbStatus -eq 2) { break }
}
Check '超时未成团 -> 团状态=已流团(2)' ($gbStatus -eq 2) $gbStatus
$failOrder = (Json 'GET' "/order/$orderNo" $tZhang).data
Check '订单自动退款状态=已退款(3)' ($failOrder.status -eq 3) $failOrder.status
$stock5After = [int](Sql "select stock from tb_product where id=5")
Check '流团后商品库存回滚到开团前' ($stock5After -eq $stock5Before) "$stock5Before -> $stock5After"
$refundNoti = (Json 'GET' '/notification/list' $tZhang).data
Check '收到流团退款通知' (@($refundNoti | Where-Object { $_.title -like '*退款*' }).Count -ge 1) (($refundNoti | ForEach-Object { $_.title }) -join ',')
$joinClosed = Json 'POST' "/group-buy/$gbFail/join" $tLi
Check '已流团的团不能继续参团' ($joinClosed.success -eq $false) $joinClosed.errorMsg

Write-Host "`n== 7. 未认证用户不能参团" -ForegroundColor Cyan
$tNoAudit = Login '13900006666'
$noAudit = Json 'POST' '/group-buy/2/join' $tNoAudit
Check '未通过业主认证不能参团' ($noAudit.success -eq $false -and $noAudit.errorMsg -like '*认证*') $noAudit.errorMsg

Write-Host "`n===== P1 冒烟结果：PASS=$pass FAIL=$fail =====" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
