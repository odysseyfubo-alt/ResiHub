# ============================================================
# P3 冒烟测试：邻里圈 / 社区活动 / 访客通行码 / 积分中心
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/smoke-p3.ps1
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

function Points($uid) { return [int](Sql "select points from tb_user where id=$uid") }

Write-Host "`n== 0. 账号" -ForegroundColor Cyan
$tZhang = Login '13900000004'   # 小区1 居民
$tLi = Login '13900000005'      # 小区1 居民
$tZhao = Login '13900000006'    # 小区2 居民
$tWorker = Login '13900000003'  # 小区1 维修工（可核销访客码）
$tManager = Login '13900000002' # 小区1 管家
Check '五个账号登录成功' (($tZhang -and $tLi -and $tZhao -and $tWorker -and $tManager) -ne $null) ''

Write-Host "`n== 1. 邻里圈：发帖 / 列表 / 分类筛选" -ForegroundColor Cyan
$pBefore = Points 4
$created = Json 'POST' '/post' $tZhang @{ category = '闲置'; title = '转卖闲置婴儿车'; content = '九成新，3 幢自提'; images = '' }
Check '发帖成功' ($created.success -eq $true) ($created | ConvertTo-Json -Compress)
$postId = $created.data
$pAfter = Points 4
Check '发帖后积分 +2' ($pAfter -eq $pBefore + 2) "$pBefore -> $pAfter"
$list = (Json 'GET' '/post/list' $tZhang).data
Check '本小区列表含新帖' (@($list | Where-Object { $_.id -eq $postId }).Count -eq 1) (($list | ForEach-Object { $_.id }) -join ',')
$cat = (Json 'GET' '/post/list?category=闲置' $tZhang).data
Check '分类筛选只返回闲置' (@($cat | Where-Object { $_.category -ne '闲置' }).Count -eq 0) (($cat | ForEach-Object { $_.category }) -join ',')
$zhaoList = (Json 'GET' '/post/list' $tZhao).data
Check '其他小区看不到该帖' (@($zhaoList | Where-Object { $_.id -eq $postId }).Count -eq 0) (($zhaoList | ForEach-Object { $_.id }) -join ',')
$miss = Json 'POST' '/post' $tZhang @{ category = '分享'; title = '' }
Check '缺标题时拒绝发帖' ($miss.success -eq $false) $miss.errorMsg

Write-Host "`n== 2. 邻里圈：点赞 / 评论" -ForegroundColor Cyan
$like1 = Json 'PUT' "/post/like/$postId" $tZhang
Check '点赞成功' ($like1.success -eq $true) ($like1 | ConvertTo-Json -Compress)
$d1 = (Json 'GET' "/post/$postId" $tZhang).data
Check '点赞数=1 且 isLike=true' ($d1.liked -eq 1 -and $d1.isLike -eq $true) "liked=$($d1.liked) isLike=$($d1.isLike)"
$like2 = Json 'PUT' "/post/like/$postId" $tZhang
$d2 = (Json 'GET' "/post/$postId" $tZhang).data
Check '再点一次取消点赞' ($d2.liked -eq 0 -and $d2.isLike -eq $false) "liked=$($d2.liked) isLike=$($d2.isLike)"
$cm = Json 'POST' "/post/$postId/comment?content=多少钱%3F" $tLi
Check '评论成功' ($cm.success -eq $true) ($cm | ConvertTo-Json -Compress)
$cms = (Json 'GET' "/post/$postId/comments" $tLi).data
Check '评论列表含内容' (@($cms | Where-Object { $_.content -like '*多少钱*' }).Count -eq 1) (($cms | ForEach-Object { $_.content }) -join ',')
$d3 = (Json 'GET' "/post/$postId" $tZhang).data
Check '帖子评论数=1' ($d3.comments -eq 1) $d3.comments
$hot = (Json 'GET' '/post/hot' $tZhang).data
Check '热门列表非空' ($hot.Count -ge 1) $hot.Count

