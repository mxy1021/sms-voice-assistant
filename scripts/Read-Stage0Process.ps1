#requires -Version 7.0
param([string]$Case = '只读快照', [switch]$Save)

# 仅读取本包系统进程、屏幕和窗口状态；不连接应用 Binder，不解冻、不改配置。
$ErrorActionPreference = 'Stop'
$rows = @((& adb devices 2>$null) | Where-Object { $_ -match '^\S+\s+device$' })
if ($rows.Count -ne 1) { throw '需要唯一在线授权设备；未显示序列号。' }
$svaDevice = ($rows[0] -split '\s+')[0]
function Read-Sva([string[]]$Arguments) {
    $text = & adb -s $svaDevice @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw '只读状态命令失败，未输出原始数据。' }
    return $text
}
$process = (Read-Sva @('shell','dumpsys','activity','processes','com.mxy1021.smsvoiceassistant')) -join "`n"
$allLines = $process -split "`n"
$block = [Collections.Generic.List[string]]::new()
for ($index = 0; $index -lt $allLines.Count; $index++) {
    if ($allLines[$index] -match '^\s*\*APP\*.*ProcessRecord.*com\.mxy1021\.smsvoiceassistant(?:/|\s|})') {
        $indent = ([regex]::Match($allLines[$index], '^\s*')).Length
        for ($next = $index + 1; $next -lt $allLines.Count; $next++) {
            $line = $allLines[$next]
            # 部分厂商的 mAdjType 行缩进异常，但仍属于当前进程。
            if ($line.Trim() -and ([regex]::Match($line, '^\s*')).Length -le $indent -and $line -notmatch '^\s*mAdjType\b') { break }
            $block.Add($line)
        }
        break
    }
}
# 限定在本包的 APP 进程块，排除 dumpsys 尾部全局冻结进程统计。
$ownProcess = $block -join "`n"
$power = (Read-Sva @('shell','dumpsys','power')) -join "`n"
$window = (Read-Sva @('shell','dumpsys','window')) -join "`n"
$elapsed = (Read-Sva @('shell','cat','/proc/uptime')) -join ''
$deviceTime = (Read-Sva @('shell','date','+%s%3N')) -join ''
$fields = [ordered]@{}
foreach ($name in @('isPendingFreeze','cached','empty','foregroundActivities','curProcState','setProcState','mIsReceivingBroadcast')) {
    $values = @([regex]::Matches($ownProcess, '\b'+[regex]::Escape($name)+'\s*[=:]\s*([A-Za-z_0-9-]+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique)
    if ($values.Count) { $fields[$name] = $values }
}
# OriginOS 同一进程有两处同名 isFrozen：平台优化器与厂商附加冻结，分别保留，不能混读。
$platform = [regex]::Match($ownProcess, '(?m)^.*hasPendingCompaction=.*\bisFrozen=(true|false).*$')
$vendor = [regex]::Match($ownProcess, '(?m)^\s*isFrozen=(true|false)\s*$')
$fields['平台优化器isFrozen'] = if ($platform.Success) { $platform.Groups[1].Value } else { '未知' }
$fields['厂商附加isFrozen'] = if ($vendor.Success) { $vendor.Groups[1].Value } else { '未知' }
$state = [pscustomobject]@{
    用例=$Case;设备毫秒=$deviceTime.Trim()
    系统运行秒=([regex]::Match($elapsed,'^\s*([0-9.]+)')).Groups[1].Value
    有本包进程记录=($process -match 'com\.mxy1021\.smsvoiceassistant')
    已限定本包进程块=($block.Count -gt 0)
    进程状态=$fields
    屏幕状态=([regex]::Match($power,'mWakefulness=(\w+)')).Groups[1].Value
    本包为焦点窗口=($window -match 'mCurrentFocus=.*com\.mxy1021\.smsvoiceassistant/')
    未修改系统设置=$true
}
$json = $state | ConvertTo-Json -Depth 4 -Compress
if ($Save) {
    $destination = Join-Path $PSScriptRoot '..\app\build\stage0-device\process.jsonl'
    New-Item -ItemType Directory -Path (Split-Path $destination) -Force | Out-Null
    Add-Content -LiteralPath $destination -Value $json -Encoding utf8
}
Write-Output $json
