#requires -Version 7.0
<#
Runs installed signed instrumentation using fixed fixture IDs, not SMS broadcasts.
Build/install instructions and evidence limits: docs/debug-sms-testing.md
#>
[CmdletBinding()]
param(
    [ValidateSet('Otp','OtpFull','Normal','Numbers','Mixed','OtpAmbiguous','Long','Long80','Long81',
        'LongPcm','Rapid','Disabled','Disconnected','Focus','Multipart','Logic','All')]
    [string]$Case = 'All',
    [string]$Serial
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = if ($adbCommand) { $adbCommand.Source } else { $null }
if (-not $adbPath) {
    foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($sdkRoot -and (Test-Path -LiteralPath (Join-Path $sdkRoot 'platform-tools\adb.exe'))) {
            $adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'; break
        }
    }
}

function Invoke-SvaAdb([string[]]$Arguments, [int]$TimeoutSeconds = 30, [string]$ForegroundComponent = '') {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $adbPath
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
    $start.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    [void]$process.Start()
    $stderr = $process.StandardError.ReadToEndAsync()
    $output = [Text.StringBuilder]::new()
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $lineTask = $process.StandardOutput.ReadLineAsync()
    $foregroundStarted = $false
    while ($true) {
        if ($lineTask.Wait(100)) {
            $line = $lineTask.GetAwaiter().GetResult()
            if ($null -eq $line) { break }
            [void]$output.AppendLine($line)
            if ($ForegroundComponent -and -not $foregroundStarted -and $line.Contains('SVA_DEBUG_READY')) {
                $foregroundStarted = $true
                $launch = Invoke-SvaAdb -Arguments ($targetArguments + @('shell','am','start','-n',$ForegroundComponent))
                if ($launch.Code -ne 0) { throw 'Could not open the real application launcher. Unlock the phone and retry.' }
            }
            $lineTask = $process.StandardOutput.ReadLineAsync()
        }
        if ($timer.Elapsed.TotalSeconds -gt $TimeoutSeconds) {
            $process.Kill()
            throw 'ADB timeout. No next case was started; check the device before retrying.'
        }
    }
    [void]$process.WaitForExit(5000)
    $result = [pscustomobject]@{ Code = $process.ExitCode; Output = $output.ToString() }
    [void]$stderr.GetAwaiter().GetResult() # Never echo raw ADB errors containing device identity.
    $process.Dispose()
    return $result
}

