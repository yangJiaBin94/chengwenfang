package com.chengwenfang.infra.support;

import cn.hutool.core.util.StrUtil;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 按操作系统选择本机 Python CLI 的 venv 解释器。
 * <p>配置里写成另一套系统的路径（例如非 Windows 仍留着 {@code Scripts/python.exe}）时，
 * 自动改用当前系统的 venv，调用方不必改配置文件。
 */
public final class OsPythonExecutableResolver {

  /** Windows venv 解释器，相对 CLI 工作目录。 */
  public static final String WINDOWS_VENV_PYTHON = ".venv/Scripts/python.exe";

  /** macOS / Linux venv 解释器，相对 CLI 工作目录。 */
  public static final String UNIX_VENV_PYTHON = ".venv/bin/python";

  private OsPythonExecutableResolver() {}

  /**
   * 解析当前系统应使用的 Python 可执行文件。
   *
   * @param contentRoot       仓库根；相对路径以此为基准
   * @param workingDirectory  CLI 工作目录（相对仓库根或绝对路径）
   * @param configured        配置中的解释器；可空、命令名，或显式路径
   * @return 应交给子进程的路径（venv 尚未安装时仍返回本机应有的 venv 路径）
   */
  public static Path resolve(Path contentRoot, String workingDirectory, String configured) {
    return resolve(contentRoot, workingDirectory, configured, isWindowsOs());
  }

  /**
   * 解析 Python 可执行文件（可指定是否按 Windows 布局，便于测试）。
   *
   * @param contentRoot       仓库根
   * @param workingDirectory  CLI 工作目录
   * @param configured        配置中的解释器
   * @param windows           为 true 时使用 {@code Scripts/python.exe}
   * @return 解释器路径
   */
  public static Path resolve(
      Path contentRoot, String workingDirectory, String configured, boolean windows) {
    Path root = contentRoot == null ? Path.of(".").toAbsolutePath() : contentRoot.toAbsolutePath();
    Path osVenv = venvPython(root, workingDirectory, windows);
    // 配置写成另一套系统的 venv 时直接忽略，避免非 Windows 还要改 yml
    if (isForeignOsLayout(configured, windows)) {
      return osVenv;
    }
    Path configuredPath = toAbsolute(root, configured);
    if (isExistingFile(configuredPath)) {
      return configuredPath;
    }
    if (isExistingFile(osVenv) || isGenericCommand(configured)) {
      return osVenv;
    }
    if (configuredPath == null) {
      return osVenv;
    }
    return configuredPath;
  }

  /**
   * 当前进程是否为 Windows。
   *
   * @return Windows 为 true
   */
  public static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return StrUtil.containsIgnoreCase(osName, "win");
  }

  /**
   * 本机 venv 解释器的绝对路径（文件可以尚不存在）。
   *
   * @param contentRoot      仓库根
   * @param workingDirectory CLI 工作目录
   * @param windows          是否 Windows 布局
   * @return 绝对路径
   */
  static Path venvPython(Path contentRoot, String workingDirectory, boolean windows) {
    Path root = contentRoot == null ? Path.of(".").toAbsolutePath() : contentRoot.toAbsolutePath();
    String relative = windows ? WINDOWS_VENV_PYTHON : UNIX_VENV_PYTHON;
    Path work = toAbsolute(root, StrUtil.blankToDefault(workingDirectory, "."));
    if (work == null) {
      work = root;
    }
    return work.resolve(relative).normalize();
  }

  /**
   * 配置值是否写成了另一套操作系统的 venv 路径。
   *
   * @param configured 配置值
   * @param windows    当前是否按 Windows 解析
   * @return 跨系统路径则为 true
   */
  static boolean isForeignOsLayout(String configured, boolean windows) {
    if (StrUtil.isBlank(configured)) {
      return false;
    }
    String normalized = configured.replace('\\', '/').toLowerCase();
    boolean looksWindows = normalized.contains("/scripts/python.exe") || normalized.endsWith("python.exe");
    boolean looksUnix = normalized.contains("/.venv/bin/python") || normalized.endsWith("/bin/python");
    if (windows) {
      return looksUnix && !looksWindows;
    }
    return looksWindows;
  }

  private static boolean isGenericCommand(String configured) {
    if (StrUtil.isBlank(configured)) {
      return true;
    }
    String name = configured.trim().toLowerCase();
    return "python".equals(name) || "python3".equals(name) || "py".equals(name);
  }

  private static Path toAbsolute(Path root, String configured) {
    if (StrUtil.isBlank(configured)) {
      return null;
    }
    Path path = Path.of(configured.trim());
    if (path.isAbsolute()) {
      return path.normalize();
    }
    return root.resolve(path).toAbsolutePath().normalize();
  }

  private static boolean isExistingFile(Path path) {
    return path != null && Files.isRegularFile(path);
  }
}