Write-Host "`n== 3. 社区活动：名额限流 + 核销" -ForegroundColor Cyan
$start = (Get-Date).AddDays(2).ToString('yyyy-MM-ddTHH:mm:ss')
$end = (Get-Date).AddDays(2).AddHours(3).ToString('yyyy-MM-ddTHH:mm:ss')
$dayAfter = (Get-Date).AddDays(3).ToString('yyyy-MM-ddTHH:mm:ss')
$act = Json 'POST' '/activity' $tManager @{ title = '冒烟-限额活动'; content = '只有两个名额'; quota = 2; startTime = $start; endTime = $end }
Check '管家创建活动成功' ($act.success -eq $true) ($act | ConvertTo-Json -Compress)
$actId = $act.data
$denyCreate = Api 'POST' '/activity' $tZhang @{ title = '居民建活动'; quota = 1; startTime = $start; endTime = $end }
Check '居民不能创建活动 403' ($denyCreate -eq 'HTTP403') $denyCreate
$pBefore2 = Points 4
$sign1 = Json 'POST' "/activity/$actId/signup" $tZhang
Check '张三报名成功并拿到核销码' ($sign1.success -eq $true -and $sign1.data.Length -eq 6) ($sign1 | ConvertTo-Json -Compress)
$code = $sign1.data
$pAfter2 = Points 4
Check '报名获得 +3 积分' ($pAfter2 -eq $pBefore2 + 3) "$pBefore2 -> $pAfter2"
$dup = Json 'POST' "/activity/$actId/signup" $tZhang
Check '重复报名被拒' ($dup.success -eq $false) $dup.errorMsg
$sign2 = Json 'POST' "/activity/$actId/signup" $tLi
Check '李四报名成功（第 2 个名额）' ($sign2.success -eq $true) ($sign2 | ConvertTo-Json -Compress)
$sign3 = Json 'POST' "/activity/$actId/signup" $tWorker
Check '第 3 人报名被拒（名额已满）' ($sign3.success -eq $false -and $sign3.errorMsg -like '*名额*') $sign3.errorMsg
$denyVerify = Api 'POST' "/activity/verify?code=$code" $tZhang
Check '居民不能核销活动码 403' ($denyVerify -eq 'HTTP403') $denyVerify
$v1 = Json 'POST' "/activity/verify?code=$code" $tManager
Check '管家核销活动码成功' ($v1.success -eq $true) ($v1 | ConvertTo-Json -Compress)
$v2 = Json 'POST' "/activity/verify?code=$code" $tManager
Check '重复核销被拒' ($v2.success -eq $false) $v2.errorMsg
$myAct = (Json 'GET' '/activity/my' $tZhang).data
Check '我的活动含报名记录' (@($myAct | Where-Object { $_.id -eq $actId }).Count -eq 1) (($myAct | ForEach-Object { $_.id }) -join ',')

Write-Host "`n== 4. 访客通行码（一次性）" -ForegroundColor Cyan
$vp = Json 'POST' '/visit-pass?hours=2' $tZhang @{ visitorName = '快递员小张'; visitorPhone = '13700001111' }
Check '生成访客码成功' ($vp.success -eq $true -and $vp.data.Length -eq 8) ($vp | ConvertTo-Json -Compress)
$vcode = $vp.data
$ttl = (& $redis -p 6380 -n 1 ttl "visit:pass:$vcode").Trim()
Check '访客码写入 Redis 且带过期' ([int]$ttl -gt 0) $ttl
$vok = Json 'POST' "/visit-pass/verify?code=$vcode" $tWorker
Check '门岗核销成功并返回访客名' ($vok.success -eq $true -and $vok.data -eq '快递员小张') ($vok | ConvertTo-Json -Compress)
$vagain = Json 'POST' "/visit-pass/verify?code=$vcode" $tWorker
Check '同一码重复核销被拒' ($vagain.success -eq $false) $vagain.errorMsg
$vbad = Json 'POST' "/visit-pass/verify?code=00000000" $tWorker
Check '不存在的码核销失败' ($vbad.success -eq $false) $vbad.errorMsg
$myPass = (Json 'GET' '/visit-pass/my' $tZhang).data
$vpRow = $myPass | Where-Object { $_.code -eq $vcode }
Check '我的访客记录显示已使用' ($vpRow.used -eq 1) $vpRow.used

Write-Host "`n== 5. 积分中心：签到 / 连续天数 / 流水" -ForegroundColor Cyan
$signKey = "sign:4:" + (Get-Date).ToString("yyyyMM")
$signDay = (Get-Date).Day - 1
# 让脚本可重复执行：先清掉今天的签到位
[void](& $redis -p 6380 -n 1 setbit $signKey $signDay 0)
$sign = Json 'POST' '/points/sign' $tZhang
Check '首次签到成功(连续天数 >= 1)' ($sign.success -eq $true -and $sign.data -ge 1) ($sign | ConvertTo-Json -Compress)
$sign2x = Json 'POST' '/points/sign' $tZhang
Check '当天重复签到被拒' ($sign2x.success -eq $false) $sign2x.errorMsg
$cnt = (Json 'GET' '/points/sign/count' $tZhang).data
Check '连续签到天数 >= 1' ($cnt -ge 1) $cnt
$bit = (& $redis -p 6380 -n 1 keys "sign:4:*" | Select-Object -First 1)
Check '签到数据落在 Redis Bitmap' ($null -ne $bit) $bit
$logs = (Json 'GET' '/points/log' $tZhang).data
$types = ($logs | ForEach-Object { $_.type }) -join ','
Check '积分流水含 post/activity/sign' (($types -like '*post*') -and ($types -like '*activity*') -and ($types -like '*sign*')) $types
$total = (Json 'GET' '/points' $tZhang).data
$dbPoints = Points 4
Check '接口积分总额与库一致' ($total -eq $dbPoints) "$total / $dbPoints"

Write-Host "`n===== P3 冒烟结果：PASS=$pass FAIL=$fail =====" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
