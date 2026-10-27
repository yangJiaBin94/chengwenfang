param(
  [switch]$SkipCompose,
  [switch]$OpenBrowser,
  # 同时拉起 SearXNG + Lightpanda（compose profile: all）
  [switch]$AllDeps,
  # 与另一成文坊实例并存：后端 8002、前端 5174、Redis DB 1（不改 compose MySQL 端口）
  [switch]$BesidePrivate
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

. "$PSScriptRoot\use-utf8.ps1"

$backendPort = if ($BesidePrivate) { 8002 } else { 8001 }
$frontendPort = if ($BesidePrivate) { 5174 } else { 5173 }
$redisDb = if ($BesidePrivate) { "1" } else { "0" }

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

Write-Host "Starting chengwenfang-app on :$backendPort (redis db=$redisDb)..."
$javaArgs = @(
  "-Dfile.encoding=UTF-8",
  "-Dstdout.encoding=UTF-8",
  "-Dstderr.encoding=UTF-8",
  "-Dsun.jnu.encoding=UTF-8",
  "-Dserver.port=$backendPort",
  "-Dspring.data.redis.database=$redisDb",
  "-jar", "chengwenfang-app\target\chengwenfang-app.jar"
)
Start-Process -FilePath "java" -ArgumentList $javaArgs -WorkingDirectory $Root -WindowStyle Minimized

Write-Host "Starting Vite on :$frontendPort..."
$viteCmd = Join-Path $Root "node_modules\.bin\vite.cmd"
if (-not (Test-Path $viteCmd)) {
  Write-Host "node_modules 缺少 vite，先执行 npm install..."
  npm install
  if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
}
$env:VITE_DEV_PORT = "$frontendPort"
$env:VITE_API_PROXY = "http://127.0.0.1:$backendPort"
Start-Process -FilePath $viteCmd -ArgumentList "--host","127.0.0.1","--port","$frontendPort" -WorkingDirectory $Root -WindowStyle Minimized

if ($OpenBrowser) {
  Start-Sleep -Seconds 8
  Start-Process "http://127.0.0.1:$frontendPort"
}

Write-Host "Done. Health: http://127.0.0.1:$backendPort/api/health"
