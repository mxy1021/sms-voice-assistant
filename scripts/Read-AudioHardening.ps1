#requires -Version 7.0
param([long]$SinceEpochMillis = 0)

# 只读采集：不更改音频 hardening、权限、音量、服务或系统设置，不保存原始日志。
$ErrorActionPreference = 'Stop'
$package = 'com.mxy1021.smsvoiceassistant'
$packagePattern = '(?<![\w.])' + [regex]::Escape($package) + '(?![\w.])'
function Read-Adb([string[]]$Arguments) {
    $result = & adb @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'ADB 只读命令失败，未输出原始数据。' }
    return $result
}
$devices = @(Read-Adb @('devices'))
$online = @($devices | Where-Object { $_ -match '\sdevice$' }).Count
if ($online -ne 1) {
    [pscustomobject]@{状态='未验证：需要唯一已授权在线设备'; 在线数量=$online; AudioHardening='未采集'; 已修改系统设置=$false} | ConvertTo-Json
    exit 0
}
$api = ([string](Read-Adb @('shell','getprop','ro.build.version.sdk'))).Trim()
if ($api -notmatch '^\d+$') { $api = '未知' }
$details = (Read-Adb @('shell','dumpsys','package',$package)) -join "`n"
$target = [regex]::Match($details, 'targetSdk=(\d+)')
$targetValue = if ($target.Success) { $target.Groups[1].Value } else { '未知' }
$hardeningRecords = [Collections.Generic.List[object]]::new()
foreach ($source in @('dumpsys','logcat')) {
    $lines = if ($source -eq 'dumpsys') { Read-Adb @('shell','dumpsys','audio') } else { Read-Adb @('logcat','-d','-v','epoch') }
    foreach ($line in $lines) {
        if ($line -notmatch 'AudioHardening' -or $line -notmatch $packagePattern) { continue }
        $stamp = [regex]::Match($line, '^\s*(\d{9,}\.\d+)')
        $epoch = if ($stamp.Success) { [long]([double]::Parse($stamp.Groups[1].Value,[Globalization.CultureInfo]::InvariantCulture) * 1000) } else { 0L }
        if ($source -eq 'logcat' -and $SinceEpochMillis -gt 0 -and $epoch -lt $SinceEpochMillis) { continue }
        $level = [regex]::Match($line, 'level:\s*(full|partial)')
        $category = if ($line -match '(?i)focus') { '焦点' } elseif ($line -match '(?i)play|track|write') { '播放' } else { '未知' }
        # 只输出白名单字段；不回显原行、UID、蓝牙地址、号码或其他应用信息。
        $hardeningRecords.Add([pscustomobject]@{
            来源=$source; 本应用匹配=$true; 记录毫秒=$epoch
            等级=$(if($level.Success){$level.Groups[1].Value}else{'未知'})
            API类别=$category; 与本轮时间关联=$(if($epoch -gt 0){'可结合事件开始毫秒核对'}else{'未知，可能是历史记录'})
        })
    }
}
[pscustomobject]@{
    状态='只读采集完成'; 系统API=$api; targetSdk=$targetValue; 已修改系统设置=$false
    AudioHardening开启状态='未直接查询开关；是否本轮拦截须结合记录与事件时间'
    匹配记录数=$hardeningRecords.Count; 记录=@($hardeningRecords | Select-Object -Last 20)
    无记录说明='没有匹配不代表关闭或未拦截，日志可能已轮换或缺少包名'
} | ConvertTo-Json -Depth 5
