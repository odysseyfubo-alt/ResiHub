# ============================================================
# P0 冒烟测试：登录 / 小区 / 业主认证 / 公告 / RBAC / 站内消息
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/smoke-p0.ps1
# 前置：ResiHub 在 8082 启动，Redis 在 6380（db1）
# 脚本可重复执行（每次新增几条公告，不影响断言）
# ============================================================
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8082'
$redis = 'C:\Program Files\Redis\redis-cli.exe'
$pass = 0; $fail = 0

function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Host ("  [PASS] {0}" -f $name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  [FAIL] {0} -> {1}" -f $name, ($detail | Out-String).Trim()) -ForegroundColor Red }
}

function Api($method, $path, $token, $body) {
    $p = @{ Uri = "$base$path"; Method = $method; TimeoutSec = 15; UseBasicParsing = $true }
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
    try { return $raw | ConvertFrom-Json } catch { Write-Host ("    !! 非 JSON 返回: {0}" -f $raw) -ForegroundColor DarkYellow; return $null }
}

function Login($phone) {
    Api 'POST' "/user/code?phone=$phone" | Out-Null
    $code = (& $redis -p 6380 -n 1 get "login:code:$phone").Trim()
    $r = Json 'POST' '/user/login' $null @{ phone = $phone; code = $code }
    if ($null -eq $r) { return $null }
    return $r.data
}

function NoticeIds($token) {
    $l = Json 'GET' '/notice/list' $token
    if ($null -eq $l -or $null -eq $l.data) { return @() }
    return @($l.data | ForEach-Object { $_.id })
}

Write-Host "`n== 1. 小区（免登录）" -ForegroundColor Cyan
$r = Json 'GET' '/community/list'
Check '小区列表返回 3 个' ($r.data.Count -eq 3) $r.data.Count
Check '小区名正确' ($r.data[0].name -eq '阳光花园') $r.data[0].name

Write-Host "`n== 2. 验证码登录 + 用户信息" -ForegroundColor Cyan
$tResident = Login '13900000004'
Check '居民登录拿到 token' ($null -ne $tResident -and $tResident.Length -eq 32) $tResident
$me = (Json 'GET' '/user/me' $tResident).data
Check '昵称=业主张三' ($me.nickName -eq '业主张三') $me.nickName
Check '归属小区=1' ($me.communityId -eq 1) $me.communityId
Check '角色=居民(0)' ($me.role -eq 0) $me.role
Check '认证=已通过(1)' ($me.audit -eq 1) $me.audit

Write-Host "`n== 3. 未登录访问受保护接口" -ForegroundColor Cyan
$anon = Api 'GET' '/notice/list'
Check '未带 token 返回 401' ($anon -eq 'HTTP401') $anon

Write-Host "`n== 4. 新用户提交业主认证（9 幢）" -ForegroundColor Cyan
$tNew = Login '13900008888'
$apply = Json 'POST' '/user/apply?communityId=1&building=9幢&room=0601' $tNew
Check '提交认证申请成功' ($apply.success -eq $true) ($apply | ConvertTo-Json -Compress)
$meNew = (Json 'GET' '/user/me' $tNew).data
Check '认证状态=待审核(0)' ($meNew.audit -eq 0) $meNew.audit

Write-Host "`n== 5. 管家发公告 + 全小区可见" -ForegroundColor Cyan
$tManager = Login '13900000002'
$pubA = (Json 'POST' '/notice' $tManager @{ title = '冒烟-全小区公告'; content = '全体可见'; type = 0; top = 0 }).data
Check '管家发公告成功' ($null -ne $pubA) $pubA
Check '张三能看到该公告' ((NoticeIds $tResident) -contains $pubA) ((NoticeIds $tResident) -join ',')
Check '9 幢新用户能看到该公告' ((NoticeIds $tNew) -contains $pubA) ((NoticeIds $tNew) -join ',')

