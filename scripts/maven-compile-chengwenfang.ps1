# Debug 启动前安装到本地仓库；以进程退出码回传 VS Code 任务状态。
# 用 install 而非仅 compile：IDE 运行时常从 localRepository 解析兄弟模块 jar。
# 经 cmd /c 调 mvn.cmd，避免 PowerShell 挂住导致 preLaunchTask 一直「等待」。
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\use-utf8.ps1"

cmd /c "mvn -pl chengwenfang-app -am -DskipTests install"
$code = $LASTEXITCODE
if ($null -eq $code) {
  $code = 0
}
[Environment]::Exit($code)
