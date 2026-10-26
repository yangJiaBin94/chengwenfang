# Import scripts/bootstrap/builtin-catalog.json (+ default-themes.json) into local MySQL.
# Requires Docker MySQL. Starts backend once with bootstrap-seed (Flyway + import) then exits.
# Existing same-name Skills / published workflows are skipped unless -Force.
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

$utf8 = Join-Path $Root "scripts\use-utf8.ps1"
if (Test-Path $utf8) {
    . $utf8
}

$catalog = (Resolve-Path (Join-Path $PSScriptRoot "builtin-catalog.json")).Path.Replace("\", "/")
$defaultThemes = (Resolve-Path (Join-Path $PSScriptRoot "default-themes.json")).Path.Replace("\", "/")
$forceValue = if ($Force) { "true" } else { "false" }
$appArgs = "--workbench.bootstrap.catalog=$catalog --workbench.bootstrap.default-themes=$defaultThemes --workbench.bootstrap.force=$forceValue"

Write-Host "Importing bootstrap catalog=$catalog defaultThemes=$defaultThemes force=$forceValue"
# Only run on workbench-app; -am hits parent POM and fails without mainClass
mvn -pl workbench-app -DskipTests spring-boot:run "-Dspring-boot.run.profiles=bootstrap-seed" "-Dspring-boot.run.arguments=$appArgs"
if ($LASTEXITCODE -ne 0) {
    throw "Bootstrap import failed, exit=$LASTEXITCODE"
}
Write-Host "Import done. Start the backend normally; log should show builtin config health OK."