Write-Host "`n== 6. 楼栋定向公告只给对应楼栋" -ForegroundColor Cyan
$pubB = (Json 'POST' '/notice' $tManager @{ title = '冒烟-9幢定向公告'; content = '只给 9 幢'; type = 1; top = 0; buildingTarget = '9幢' }).data
Check '9 幢新用户能看到定向公告' ((NoticeIds $tNew) -contains $pubB) ((NoticeIds $tNew) -join ',')
Check '3 幢张三看不到定向公告' (-not ((NoticeIds $tResident) -contains $pubB)) ((NoticeIds $tResident) -join ',')
Check '物业楼管家看不到 9 幢定向公告' (-not ((NoticeIds $tManager) -contains $pubB)) ((NoticeIds $tManager) -join ',')
$byType = (Json 'GET' '/notice/list?type=1' $tNew).data
Check '按类型筛选(停水停电)生效' (@($byType | Where-Object { $_.type -ne 1 }).Count -eq 0) (($byType | ForEach-Object { $_.type }) -join ',')

Write-Host "`n== 7. 未读计数 + 读详情 + 一键已读" -ForegroundColor Cyan
Api 'POST' '/notice/read-all' $tResident | Out-Null
$u0 = (Json 'GET' '/notice/unread' $tResident).data
Check '一键已读后未读=0' ($u0 -eq 0) $u0
$pubC = (Json 'POST' '/notice' $tManager @{ title = '冒烟-未读测试'; content = '用于验证未读计数'; type = 0; top = 0 }).data
$u1 = (Json 'GET' '/notice/unread' $tResident).data
Check '新公告使未读数+1' ($u1 -eq 1) $u1
$detail = (Json 'GET' "/notice/$pubC" $tResident).data
Check '公告详情可读' ($detail.id -eq $pubC) $detail.id
$u2 = (Json 'GET' '/notice/unread' $tResident).data
Check '读详情后未读归零' ($u2 -eq 0) $u2

Write-Host "`n== 8. RBAC：居民不能发公告" -ForegroundColor Cyan
$deny = Api 'POST' '/notice' $tResident @{ title = '居民越权'; content = 'x'; type = 0 }
Check '居民发公告被拒 403' ($deny -eq 'HTTP403') $deny

Write-Host "`n== 9. 业主认证审核闭环" -ForegroundColor Cyan
$pending = (Json 'GET' '/user/audit/list' $tManager).data
$target = $pending | Where-Object { $_.nickName -eq '业主8888' }
Check '管家能看到待审核申请' ($null -ne $target) (($pending | ForEach-Object { $_.nickName }) -join ',')
if ($target) {
    $auditRes = Json 'POST' "/user/audit/$($target.id)/true" $tManager
    Check '审核通过调用成功' ($auditRes.success -eq $true) (Api 'POST' "/user/audit/$($target.id)/true" $tManager)
    Check '审核后状态=已通过(1)' (((Json 'GET' '/user/me' $tNew).data).audit -eq 1) ((Json 'GET' '/user/me' $tNew).data).audit
    $noti = (Json 'GET' '/notification/list' $tNew).data
    Check '用户收到审核结果消息' (@($noti | Where-Object { $_.title -like '*认证*' }).Count -ge 1) (($noti | ForEach-Object { $_.title }) -join ',')
}
$denyAudit = Api 'GET' '/user/audit/list' $tResident
Check '居民访问审核列表被拒 403' ($denyAudit -eq 'HTTP403') $denyAudit

Write-Host "`n== 10. 站内消息未读数" -ForegroundColor Cyan
$mn = (Json 'GET' '/notification/unread' $tManager).data
Check '管家有未读消息' ($mn -ge 1) $mn
Api 'POST' '/notification/read-all' $tManager | Out-Null
Check '消息一键已读' (((Json 'GET' '/notification/unread' $tManager).data) -eq 0) ''

Write-Host "`n===== P0 冒烟结果：PASS=$pass FAIL=$fail =====" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
