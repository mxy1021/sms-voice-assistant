#requires -Version 7.0
param([switch]$Follow, [switch]$Save, [switch]$Quiet, [long]$SinceEpochMillis = 0)

# 只接收本项目 SVA_STAGE0 的既定脱敏 JSON，不保存原始 Logcat。
$ErrorActionPreference = 'Stop'
$deviceRows = @((& adb devices 2>$null) | Where-Object { $_ -match '^\S+\s+device$' })
if ($deviceRows.Count -ne 1) { throw '需要唯一在线授权设备；未输出序列号。' }
$svaDevice = ($deviceRows[0] -split '\s+')[0]
$destination = Join-Path $PSScriptRoot '..\app\build\stage0-device\live.jsonl'
if ($Save) { New-Item -ItemType Directory -Path (Split-Path $destination) -Force | Out-Null }
$readArguments = @('-s', $svaDevice, 'logcat')
if (-not $Follow) { $readArguments += '-d' }
$readArguments += @('-v','raw','-s','SVA_STAGE0:I','*:S')
& adb @readArguments 2>$null | ForEach-Object {
    $line = $_
    try {
        $event = $line | ConvertFrom-Json -ErrorAction Stop
        if ($event.schema -ne 1 -or $event.package -ne 'com.mxy1021.smsvoiceassistant' -or
            $event.kind -notin @('timeline','sms','audio','focus','tts-compatibility','lifecycle','event') -or
            [long]$event.epochMs -lt $SinceEpochMillis) { return }
        # 明确重建白名单，拒绝其他 JSON 字段或非本项目原始行。
        $safe = [ordered]@{schema=1;epochMs=$event.epochMs;elapsedMs=$event.elapsedMs;kind=$event.kind;field=$event.field;value=$event.value}
        if ($event.id) { $safe['id'] = $event.id }
        $json = $safe | ConvertTo-Json -Compress -Depth 3
        if ($Save) { Add-Content -LiteralPath $destination -Value $json -Encoding utf8 }
        if (-not $Quiet) { Write-Output $json }
    } catch { }
}
