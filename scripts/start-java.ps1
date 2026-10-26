param(
  [switch]$SkipCompose,
  [switch]$OpenBrowser,
  # 同时拉起 SearXNG + Lightpanda（compose profile: all）
  [switch]$AllDeps
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

. "$PSScriptRoot\use-utf8.ps1"

if (-not $SkipCompose) {
  if ($AllDeps) {
    Write-Host "Starting MySQL + Redis + SearXNG + Lightpanda..."
    docker compose --profile all up -d --wait
  } else {
    Write-Host "Starting MySQL + Redis (3306 / 6379)..."
    docker compose up -d --wait
  }
  if ($LASTEXITCODE -ne 0) { throw "docker compose failed" }
}

Write-Host "Building chengwenfang-app..."
mvn -pl chengwenfang-app -am -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed" }

Write-Host "Starting chengwenfang-app on :8001..."
$javaArgs = @(
  "-Dfile.encoding=UTF-8",
  "-Dstdout.encoding=UTF-8",
  "-Dstderr.encoding=UTF-8",
  "-Dsun.jnu.encoding=UTF-8",
  "-jar", "chengwenfang-app\target\chengwenfang-app-0.1.0-SNAPSHOT.jar"
)
Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $Root -WindowStyle Minimized

Write-Host "Starting Vite on :5173..."
Start-Process -FilePath "npm" -ArgumentList "run","dev:frontend" -WorkingDirectory $Root -WindowStyle Minimized

if ($OpenBrowser) {
  Start-Sleep -Seconds 8
  Start-Process "http://127.0.0.1:5173"
}

Write-Host "Done. Health: http://127.0.0.1:8001/api/health"
