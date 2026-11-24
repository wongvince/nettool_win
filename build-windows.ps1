
# ============================================================
# NetworkToolkit Windows x64 Build Script
# Builds:
#   1. Windows installer EXE
#   2. Windows portable ZIP (bundled Java runtime)
#
# Compatible with:
#   JDK 17 x64
#   Gradle 8.10.2
#   Compose Desktop 1.7.3
# ============================================================

$ErrorActionPreference = 'Stop'

# Always run from the project root
Set-Location -LiteralPath $PSScriptRoot

Write-Host ''
Write-Host '========== NetworkToolkit Windows Build ==========' -ForegroundColor Cyan
Write-Host "Project: $PWD" -ForegroundColor Gray

# ------------------------------------------------------------
# 1. Check Gradle
# ------------------------------------------------------------

$gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue

if (-not $gradleCommand) {
    throw 'Gradle was not found. Please configure Gradle 8.10.2.'
}

Write-Host "[OK] Gradle: $($gradleCommand.Source)" -ForegroundColor Green

# ------------------------------------------------------------
# 2. Check JDK 17
# ------------------------------------------------------------

if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    throw 'JAVA_HOME is empty. JDK 17 x64 is required.'
}

$jdkBin = Join-Path $env:JAVA_HOME 'bin'
$jpackage = Join-Path $jdkBin 'jpackage.exe'
$jlink = Join-Path $jdkBin 'jlink.exe'

if (-not (Test-Path -LiteralPath $jpackage)) {
    throw "JDK jpackage.exe was not found: $jpackage"
}

if (-not (Test-Path -LiteralPath $jlink)) {
    throw "JDK jlink.exe was not found: $jlink"
}

Write-Host "[OK] JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
Write-Host '[OK] JDK packaging tools found.' -ForegroundColor Green

# ------------------------------------------------------------
# 3. Clean previous output
# ------------------------------------------------------------

$releaseBase = Join-Path $PWD 'build/compose/binaries/main-release'
$appBase = Join-Path $releaseBase 'app'
$installerDir = Join-Path $releaseBase 'exe'
$outputDir = Join-Path $PWD 'artifacts'

if (Test-Path -LiteralPath $outputDir) {
    Remove-Item -LiteralPath $outputDir -Recurse -Force
}

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

# ------------------------------------------------------------
# 4. Build release installer and release app image
# ------------------------------------------------------------

Write-Host ''
Write-Host '========== Building Release ==========' -ForegroundColor Cyan

gradle packageReleaseExe createReleaseDistributable --stacktrace

if ($LASTEXITCODE -ne 0) {
    throw "Gradle release packaging failed. Exit code: $LASTEXITCODE"
}

Write-Host '[OK] Gradle build completed.' -ForegroundColor Green

# ------------------------------------------------------------
# 5. Check release app image
# ------------------------------------------------------------

if (-not (Test-Path -LiteralPath $appBase)) {
    throw "Release app-image directory was not created: $appBase"
}

Write-Host ''
Write-Host '========== Searching Application ==========' -ForegroundColor Cyan

$launchers = @(
    Get-ChildItem -Path $appBase -Filter 'NetworkToolkit.exe' -File -Recurse
)

if ($launchers.Count -eq 0) {
    Write-Host 'App image contents:' -ForegroundColor Yellow

    Get-ChildItem -Path $appBase -Recurse -Force |
        Select-Object -ExpandProperty FullName |
        Write-Host

    throw 'NetworkToolkit.exe was not found in the release app image.'
}

# Use the launcher directory as the actual portable app image.
$appLauncher = $launchers | Select-Object -First 1
$appImage = $appLauncher.Directory.FullName

Write-Host "[OK] Application launcher: $($appLauncher.FullName)" -ForegroundColor Green

# ------------------------------------------------------------
# 6. Check bundled Java runtime
#
# IMPORTANT:
# Do not require runtime/bin/java.exe.
# A jlink runtime may omit the standalone Java launcher.
# The packaged application launcher uses the JVM runtime.
# ------------------------------------------------------------

Write-Host ''
Write-Host '========== Checking Bundled Java Runtime ==========' -ForegroundColor Cyan

