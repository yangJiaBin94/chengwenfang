# 将当前 PowerShell 会话切到 UTF-8，供 Cursor 终端 / Agent Shell / 本地脚本共用。
# 用法：. .\scripts\use-utf8.ps1

$ErrorActionPreference = "SilentlyContinue"

try {
  chcp 65001 | Out-Null
} catch {}

$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$global:OutputEncoding = $utf8
$PSDefaultParameterValues["*:Encoding"] = "utf8"

$jvmUtf8 = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
  $env:JAVA_TOOL_OPTIONS = $jvmUtf8
} elseif ($env:JAVA_TOOL_OPTIONS -notmatch "stdout\.encoding") {
  $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS.Trim() + " " + $jvmUtf8).Trim()
}

$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUTF8 = "1"
