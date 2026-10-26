$ErrorActionPreference = "SilentlyContinue"
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
  $_.Path -and $_.CommandLine -match "workbench-app"
} | Stop-Process -Force
Write-Host "Requested stop for workbench-app Java processes (if any)."
