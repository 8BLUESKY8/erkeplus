# erke++ build entry (ASCII only: Windows PowerShell 5.1 reads .ps1 as ANSI)
# Usage:
#   build.cmd            -> debug APK
#   build.cmd release    -> signed release APK
param(
    [ValidateSet('debug', 'release')][string]$Mode = 'debug'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# System PATH usually points to JDK 8, which AGP 8.x cannot use.
$jdk = if ($env:ERKE_JAVA_HOME) { $env:ERKE_JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
if (-not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    throw "JDK not found: $jdk . Set ERKE_JAVA_HOME to a JDK 17+."
}
$env:JAVA_HOME = $jdk
# Signing dir: env var first, else the local convention path.
if (-not $env:ERKE_KS_DIR) {
    $env:ERKE_KS_DIR = Join-Path $env:USERPROFILE '.android\keystore'
}

$task = if ($Mode -eq 'release') { 'assembleRelease' } else { 'assembleDebug' }
& (Join-Path $root 'gradlew.bat') $task --console=plain
if ($LASTEXITCODE -ne 0) { throw "build failed: exit $LASTEXITCODE" }

$apkDir = Join-Path $root "app\build\outputs\apk\$Mode"
$apk = Get-ChildItem $apkDir -Filter *.apk -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "APK not found in $apkDir" }

$outDir = Join-Path $root 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$dest = Join-Path $outDir $apk.Name
Copy-Item $apk.FullName $dest -Force

""
"APK   : $dest"
"SIZE  : $([math]::Round($apk.Length / 1MB, 2)) MB"
"SHA256: $((Get-FileHash $dest -Algorithm SHA256).Hash)"
