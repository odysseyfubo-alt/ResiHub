# ============================================================
# 重置为干净的演示数据（冒烟/压测之后用）
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/reset.ps1
# 注意：会 DROP 并重建 resihub 库里的表，Redis 里只清业务 key，不动 stream:orders
# ============================================================
$ErrorActionPreference = 'Stop'
$mysql = 'D:\Mysql\mysql-8.0.44-winx64\bin\mysql.exe'
$redis = 'C:\Program Files\Redis\redis-cli.exe'
$initSql = Join-Path (Split-Path $PSScriptRoot -Parent) 'src\main\resources\db\init.sql'

Write-Host "== 重新导入 init.sql（建表 + 种子数据）" -ForegroundColor Cyan
cmd /c "`"$mysql`" -uroot -p990829 --default-character-set=utf8mb4 < `"$initSql`" 2>&1" |
    Where-Object { $_ -notmatch 'Warning' } | Out-Null

Write-Host "== 清理 Redis 业务 key" -ForegroundColor Cyan
$patterns = @('gb:*', 'cache:*', 'work:timeout', 'visit:pass:*', 'sign:*', 'post:liked:*', 'notice:read:*', 'act:*')
foreach ($p in $patterns) {
    $keys = & $redis -p 6380 -n 1 keys $p
    foreach ($k in $keys) {
        if ($k) { & $redis -p 6380 -n 1 del $k | Out-Null }
    }
}
# 登录态一起清掉，避免旧 token 指向已删除的用户
$loginKeys = & $redis -p 6380 -n 1 keys 'login:*'
foreach ($k in $loginKeys) { if ($k) { & $redis -p 6380 -n 1 del $k | Out-Null } }

Write-Host "== 清理压测产物" -ForegroundColor Cyan
Remove-Item (Join-Path $PSScriptRoot 'loadtest\out\*') -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "重置完成：数据回到初始种子状态，可以重新执行冒烟脚本" -ForegroundColor Green
