$ErrorActionPreference = 'Stop'
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
  Write-Host 'Gradle was not found. Install Gradle 8.10+ and JDK 17, then retry.' -ForegroundColor Yellow
  exit 1
}
gradle packageReleaseExe packageReleaseMsi createDistributable
Write-Host 'Build complete. Check build\compose\binaries\main-release\' -ForegroundColor Green
