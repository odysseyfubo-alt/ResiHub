# ============================================================
# P2 冒烟测试：报修工单（提单 / 抢单 / 完工 / 评价 / 超时升级）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/smoke-p2.ps1
# 前置：ResiHub 在 8082 启动
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
    return (Json 'POST' '/user/login' $null @{ phone = $phone; code = $code }).data
}

function Sql($q) {
    return (& $mysql -uroot -p990829 -D resihub -N -B -e $q 2>&1 | Where-Object { $_ -notmatch 'Warning' }) -join ' '
}

Write-Host "`n== 0. 账号准备" -ForegroundColor Cyan
$tZhang = Login '13900000004'    # 居民
$tLi = Login '13900000003'       # 维修工老李
$tManager = Login '13900000002'  # 管家
Check '三个角色登录成功' (($tZhang -and $tLi -and $tManager) -ne $null) ''

Write-Host "`n== 1. 居民提报修单" -ForegroundColor Cyan
$create = Json 'POST' '/work-order' $tZhang @{ type = '水电'; description = '厨房水槽下水道堵了，水下不去'; images = '' }
Check '提报修单成功' ($create.success -eq $true) ($create | ConvertTo-Json -Compress)
$woId = $create.data
$myList = (Json 'GET' '/work-order/my' $tZhang).data
$wo = $myList | Where-Object { $_.id -eq $woId }
Check '我的报修能看到该单' ($null -ne $wo) (($myList | ForEach-Object { $_.id }) -join ',')
Check '工单初始状态=待接单(0)' ($wo.status -eq 0) $wo.status
$zscore = (& $redis -p 6380 -n 1 zscore 'work:timeout' "$woId").Trim()
Check '工单进入超时升级队列' ($null -ne $zscore -and $zscore.Length -gt 0) $zscore
$miss = Json 'POST' '/work-order' $tZhang @{ type = ''; description = 'x' }
Check '缺类型时拒绝提单' ($miss.success -eq $false) $miss.errorMsg

Write-Host "`n== 2. 工单池与权限" -ForegroundColor Cyan
$pool = (Json 'GET' '/work-order/pool' $tLi).data
Check '维修工能看到待接单池' (@($pool | Where-Object { $_.id -eq $woId }).Count -eq 1) (($pool | ForEach-Object { $_.id }) -join ',')
$denyPool = Api 'GET' '/work-order/pool' $tZhang
Check '居民访问工单池被拒 403' ($denyPool -eq 'HTTP403') $denyPool
$denyBoard = Api 'GET' '/work-order/list' $tZhang
Check '居民访问工单看板被拒 403' ($denyBoard -eq 'HTTP403') $denyBoard

Write-Host "`n== 3. 抢单（Redisson 锁 + 状态条件更新）" -ForegroundColor Cyan
$accept = Json 'POST' "/work-order/$woId/accept" $tLi
Check '维修工抢单成功' ($accept.success -eq $true) ($accept | ConvertTo-Json -Compress)
$acceptAgain = Json 'POST' "/work-order/$woId/accept" $tManager
Check '第二个人抢同一单失败' ($acceptAgain.success -eq $false) $acceptAgain.errorMsg
$wo2 = (Json 'GET' "/work-order/$woId" $tZhang).data
Check '工单状态=处理中(1)' ($wo2.status -eq 1) $wo2.status
Check '工单已分配维修工' ($wo2.assigneeName -eq '维修工老李') $wo2.assigneeName
$zAfter = (& $redis -p 6380 -n 1 zscore 'work:timeout' "$woId").Trim()
Check '接单后移出超时队列' (-not $zAfter) $zAfter

Write-Host "`n== 4. 完工与评价" -ForegroundColor Cyan
$finishByOther = Json 'POST' "/work-order/$woId/finish" $tManager
Check '非接单人不能完工' ($finishByOther.success -eq $false) $finishByOther.errorMsg
$finish = Json 'POST' "/work-order/$woId/finish" $tLi
Check '维修工完工成功' ($finish.success -eq $true) ($finish | ConvertTo-Json -Compress)
$wo3 = (Json 'GET' "/work-order/$woId" $tZhang).data
Check '工单状态=待确认(2)' ($wo3.status -eq 2) $wo3.status
$rateEarly = Json 'POST' "/work-order/$woId/rate?rating=5" $tLi
Check '非报修人不能评价' ($rateEarly.success -eq $false) $rateEarly.errorMsg
$pointsBefore = [int](Sql "select points from tb_user where id=4")
$rate = Json 'POST' "/work-order/$woId/rate?rating=5&comment=师傅很快" $tZhang
Check '居民评价成功' ($rate.success -eq $true) ($rate | ConvertTo-Json -Compress)
$wo4 = (Json 'GET' "/work-order/$woId" $tZhang).data
Check '工单状态=已完成(3)' ($wo4.status -eq 3) $wo4.status
Check '评分已保存' ($wo4.rating -eq 5) $wo4.rating
$pointsAfter = [int](Sql "select points from tb_user where id=4")
Check '评价后居民积分 +5' ($pointsAfter -eq $pointsBefore + 5) "$pointsBefore -> $pointsAfter"
$rateAgain = Json 'POST' "/work-order/$woId/rate?rating=4" $tZhang
Check '重复评价被拒' ($rateAgain.success -eq $false) $rateAgain.errorMsg

Write-Host "`n== 5. 超时未接单自动升级" -ForegroundColor Cyan
$create2 = Json 'POST' '/work-order' $tZhang @{ type = '门窗'; description = '阳台纱窗破了需要更换' }
$woId2 = $create2.data
Check '第二张工单创建成功' ($null -ne $woId2) $woId2
$past = [string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - 5000)
& $redis -p 6380 -n 1 zadd 'work:timeout' $past "$woId2" | Out-Null
Write-Host "    把超时时间改到过去，等待定时任务（最多 15 秒）..."
$st = 0
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $st = [int](Sql "select status from tb_work_order where id=$woId2")
    if ($st -eq 4) { break }
}
Check '超时未接单 -> 状态=已超时升级(4)' ($st -eq 4) $st
$board = (Json 'GET' '/work-order/list?status=4' $tManager).data
Check '管家看板能看到升级工单' (@($board | Where-Object { $_.id -eq $woId2 }).Count -eq 1) (($board | ForEach-Object { $_.id }) -join ',')
$noti = (Json 'GET' '/notification/list' $tManager).data
Check '管家收到超时升级通知' (@($noti | Where-Object { $_.title -like '*超时*' }).Count -ge 1) (($noti | ForEach-Object { $_.title }) -join ',')

Write-Host "`n== 6. 维修工我的工单" -ForegroundColor Cyan
$assigned = (Json 'GET' '/work-order/assigned' $tLi).data
Check '维修工能看到自己接的单' (@($assigned | Where-Object { $_.id -eq $woId }).Count -eq 1) (($assigned | ForEach-Object { $_.id }) -join ',')

Write-Host "`n== 7. 未认证用户不能报修" -ForegroundColor Cyan
$tNo = Login '13900005555'
$noAudit = Json 'POST' '/work-order' $tNo @{ type = '其他'; description = 'test' }
Check '未认证用户不能报修' ($noAudit.success -eq $false -and $noAudit.errorMsg -like '*认证*') $noAudit.errorMsg

Write-Host "`n===== P2 冒烟结果：PASS=$pass FAIL=$fail =====" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
