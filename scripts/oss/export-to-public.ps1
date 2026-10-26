#Requires -Version 5.1
<#
.SYNOPSIS
  Export a clean Apache-2.0 public tree (no private git history / course content).

.DESCRIPTION
  Copies the private workbench working tree into a sibling (or -Dest) directory,
  applying the hard exclude list from the open-source migration plan.
  Replaces default-themes.json with the demo seed (default-themes.oss.json).
  Optionally git init + single commit + push to a new/existing public remote.

.PARAMETER Dest
  Destination directory. Default: ../local-writer-workbench next to this repo.

.PARAMETER InitGit
  After export, git init and create one commit if Dest is not already a git repo.

.PARAMETER CreateRemote
  With -InitGit: gh repo create --public and push (requires gh auth).

.PARAMETER RemoteName
  GitHub repo name under the authenticated user. Default: local-writer-workbench
#>
param(
  [string]$Dest = "",
  [switch]$InitGit,
  [switch]$CreateRemote,
  [string]$RemoteName = "local-writer-workbench"
)

$ErrorActionPreference = "Stop"

$PrivateRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not $Dest) {
  $Dest = Join-Path (Split-Path $PrivateRoot -Parent) "local-writer-workbench"
}

Write-Host "Private: $PrivateRoot"
Write-Host "Dest:    $Dest"

if (Test-Path $Dest) {
  $existing = Get-ChildItem -Force $Dest -ErrorAction SilentlyContinue
  if ($existing) {
    throw "Dest is not empty: $Dest — delete it or choose another path."
  }
}

New-Item -ItemType Directory -Path $Dest -Force | Out-Null

# robocopy: exclude dirs that must never enter the public tree
$excludeDirs = @(
  ".git",
  ".idea",
  "node_modules",
  "target",
  "dist",
  ".workbench",
  ".workbench-it",
  "dev",
  "hotnews",
  "renders",
  "repairs",
  "works",
  "video-transcripts",
  "workflow-artifacts",
  "learning-content",
  "refactor-v2",
  ".venv",
  "__pycache__",
  ".pytest_cache",
  ".mypy_cache",
  ".ruff_cache"
)

$excludeFiles = @(
  ".env",
  "settings.xml",
  "workbench-data.json",
  "*.tsbuildinfo",
  "dev-*.log",
  "dev-*.err.log",
  "dev-*.out.log"
)

$xd = @(); foreach ($d in $excludeDirs) { $xd += "/XD"; $xd += $d }
$xf = @(); foreach ($f in $excludeFiles) { $xf += "/XF"; $xf += $f }

# /E copy subdirs including empty; /NFL /NDL quieter; /NJH /NJS; exit codes 0-7 OK
$robolog = Join-Path $env:TEMP "workbench-oss-export.log"
$args = @($PrivateRoot, $Dest, "/E", "/COPY:DAT", "/R:1", "/W:1", "/NFL", "/NDL", "/NJH", "/NJS", "/NP", "/LOG:$robolog") + $xd + $xf
Write-Host "Running robocopy (log: $robolog)..."
& robocopy @args
$rc = $LASTEXITCODE
if ($rc -ge 8) {
  throw "robocopy failed with exit code $rc — see $robolog"
}

# Demo themes for public seed (private keeps its own default-themes.json)
$ossThemes = Join-Path $Dest "scripts\bootstrap\default-themes.oss.json"
$pubThemes = Join-Path $Dest "scripts\bootstrap\default-themes.json"
if (-not (Test-Path $ossThemes)) {
  throw "Missing default-themes.oss.json after export"
}
Copy-Item -Force $ossThemes $pubThemes

# Ensure content/ only has README (learning-content already excluded by name)
$contentDir = Join-Path $Dest "content"
New-Item -ItemType Directory -Path $contentDir -Force | Out-Null
$readmeSrc = Join-Path $PrivateRoot "content\README.md"
if (Test-Path $readmeSrc) {
  Copy-Item -Force $readmeSrc (Join-Path $contentDir "README.md")
}

# Drop public mirrors of personal content if any slipped through
$pubContent = Join-Path $Dest "public\content"
if (Test-Path $pubContent) {
  Remove-Item -Recurse -Force $pubContent
}
$pubData = Join-Path $Dest "public\data\workbench-data.json"
if (Test-Path $pubData) {
  Remove-Item -Force $pubData
}

# Never ship machine-local Maven settings
$localSettings = Join-Path $Dest ".mvn\settings.xml"
if (Test-Path $localSettings) {
  Remove-Item -Force $localSettings
}

# Sanity checks
$leaks = @()
if (Test-Path (Join-Path $Dest "content\learning-content")) { $leaks += "content/learning-content" }
if (Test-Path (Join-Path $Dest "docs\refactor-v2")) { $leaks += "docs/refactor-v2" }
if (Test-Path (Join-Path $Dest ".mvn\settings.xml")) { $leaks += ".mvn/settings.xml" }
if (Test-Path (Join-Path $Dest "public\data\workbench-data.json")) { $leaks += "public/data/workbench-data.json" }
if ($leaks.Count -gt 0) {
  throw "Export still contains forbidden paths: $($leaks -join ', ')"
}

Write-Host "Export OK."

if (-not $InitGit) {
  Write-Host "Skip git (pass -InitGit to create initial commit)."
  return
}

Push-Location $Dest
try {
  if (Test-Path ".git") {
    throw "Dest already has .git — refusing to re-init"
  }
  git init -b main
  git add -A
  git status --short | Select-Object -First 40
  git commit -m "Initial public release under Apache-2.0"
  Write-Host "Created single initial commit."

  if ($CreateRemote) {
    $owner = (gh api user --jq .login).Trim()
    $fullName = "$owner/$RemoteName"
    gh repo create $RemoteName --public --source=. --remote=origin --push --description "Local AI content generation and review workbench"
    gh repo edit $fullName --add-topic java --add-topic spring-boot --add-topic react --add-topic ai --add-topic local-first
    Write-Host "Pushed to public repo: $fullName"
  } else {
    Write-Host "Remote not created. Example:"
    Write-Host "  cd `"$Dest`""
    Write-Host "  gh repo create $RemoteName --public --source=. --remote=origin --push"
  }
}
finally {
  Pop-Location
}