try {
    if (-not $adbPath) { throw 'ADB is unavailable. Install platform-tools or set ANDROID_SDK_ROOT.' }
    $listing = Invoke-SvaAdb -Arguments @('devices')
    if ($listing.Code -ne 0) { throw 'Cannot query ADB devices.' }
    $devices = @($listing.Output -split "`r?`n" | ForEach-Object {
        if ($_ -match '^(\S+)\s+(device|unauthorized|offline)\b') {
            [pscustomobject]@{ Identity = $Matches[1]; State = $Matches[2] }
        }
    })
    if ($Serial) {
        $chosen = @($devices | Where-Object { $_.Identity -ceq $Serial })
        if ($chosen.Count -ne 1 -or $chosen[0].State -ne 'device') {
            throw 'Selected device is absent, offline or unauthorized. Confirm USB debugging on the phone.'
        }
    } else {
        $chosen = @($devices | Where-Object State -eq 'device')
        if ($chosen.Count -ne 1) { throw 'Need exactly one online authorized device; use -Serial when multiple are online.' }
    }
    $targetArguments = @('-s', $chosen[0].Identity)
    $appMetaPath = Join-Path $projectRoot 'app/build/outputs/apk/debug/output-metadata.json'
    $testMetaPath = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/output-metadata.json'
    if (-not (Test-Path -LiteralPath $appMetaPath) -or -not (Test-Path -LiteralPath $testMetaPath)) {
        throw 'Build Debug and androidTest first: .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest'
    }
    $appMetadata = Get-Content -LiteralPath $appMetaPath -Raw -Encoding utf8 | ConvertFrom-Json
    $testMetadata = Get-Content -LiteralPath $testMetaPath -Raw -Encoding utf8 | ConvertFrom-Json
    $packageName = $appMetadata.applicationId
    $testPackage = $testMetadata.applicationId
    foreach ($package in @($packageName, $testPackage)) {
        if ($package -notmatch '^[A-Za-z_][A-Za-z0-9_.]+$') { throw 'Invalid package metadata.' }
        $installed = Invoke-SvaAdb -Arguments ($targetArguments + @('shell','pm','path',$package))
        if ($installed.Code -ne 0 -or $installed.Output -notmatch '(?m)^package:') {
            throw 'Debug application or test APK is not installed. See docs/debug-sms-testing.md for install -r steps.'
        }
    }
    $flags = Invoke-SvaAdb -Arguments ($targetArguments + @('shell','dumpsys','package',$packageName))
    if ($flags.Code -ne 0 -or $flags.Output -notmatch '(?m)^\s*flags=\[[^\]]*\bDEBUGGABLE\b') {
        throw 'Installed target is not a Debug application. Release cannot run this harness.'
    }
    $registrations = Invoke-SvaAdb -Arguments ($targetArguments + @('shell','pm','list','instrumentation'))
    $components = @($registrations.Output -split "`r?`n" | ForEach-Object {
        if ($_ -match '^instrumentation:([A-Za-z0-9_.$/]+)\s+\(target=([A-Za-z0-9_.]+)\)\s*$') {
            if ($Matches[2] -ceq $packageName -and $Matches[1].StartsWith("$testPackage/", [StringComparison]::Ordinal)) {
                $Matches[1]
            }
        }
    })
    if ($components.Count -ne 1) { throw 'No unique installed instrumentation registration matches the built target/test APKs.' }
    $sourcePath = Join-Path $projectRoot 'app/src/androidTest/java/com/mxy1021/smsvoiceassistant/DebugSmsInjectionTest.kt'
    $testSource = Get-Content -LiteralPath $sourcePath -Raw -Encoding utf8
    if ($testSource -notmatch '(?m)^package ([A-Za-z0-9_.]+)\s*$') { throw 'Cannot resolve test class from source.' }
    $testClass = "$($Matches[1]).DebugSmsInjectionTest"
    [xml]$appManifest = Get-Content -LiteralPath (Join-Path $projectRoot 'app/src/main/AndroidManifest.xml') -Raw -Encoding utf8
    $ns = [Xml.XmlNamespaceManager]::new($appManifest.NameTable)
    $ns.AddNamespace('android','http://schemas.android.com/apk/res/android')
    $launcher = $appManifest.SelectSingleNode("//activity[intent-filter/category[@android:name='android.intent.category.LAUNCHER']]",$ns)
    if (-not $launcher) { throw 'Cannot resolve the real launcher from the application manifest.' }
    $activityName = $launcher.GetAttribute('name','http://schemas.android.com/apk/res/android')
    if ($activityName.StartsWith('.')) { $activityName = "$packageName$activityName" }
    if ($activityName -notmatch '^[A-Za-z0-9_.]+$') { throw 'Invalid launcher component.' }
    Write-Output "DEBUG_SIMULATION fixture=$Case; waiting for terminal instrumentation results. Hearing remains manual."
    $execution = Invoke-SvaAdb -Arguments ($targetArguments + @('shell','am','instrument','-w','-r',
        '-e','class',$testClass,'-e','fixture',$Case,$components[0])) -TimeoutSeconds 240 -ForegroundComponent "$packageName/$activityName"
    $results = @()
    foreach ($line in ($execution.Output -split "`r?`n")) {
        if ($line -match 'SVA_DEBUG_RESULT (\{.*\})\s*$') {
            $item = $Matches[1] | ConvertFrom-Json
            if ($item.schema -ne 1 -or $item.source -cne 'DEBUG_SIMULATION' -or
                $item.status -notin @('PASS','FAIL','SKIP','NOT_RUN','MANUAL_REQUIRED','UNSUPPORTED')) {
                throw 'Unexpected structured instrumentation result.'
            }
            $results += $item
            # Rebuild allowed fields: never relay raw instrumentation or Logcat output.
            $safe = [ordered]@{ source=$item.source; case=$item.case; status=$item.status;
                assertions=$item.assertions; hearing=$item.hearing; evidence=$item.evidence }
            foreach ($field in @('event','disposition','reason','pcmMs','bytesWritten','actualRoute','focusReleased','focusChanges')) {
                if ($item.PSObject.Properties.Name -contains $field) { $safe[$field] = $item.$field }
            }
            Write-Output ($safe | ConvertTo-Json -Compress -Depth 4)
        }
    }
    if ($execution.Code -ne 0 -or $execution.Output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=' -or
        $execution.Output -notmatch 'OK \(1 test\)' -or $results.Count -eq 0 -or @($results | Where-Object status -eq 'FAIL').Count -gt 0) {
        throw 'Instrumentation or business assertions failed. Do not treat command completion as playback success.'
    }
    Write-Output "Completed $($results.Count) structured results. SKIP/UNSUPPORTED/manual hearing are not acceptance passes."
    exit 0
} catch {
    # Messages above are generated fixed diagnostics; no raw remote command data is included.
    Write-Error -Message $_.Exception.Message -ErrorAction Continue
    exit 1
}
