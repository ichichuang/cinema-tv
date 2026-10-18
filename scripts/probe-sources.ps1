# 推荐源清单「实测」探针：逐个访问苹果CMS接口，实测速度+资源量，生成 update/sources.json
# 用法：powershell -ExecutionPolicy Bypass -File scripts\probe-sources.ps1
# 说明：测量结果直接覆盖 update\sources.json（再跑 publish-sources.ps1 上传发布仓库）。
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$file = Join-Path $root "update\sources.json"
$tmp  = Join-Path $env:TEMP "probe-vod.json"

$json = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8) | ConvertFrom-Json
$rows = @()
foreach ($s in $json.sources) {
    $url = $s.url + "?ac=videolist&pg=1&at=json"
    $ms = 0; $total = 0; $ok = $false
    try {
        if (Test-Path $tmp) { Remove-Item $tmp -Force }
        $sw = [Diagnostics.Stopwatch]::StartNew()
        & curl.exe --ssl-no-revoke -s -m 15 -A "Mozilla/5.0" -o $tmp $url | Out-Null
        $code = $LASTEXITCODE
        $sw.Stop()
        $ms = [int]$sw.Elapsed.TotalMilliseconds
        if ($code -eq 0 -and (Test-Path $tmp)) {
            $body = [IO.File]::ReadAllText($tmp, [Text.Encoding]::UTF8)
            $m = [regex]::Match($body, '"total"\s*:\s*(\d+)')
            if ($m.Success) { $total = [int]$m.Groups[1].Value; $ok = $true }
        }
    } catch { $ok = $false }
    $rows += [pscustomobject]@{ name = $s.name; url = $s.url; ms = $ms; total = $total; ok = $ok }
    Write-Host ("  " + $s.name + "  " + $ms + "ms  total=" + $total + "  ok=" + $ok)
}
$good = @($rows | Where-Object { $_.ok -and $_.total -gt 0 } | Sort-Object -Property @{Expression="total";Descending=$true}, @{Expression="ms";Ascending=$true})
$bad  = @($rows | Where-Object { -not ($_.ok -and $_.total -gt 0) })
$all  = $good.Count + $bad.Count

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("{")
$lines.Add('  "updated": "' + (Get-Date -Format "yyyy-MM-dd") + '",')
$lines.Add('  "note": "极简影院推荐片源 · 发布方今天实测（速度+资源量）排序 · 逐个核对过分类表、已剔除成人源。想换镜像/加源只改这个文件，应用不用重新发版。",')
$lines.Add('  "sources": [')
$i = 0
foreach ($r in $good) {
    $i++
    $rank = switch ($i) { 1 { "首选" } 2 { "备选1" } 3 { "备选2" } 4 { "备选3" } 5 { "备选4" } 6 { "备选5" } default { "" } }
    $wan = [math]::Round($r.total / 10000.0, 1)
    $info = if ($rank) { $rank + " · " } else { "" }
    $info = $info + $wan + "万部 · " + $r.ms + "ms"
    $comma = if ($i -lt $all) { "," } else { "" }
    $lines.Add('    { "name": "' + $r.name + '", "url": "' + $r.url + '", "info": "' + $info + '" }' + $comma)
}
$k = $i
foreach ($r in $bad) {
    $k++
    $comma = if ($k -lt $all) { "," } else { "" }
    $lines.Add('    { "name": "' + $r.name + '", "url": "' + $r.url + '", "info": "现在测不通（可能已下线），先留着" }' + $comma)
}
$lines.Add("  ]")
$lines.Add("}")
$text = ($lines -join "`r`n")
[IO.File]::WriteAllText($file, $text, (New-Object Text.UTF8Encoding($false)))
Write-Host ("已写入 " + $file + "：可用 " + $good.Count + " / 共 " + $all)
