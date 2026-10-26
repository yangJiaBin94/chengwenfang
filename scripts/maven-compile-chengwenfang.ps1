# Debug 启动前安装到本地仓库；以脚本进程退出码回传 VS Code 任务状态。
# 用 install 而非仅 compile：IDE 运行时常从 localRepository 解析兄弟模块 jar，
# 只 compile 时 target/classes 已更新但 ~/.m2 或错误仓库里的 SNAPSHOT 仍缺类。
$ErrorActionPreference = "Continue"
. "$PSScriptRoot\use-utf8.ps1"

& mvn -pl chengwenfang-app -am -DskipTests install
$code = $LASTEXITCODE
if ($null -eq $code) {
  $code = 0
}
exit $code
