$ErrorActionPreference = 'Stop'

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw 'Gradle 8.10.2+ was not found on PATH.'
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/jpackage.exe'))) {
    throw "JDK 17 was not configured correctly. JAVA_HOME=$env:JAVA_HOME"
}

# IMPORTANT: use release tasks for BOTH outputs. The release app image is
# written under main-release/app, not main/app.
gradle packageReleaseExe createReleaseDistributable --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Gradle release packaging failed with exit code $LASTEXITCODE"
}

$releaseBase = Join-Path $PWD 'build/compose/binaries/main-release'
$appBase = Join-Path $releaseBase 'app'
$installerDir = Join-Path $releaseBase 'exe'

if (-not (Test-Path $appBase)) {
    Write-Host 'Compose release output tree:'
    if (Test-Path (Join-Path $PWD 'build/compose/binaries')) {
        Get-ChildItem (Join-Path $PWD 'build/compose/binaries') -Recurse -Force |
            Select-Object -ExpandProperty FullName | Write-Host
    }
    throw "Release app-image directory was not created: $appBase"
}

# Find the app image by its launcher, then verify the runtime inside that
# same app image. Do not inspect the debug output directory.
$launchers = @(Get-ChildItem -Path $appBase -Filter 'NetworkToolkit.exe' -File -Recurse)
$appImage = $null
$javaExe = $null
foreach ($launcher in $launchers) {
    $candidateImage = $launcher.Directory.FullName
    $candidateJava = Join-Path $candidateImage 'runtime/bin/java.exe'
    if (Test-Path $candidateJava) {
        $appImage = $candidateImage
        $javaExe = $candidateJava
        break
    }
}

if (-not $appImage) {
    Write-Host 'Release app-image file listing:'
    Get-ChildItem -Path $appBase -Recurse -Force |
        Select-Object -ExpandProperty FullName | Write-Host
    throw 'The RELEASE app image does not contain NetworkToolkit.exe together with runtime/bin/java.exe. Check the jpackage output above.'
}

& $javaExe -version
if ($LASTEXITCODE -ne 0) {
    throw "Bundled Java runtime failed to start: $javaExe"
}

if (-not (Test-Path $installerDir)) {
    throw "Release installer directory was not created: $installerDir"
}
$installer = Get-ChildItem -Path $installerDir -Filter '*.exe' -File |
    Where-Object { $_.Name -notlike '*uninstall*' } |
    Select-Object -First 1
if (-not $installer) {
    throw "NetworkToolkit installer EXE was not found in $installerDir"
}

$out = Join-Path $PWD 'artifacts'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Copy-Item -LiteralPath $installer.FullName -Destination (Join-Path $out 'NetworkToolkit-Setup.exe') -Force

$portableZip = Join-Path $out 'NetworkToolkit-Windows-x64-Portable.zip'
if (Test-Path $portableZip) { Remove-Item $portableZip -Force }
Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $portableZip -CompressionLevel Optimal

Write-Host "Verified RELEASE app image: $appImage" -ForegroundColor Green
Write-Host "Verified bundled JVM: $javaExe" -ForegroundColor Green
Write-Host "Installer: $(Join-Path $out 'NetworkToolkit-Setup.exe')"
Write-Host "Portable: $portableZip"
