# ============================================================
# 压测准备：批量造 200 个已认证业主 + 建一个 10 名额的团
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts/loadtest/prepare.ps1
# 产出：scripts/loadtest/out/tokens.txt、scripts/loadtest/out/context.json
# ============================================================
param(
    [int]$Users = 200,
    [int]$Stock = 10
)
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8082'
$redis = 'C:\Program Files\Redis\redis-cli.exe'
$mysql = 'D:\Mysql\mysql-8.0.44-winx64\bin\mysql.exe'
$outDir = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Api($method, $path, $token, $body) {
    $p = @{ Uri = "$base$path"; Method = $method; TimeoutSec = 20; UseBasicParsing = $true }
    if ($token) { $p.Headers = @{ authorization = $token } }
    if ($body) { $p.Body = ($body | ConvertTo-Json -Compress); $p.ContentType = 'application/json; charset=utf-8' }
    return [System.Text.Encoding]::UTF8.GetString((Invoke-WebRequest @p).RawContentStream.ToArray())
}
function Login($phone, $code) {
    $r = Api 'POST' '/user/login' $null @{ phone = $phone; code = $code }
    return ($r | ConvertFrom-Json).data
}
function SqlFile($path) {
    cmd /c "`"$mysql`" -uroot -p990829 --default-character-set=utf8mb4 < `"$path`" 2>&1" | Where-Object { $_ -notmatch 'Warning' } | Out-Null
}

Write-Host "== 1. 批量创建 $Users 个已认证业主" -ForegroundColor Cyan
$sql = New-Object System.Text.StringBuilder
[void]$sql.AppendLine("USE resihub;")
[void]$sql.AppendLine("DELETE FROM tb_user WHERE phone LIKE '1391000%';")
[void]$sql.AppendLine("INSERT INTO tb_user(phone,nick_name,icon,community_id,building,room,role,audit,points,status) VALUES")
$rows = @()
for ($i = 1; $i -le $Users; $i++) {
    $phone = '1391' + $i.ToString('0000000')
    $rows += "('$phone','压测用户$i','',1,'1幢','$i',0,1,0,1)"
}
[void]$sql.AppendLine(($rows -join ",`n") + ";")
$sqlPath = Join-Path $outDir 'users.sql'
[System.IO.File]::WriteAllText($sqlPath, $sql.ToString(), (New-Object System.Text.UTF8Encoding($false)))
SqlFile $sqlPath
Write-Host "   已写入 tb_user"

Write-Host "== 2. 批量写入登录验证码（redis --pipe）" -ForegroundColor Cyan
$pipe = New-Object System.Text.StringBuilder
$phones = @()
for ($i = 1; $i -le $Users; $i++) { $phones += ('1391' + $i.ToString('0000000')) }
foreach ($p in $phones) { [void]$pipe.AppendLine("SELECT 1"); [void]$pipe.AppendLine("SET login:code:$p 123456 EX 900") }
$pipePath = Join-Path $outDir 'codes.txt'
[System.IO.File]::WriteAllText($pipePath, $pipe.ToString(), (New-Object System.Text.ASCIIEncoding))
$pipeOut = cmd /c "type `"$pipePath`" | `"$redis`" -p 6380 -n 1 --pipe 2>&1"
Write-Host ("   " + ($pipeOut -join ' '))

Write-Host "== 3. 逐个登录拿 token" -ForegroundColor Cyan
$tokens = New-Object System.Collections.Generic.List[string]
$i = 0
foreach ($p in $phones) {
    $i++
    try {
        $t = Login $p '123456'
        if ($t) { $tokens.Add($t) }
    } catch {
        Write-Host ("   登录失败 $p : " + $_.Exception.Message) -ForegroundColor DarkYellow
    }
    if ($i % 50 -eq 0) { Write-Host "   已登录 $i / $Users" }
}
$tokenPath = Join-Path $outDir 'tokens.txt'
[System.IO.File]::WriteAllLines($tokenPath, $tokens, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "   有效 token 数：$($tokens.Count)"

Write-Host "== 4. 建压测商品 + 开团（$Stock 个名额）" -ForegroundColor Cyan
$sql2 = New-Object System.Text.StringBuilder
[void]$sql2.AppendLine("USE resihub;")
[void]$sql2.AppendLine("DELETE FROM tb_group_buy WHERE product_id = 99;")
[void]$sql2.AppendLine("DELETE FROM tb_product WHERE id = 99;")
[void]$sql2.AppendLine("DELETE FROM tb_order WHERE product_id = 99;")
[void]$sql2.AppendLine("INSERT INTO tb_product(id,community_id,name,sub_title,images,price,origin_price,unit,stock,total_stock,status) VALUES (99,1,'压测商品','仅用于并发压测','',100,100,'份',$Stock,$Stock,1);")
$sqlPath2 = Join-Path $outDir 'product.sql'
[System.IO.File]::WriteAllText($sqlPath2, $sql2.ToString(), (New-Object System.Text.UTF8Encoding($false)))
SqlFile $sqlPath2

# 用第一个 token 当团长开团
$leaderToken = Api 'POST' '/user/code?phone=13900000004' | Out-Null
$code = (& $redis -p 6380 -n 1 get 'login:code:13900000004').Trim()
$leaderToken = Login '13900000004' $code
$open = (Api 'POST' "/group-buy?productId=99&minUsers=998&hours=2&pickupPoint=压测自提点" $leaderToken | ConvertFrom-Json)
if (-not $open.success) { throw ("开团失败：" + $open.errorMsg) }
$groupId = $open.data
Write-Host "   groupId=$groupId"

$ctx = @{ base = $base; groupId = $groupId; stock = $Stock; tokens = $tokenPath; users = $tokens.Count }
[System.IO.File]::WriteAllText((Join-Path $outDir 'context.json'), ($ctx | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "`n准备完成：$tokenPath   groupId=$groupId   名额=$Stock" -ForegroundColor Green
