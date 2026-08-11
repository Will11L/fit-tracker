# push-to-phone.ps1
# Build (optionnel) + install release APK sur le S21+ en detectant
# automatiquement le mode ADB disponible (USB > Tailscale 5555).
#
# Usage :
#   .\scripts\push-to-phone.ps1                  # push APK existant
#   .\scripts\push-to-phone.ps1 -Build           # build release puis push
#   .\scripts\push-to-phone.ps1 -NoRelaunch      # install sans relancer l'app
#
# Pre-requis :
#   - JDK 21 a C:\Users\William\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2
#   - ADB a C:\Users\William\AppData\Local\Android\Sdk\platform-tools
#   - Tel S21+ : USB plug OU adb tcpip 5555 deja active (cf. DEV_GUIDE.md sec.11)

param(
    [switch]$Build,
    [switch]$NoRelaunch
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$jdk      = "C:\Users\William\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
$adb      = "C:\Users\William\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk      = Join-Path $repoRoot "appli-android\app\build\outputs\apk\release\app-release.apk"
$pkg      = "com.example.sportapp"
$tsTarget = "<phone-ts-ip>:5555"   # S21+ Tailscale + adb tcpip 5555

if (-not (Test-Path $adb)) { throw "ADB introuvable : $adb" }

# Build optionnel
if ($Build) {
    Write-Host "[1/3] Build release..." -ForegroundColor Cyan
    $env:JAVA_HOME = $jdk
    $env:Path      = "$jdk\bin;$env:Path"
    Push-Location (Join-Path $repoRoot "appli-android")
    try {
        & .\gradlew.bat :app:assembleRelease --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "assembleRelease KO (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

if (-not (Test-Path $apk)) { throw "APK introuvable : $apk (lance avec -Build)" }

# Detection mode ADB : USB d'abord, puis Tailscale 5555
Write-Host "[2/3] Detection du device..." -ForegroundColor Cyan
$devicesRaw = & $adb devices
$lines = $devicesRaw | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }

$target = $null
$mode   = $null
foreach ($l in $lines) {
    $serial = ($l -split "\s+")[0]
    if ($serial -notmatch ":") {  # serial non-IP = USB
        $target = $serial
        $mode   = "USB"
        break
    }
}
if (-not $target) {
    # Pas d'USB, essayer Tailscale
    & $adb connect $tsTarget | Out-Null
    Start-Sleep -Milliseconds 500
    $devicesRaw = & $adb devices
    if ($devicesRaw -match [regex]::Escape($tsTarget) + "\s+device") {
        $target = $tsTarget
        $mode   = "Tailscale (wifi)"
    }
}
if (-not $target) {
    Write-Host "Aucun device detecte. Verifie :" -ForegroundColor Red
    Write-Host "  - USB branche + 'Autoriser le debogage' valide sur le tel"
    Write-Host "  - OU 'adb tcpip 5555' deja active sur le S21+ (cf. DEV_GUIDE.md sec.11)"
    exit 1
}
Write-Host "    -> device : $target ($mode)" -ForegroundColor Green

# Install + relaunch
Write-Host "[3/3] Install + relaunch..." -ForegroundColor Cyan
$sw = [Diagnostics.Stopwatch]::StartNew()
& $adb -s $target install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install KO (exit $LASTEXITCODE)" }
$sw.Stop()
Write-Host "    install OK en $([math]::Round($sw.Elapsed.TotalSeconds,1)) s" -ForegroundColor Green

if (-not $NoRelaunch) {
    & $adb -s $target shell am force-stop $pkg | Out-Null
    & $adb -s $target shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host "    app relancee" -ForegroundColor Green
}