$runtimeDir = Join-Path $appImage 'runtime'

if (-not (Test-Path -LiteralPath $runtimeDir)) {
    throw "Bundled runtime directory was not found: $runtimeDir"
}

# Check Java module image.
$modulesFile = Join-Path $runtimeDir 'lib/modules'

if (-not (Test-Path -LiteralPath $modulesFile)) {
    throw "Java runtime modules file was not found: $modulesFile"
}

# Find the JVM native library.
$jvmFiles = @(
    Get-ChildItem -Path $runtimeDir -Filter 'jvm.dll' -File -Recurse
)

if ($jvmFiles.Count -eq 0) {
    Write-Host 'Runtime directory contents:' -ForegroundColor Yellow

    Get-ChildItem -Path $runtimeDir -Recurse -Force |
        Select-Object -ExpandProperty FullName |
        Write-Host

    throw 'The bundled runtime does not contain jvm.dll. The Java runtime image may be incomplete.'
}

$jvmDll = $jvmFiles | Select-Object -First 1

Write-Host "[OK] Java modules: $modulesFile" -ForegroundColor Green
Write-Host "[OK] JVM library: $($jvmDll.FullName)" -ForegroundColor Green

# ------------------------------------------------------------
# 7. Check Windows installer
# ------------------------------------------------------------

Write-Host ''
Write-Host '========== Checking Installer ==========' -ForegroundColor Cyan

if (-not (Test-Path -LiteralPath $installerDir)) {
    throw "Release installer directory was not created: $installerDir"
}

$installer = Get-ChildItem -Path $installerDir -Filter '*.exe' -File |
    Where-Object {
        $_.Name -notlike '*uninstall*'
    } |
    Select-Object -First 1

if (-not $installer) {
    Write-Host 'Installer directory contents:' -ForegroundColor Yellow

    Get-ChildItem -Path $installerDir -Recurse -Force |
        Select-Object -ExpandProperty FullName |
        Write-Host

    throw "NetworkToolkit installer EXE was not found in: $installerDir"
}

$installerOutput = Join-Path $outputDir 'NetworkToolkit-Setup.exe'

Copy-Item -LiteralPath $installer.FullName `
    -Destination $installerOutput `
    -Force

Write-Host "[OK] Installer copied: $installerOutput" -ForegroundColor Green

# ------------------------------------------------------------
# 8. Create Windows x64 portable ZIP
# ------------------------------------------------------------

Write-Host ''
Write-Host '========== Creating Portable ZIP ==========' -ForegroundColor Cyan

$portableZip = Join-Path $outputDir 'NetworkToolkit-Windows-x64-Portable.zip'

if (Test-Path -LiteralPath $portableZip) {
    Remove-Item -LiteralPath $portableZip -Force
}

Compress-Archive `
    -Path (Join-Path $appImage '*') `
    -DestinationPath $portableZip `
    -CompressionLevel Optimal `
    -Force

if (-not (Test-Path -LiteralPath $portableZip)) {
    throw 'Portable ZIP was not created.'
}

$zipInfo = Get-Item -LiteralPath $portableZip

if ($zipInfo.Length -le 0) {
    throw 'Portable ZIP is empty.'
}

Write-Host "[OK] Portable ZIP: $portableZip" -ForegroundColor Green

# ------------------------------------------------------------
# 9. Final output
# ------------------------------------------------------------

Write-Host ''
Write-Host '========== BUILD COMPLETED ==========' -ForegroundColor Green

Write-Host ''
Write-Host 'Generated files:' -ForegroundColor Cyan

Get-ChildItem -Path $outputDir -File |
    Select-Object Name, Length |
    Format-Table -AutoSize

Write-Host ''
Write-Host "Application: $($appLauncher.FullName)" -ForegroundColor Green
Write-Host "Runtime:     $runtimeDir" -ForegroundColor Green
Write-Host "JVM:         $($jvmDll.FullName)" -ForegroundColor Green
Write-Host "Installer:   $installerOutput" -ForegroundColor Green
Write-Host "Portable:    $portableZip" -ForegroundColor Green

Write-Host ''
Write-Host 'Build script finished.' -ForegroundColor Green
