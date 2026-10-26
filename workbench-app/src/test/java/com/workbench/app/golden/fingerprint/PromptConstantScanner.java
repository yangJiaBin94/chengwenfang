package com.workbench.app.golden.fingerprint;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 扫描主代码中名称匹配提示词模式的 {@code static final String}，生成「全限定名 → 值 SHA-256」。
 */
public final class PromptConstantScanner {
  /** 匹配声明行：修饰符 + static final String NAME。 */
  private static final Pattern DECL =
      Pattern.compile(
          "(?:public|private|protected)\\s+static\\s+final\\s+String\\s+([A-Za-z0-9_]+)\\s*=");

  private PromptConstantScanner() {}

  /**
   * 是否属于指纹关注的常量名。
   *
   * @param name 常量简单名
   * @return 匹配为 true
   */
  public static boolean matchesName(String name) {
    if (StrUtil.isBlank(name)) {
      return false;
    }
    String upper = name.toUpperCase(Locale.ROOT);
    return upper.contains("PROMPT")
        || upper.endsWith("_SYSTEM")
        || upper.contains("RULES")
        || upper.contains("PREAMBLE");
  }

  /**
   * 扫描仓库全部 {@code src/main/java}。
   *
   * @param repoRoot 仓库根
   * @return 有序 map：全限定常量名 → 值的 SHA-256（小写 hex）
   * @throws IOException 读文件失败
   */
  public static Map<String, String> scan(Path repoRoot) throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    // 只扫各 Maven 模块的 main 源码，避开 .workbench / target 等不可读目录
    try (Stream<Path> modules = Files.list(repoRoot)) {
      modules
          .filter(Files::isDirectory)
          .filter(path -> path.getFileName().toString().startsWith("workbench-"))
          .map(path -> path.resolve("src/main/java"))
          .filter(Files::isDirectory)
          .sorted()
          .forEach(javaRoot -> {
            try (Stream<Path> walk = Files.walk(javaRoot)) {
              walk.filter(path -> path.getFileName().toString().endsWith(".java"))
                  .sorted()
                  .forEach(path -> {
                    try {
                      scanFile(repoRoot, path, out);
                    } catch (IOException exception) {
                      throw new IllegalStateException("扫描失败: " + path, exception);
                    }
                  });
            } catch (IOException exception) {
              throw new IllegalStateException("遍历失败: " + javaRoot, exception);
            }
          });
    }
    return out;
  }

  /**
   * 扫描单个 Java 源文件。
   *
   * @param repoRoot 仓库根（未使用，保留对称）
   * @param file     源文件
   * @param out      写入目标
   * @throws IOException 读失败
   */
  static void scanFile(Path repoRoot, Path file, Map<String, String> out) throws IOException {
    String source = Files.readString(file, StandardCharsets.UTF_8);
    String packageName = extractPackage(source);
    String className = file.getFileName().toString().replace(".java", "");
    String fqClass = StrUtil.isBlank(packageName) ? className : packageName + "." + className;
    Matcher matcher = DECL.matcher(source);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!matchesName(name)) {
        continue;
      }
      int valueStart = matcher.end();
      String value = parseStringValue(source, valueStart);
      if (value == null) {
        throw new IllegalStateException("无法解析常量值: " + fqClass + "." + name + " @ " + file);
      }
      out.put(fqClass + "." + name, DigestUtil.sha256Hex(value));
    }
  }

  /**
   * 从声明等号后解析字符串常量值（支持文本块与 {@code +} 拼接）。
   *
   * @param source     源码
   * @param fromIndex  等号后起始下标
   * @return 运行时拼接后的字符串；失败返回 null
   */
  static String parseStringValue(String source, int fromIndex) {
    int index = skipWhitespaceAndComments(source, fromIndex);
    if (index >= source.length()) {
      return null;
    }
    StringBuilder value = new StringBuilder();
    while (index < source.length()) {
      index = skipWhitespaceAndComments(source, index);
      if (index >= source.length()) {
        break;
      }
      if (source.startsWith("\"\"\"", index)) {
        int close = source.indexOf("\"\"\"", index + 3);
        if (close < 0) {
          return null;
        }
        String raw = source.substring(index + 3, close);
        value.append(stripTextBlock(raw));
        index = close + 3;
      } else if (source.charAt(index) == '"') {
        ParsedString parsed = parseClassicString(source, index);
        if (parsed == null) {
          return null;
        }
        value.append(parsed.value);
        index = parsed.endIndex;
      } else if (source.charAt(index) == ';') {
        break;
      } else if (source.charAt(index) == '+') {
        index++;
        continue;
      } else {
        // 非字符串续接（如方法调用）则放弃
        return value.length() == 0 ? null : value.toString();
      }
      index = skipWhitespaceAndComments(source, index);
      if (index < source.length() && source.charAt(index) == '+') {
        index++;
        continue;
      }
      if (index < source.length() && source.charAt(index) == ';') {
        break;
      }
      if (index < source.length() && source.charAt(index) != '+') {
        break;
      }
    }
    return value.toString();
  }

  /**
   * 文本块去公共缩进（近似 javac 规则：去掉结尾换行后的公共空白前缀）。
   *
   * @param raw 文本块内部原文
   * @return 去缩进后的内容
   */
  static String stripTextBlock(String raw) {
    String content = raw;
    if (content.startsWith("\r\n")) {
      content = content.substring(2);
    } else if (content.startsWith("\n")) {
      content = content.substring(1);
    }
    String[] lines = content.split("\n", -1);
    int minIndent = Integer.MAX_VALUE;
    for (String line : lines) {
      if (line.isBlank()) {
        continue;
      }
      int indent = 0;
      while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
        indent++;
      }
      minIndent = Math.min(minIndent, indent);
    }
    if (minIndent == Integer.MAX_VALUE) {
      minIndent = 0;
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.length() >= minIndent) {
        out.append(line.substring(minIndent));
      } else {
        out.append(line.stripLeading());
      }
      if (i < lines.length - 1) {
        out.append('\n');
      }
    }
    return out.toString();
  }

  private static ParsedString parseClassicString(String source, int startQuote) {
    StringBuilder value = new StringBuilder();
    int index = startQuote + 1;
    while (index < source.length()) {
      char ch = source.charAt(index);
      if (ch == '\\') {
        if (index + 1 >= source.length()) {
          return null;
        }
        char next = source.charAt(index + 1);
        value.append(switch (next) {
          case 'n' -> '\n';
          case 'r' -> '\r';
          case 't' -> '\t';
          default -> next;
        });
        index += 2;
        continue;
      }
      if (ch == '"') {
        return new ParsedString(value.toString(), index + 1);
      }
      value.append(ch);
      index++;
    }
    return null;
  }

  private static int skipWhitespaceAndComments(String source, int index) {
    int cursor = index;
    while (cursor < source.length()) {
      char ch = source.charAt(cursor);
      if (Character.isWhitespace(ch)) {
        cursor++;
        continue;
      }
      if (source.startsWith("//", cursor)) {
        int eol = source.indexOf('\n', cursor);
        cursor = eol < 0 ? source.length() : eol + 1;
        continue;
      }
      if (source.startsWith("/*", cursor)) {
        int end = source.indexOf("*/", cursor + 2);
        cursor = end < 0 ? source.length() : end + 2;
        continue;
      }
      break;
    }
    return cursor;
  }

  private static String extractPackage(String source) {
    Matcher matcher = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE).matcher(source);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  /**
   * 经典双引号字符串解析结果。
   *
   * @param value    解码后的值
   * @param endIndex 闭合引号后的下标
   */
  private record ParsedString(String value, int endIndex) {}
}
