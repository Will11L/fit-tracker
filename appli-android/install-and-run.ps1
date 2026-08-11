# install-and-run.ps1
# Install l'APK release + lance l'app sur le tel branche en USB.
# Remplace le clic Shift+F10 d'Android Studio (etape limitante en iter rapide).
#
# Usage : .\install-and-run.ps1
# Optionnel : .\install-and-run.ps1 -Build (rebuild release APK avant install)
# Optionnel : .\install-and-run.ps1 -Reset (clear app data avant install : re-test onboarding from scratch)
#
# Pre-requis : tel branche en USB + USB debugging actif + adb authorisation
# (popup "Autoriser ce PC ?" sur le tel au premier branchement).

param(
    [switch]$Build,
    [switch]$Reset
)

$ErrorActionPreference = "Stop"

# --- JAVA_HOME auto-fix : sur l'ASUS Zenbook le JBR par defaut d'AS est casse
# (lib\jvm.cfg manquant). Fallback sur "Android Studio1\jbr" si l'actuel est mort.
function Test-JbrOk($path) {
    if (-not $path) { return $false }
    return (Test-Path (Join-Path $path "lib\jvm.cfg"))
}
if (-not (Test-JbrOk $env:JAVA_HOME)) {
    $candidates = @(
        "C:\Program Files\Android\Android Studio1\jbr",
        "C:\Program Files\Android\Android Studio\jbr"
    )
    foreach ($c in $candidates) {
        if (Test-JbrOk $c) { $env:JAVA_HOME = $c; break }
    }
    if (-not (Test-JbrOk $env:JAVA_HOME)) {
        Write-Error "Aucun JBR fonctionnel trouve. Cherche manuellement un dossier contenant lib\jvm.cfg."
        exit 1
    }
}

# --- ANDROID_HOME auto-fix : si pas defini, prendre l'emplacement utilisateur standard
if (-not $env:ANDROID_HOME) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultSdk) {
        $env:ANDROID_HOME = $defaultSdk
        $env:ANDROID_SDK_ROOT = $defaultSdk
    } else {
        Write-Error "ANDROID_HOME non defini et SDK introuvable a $defaultSdk."
        exit 1
    }
}

$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
$package = "com.example.sportapp"
$activity = "com.example.sportapp.MainActivity"

if (-not (Test-Path $adb)) {
    Write-Error "adb non trouve : $adb"
    exit 1
}

# Optionnel : build release avant install
if ($Build) {
    Write-Host "[install-and-run] Building release APK..." -ForegroundColor Cyan
    Push-Location $PSScriptRoot
    try {
        & .\gradlew.bat assembleRelease
        if ($LASTEXITCODE -ne 0) {
            Write-Error "gradlew assembleRelease failed"
            exit 1
        }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $apk)) {
    Write-Error "APK non trouve : $apk -- lancer .\gradlew.bat assembleRelease ou passer -Build"
    exit 1
}

# Verifier qu'au moins un device est connecte (status `device`, pas `unauthorized`/`offline`)
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
if (-not $devices) {
    Write-Error "Aucun device Android connecte authorise. Verifier USB + debugging + popup autorisation."
    exit 1
}

Write-Host "[install-and-run] Device(s) :" -ForegroundColor Cyan
$devices | ForEach-Object { Write-Host "  - $_" }

# -Reset : clear app data avant install (SharedPreferences + DataStore + Room).
# Utile pour re-tester l'onboarding (le flag onboarding_done_user_<uid> survit
# sinon a un install -r) ou repartir d'un theme=SYSTEM par defaut.
# No-op si le package n'est pas encore installe (sera install fresh juste apres).
if ($Reset) {
    $installed = & $adb shell pm list packages $package | Select-String "package:$package"
    if ($installed) {
        Write-Host "[install-and-run] adb shell pm clear $package (data cleared)" -ForegroundColor Yellow
        & $adb shell pm clear $package
        if ($LASTEXITCODE -ne 0) {
            Write-Error "adb shell pm clear failed (code $LASTEXITCODE)"
            exit 1
        }
    } else {
        Write-Host "[install-and-run] -Reset : package $package pas installe, skip pm clear" -ForegroundColor Yellow
    }
}

Write-Host "[install-and-run] adb install -r $apk" -ForegroundColor Cyan
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb install failed (code $LASTEXITCODE)"
    exit 1
}

Write-Host "[install-and-run] adb shell am start -n $package/$activity" -ForegroundColor Cyan
& $adb shell am start -n "$package/$activity"
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb shell am start failed (code $LASTEXITCODE)"
    exit 1
}

Write-Host "[install-and-run] OK - app lancee sur le tel" -ForegroundColor Green
