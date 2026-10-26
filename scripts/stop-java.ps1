$ErrorActionPreference = "SilentlyContinue"
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
  $_.Path -and $_.CommandLine -match "chengwenfang-app"
} | Stop-Process -Force
Write-Host "Requested stop for chengwenfang-app Java processes (if any)."
