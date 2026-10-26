package com.chengwenfang.quality.domain.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/**
 * 知识库一致性规则引擎：公众号排版禁令、AI 套话、公式样式/表达式与内容保留比检查。
 * 供课程终检与公众号审核复用。
 */
@Component
public class KnowledgeRulesEngine {
  /**
   * 单条知识库规则结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class KnowledgeRule {
    /** 规则编码。 */
    private final String code;
    /** 是否通过。 */
    private final boolean passed;
    /** 说明文案。 */
    private final String detail;
    /** 是否阻断发布。 */
    private final boolean blocking;
  }

  /**
   * AI 套话模式。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class Cliche {
    /** 展示标签。 */
    private final String label;
    /** 匹配模式。 */
    private final Pattern pattern;
  }

  private static final List<Cliche> AI_CLICHES = List.of(
      new Cliche("赋能", Pattern.compile("赋能")),
      new Cliche("打通", Pattern.compile("打通")),
      new Cliche("底层逻辑", Pattern.compile("底层逻辑")),
      new Cliche("全方位", Pattern.compile("全方位")),
      new Cliche("深度套话", Pattern.compile("深度(?:赋能|融合|解析|解读|剖析|挖掘|协同|思考)")),
      new Cliche("闭环", Pattern.compile("(?:形成|打造|构建|实现|完整)(?:[^，。；\\n]{0,8})闭环")),
      new Cliche("重塑", Pattern.compile("重塑(?:行业|格局|体验|未来|价值|能力|流程)")));

  private static final Pattern LATEX_FORMULA_COMMAND = Pattern.compile(
      "\\\\(?:operatorname|mathrm|text|frac|left|right|cdot|times|sqrt|sum|exp|log)\\b");
  private static final Pattern BLOCKQUOTE_CODE_SIGNAL = Pattern.compile(
      "^(?:"
          + "(?:&nbsp;)+"
          + "|(?:python|py)\\s+\\S+"
          + "|(?:from\\s+\\S+\\s+import|import\\s+\\S+)"
          + "|(?:class|def|if|elif|else|for|while|try|except|with|return)\\b"
          + "|(?:const|let|var)\\s+[A-Za-z_]\\w*\\s*="
          + "|[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*|\\[[^\\]]+\\])*\\s*="
          + "|[A-Za-z_]\\w*\\s*\\("
          + "|[\\[{].*[:＝=]"
          + ")");
  private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+)$", Pattern.MULTILINE);
  private static final Pattern COURSE_NUMBER = Pattern.compile("第\\s*\\d+\\s*[课篇章]");
  private static final Pattern HORIZONTAL_RULE = Pattern.compile("^\\s*(?:---+|\\*\\*\\*+|___+)\\s*$", Pattern.MULTILINE);
  private static final Pattern MARKDOWN_TABLE = Pattern.compile("^\\s*\\|.+\\|\\s*$", Pattern.MULTILINE);
  private static final Pattern WORKFLOW_PLACEHOLDER = Pattern.compile("^\\[\\[(?:COURSE_COVER|COURSE_IMAGE_\\d+)\\]\\]$");
  private static final Pattern NAMED_LATEX = Pattern.compile("\\\\(?:operatorname|mathrm|text)\\{([^{}]+)\\}");
  private static final String[][] FORMULA_MARKERS = {{"[", "]"}, {"\\[", "\\]"}, {"$$", "$$"}};

  /**
   * 对公众号正文执行知识库规则检查。
   *
   * @param article 公众号正文
   * @param source  可选课程原稿，用于 CONTENT_PRESERVATION
   * @return 规则结果列表（含 blocking 标记）
   */
  public List<KnowledgeRule> check(String article, String source) {
    String text = article == null ? "" : article;
    String proseWithoutCode = proseForQuoteCheck(text);
    Matcher headingMatcher = HEADING.matcher(text);
    String firstHeading = headingMatcher.find() ? headingMatcher.group(1) : null;
    boolean hasCourseNumber = firstHeading != null && COURSE_NUMBER.matcher(firstHeading).find();
    List<String> aiCliches = findAiCliches(text);
    List<KnowledgeRule> rules = new ArrayList<>();
    rules.add(new KnowledgeRule(
        "TITLE_NO_COURSE_NUMBER",
        !hasCourseNumber,
        "公众号标题不能包含“第几课”等课程编号。",
        hasCourseNumber));
    rules.add(new KnowledgeRule(
        "NO_HORIZONTAL_RULE",
        !HORIZONTAL_RULE.matcher(text).find(),
        "公众号正文不能使用 Markdown 水平分隔线。",
        false));
    rules.add(new KnowledgeRule(
        "NO_MARKDOWN_TABLE",
        !MARKDOWN_TABLE.matcher(text).find(),
        "公众号正文中的 Markdown 表格需要转换为可读列表。",
        false));
    rules.add(new KnowledgeRule(
        "NO_ENGLISH_QUOTES",
        !proseWithoutCode.contains("\""),
        "中文对话和引用应使用中文双引号。",
        false));
    boolean hasFence = text.contains("```");
    rules.add(new KnowledgeRule(
        "NO_CODE_FENCE",
        !hasFence,
        "公众号文章不能保留三个反引号组成的代码围栏。",
        hasFence));
    rules.add(new KnowledgeRule(
        "NO_AI_CLICHES",
        aiCliches.isEmpty(),
        aiCliches.isEmpty()
            ? "正文未检测到知识库规范中列出的 AI 套话和模板化词汇。"
            : "正文检测到可能的 AI 套话：" + String.join("、", aiCliches) + "。",
        false));
    boolean unstyled = hasUnstyledFormulaBlocks(text);
    rules.add(new KnowledgeRule(
        "FORMULA_BLOCK_STYLE",
        !unstyled,
        unstyled ? "发现未使用引用块样式的独立公式，应改为“> 公式”。" : "独立数学公式已使用 Markdown 引用块样式。",
        false));
    boolean latex = hasLatexFormulaCommands(text);
    rules.add(new KnowledgeRule(
        "FORMULA_PLAIN_EXPRESSION",
        !latex,
        latex ? "公式中仍有 LaTeX 排版命令，应转换为括号、斜杠和普通函数名。" : "公式已使用普通可读表达式，未残留 LaTeX 排版命令。",
        false));
    if (source != null && !source.isBlank()) {
      int sourceLen = Math.max(1, preservationSourceText(source).length());
      double ratio = text.strip().length() / (double) sourceLen;
      rules.add(new KnowledgeRule(
          "CONTENT_PRESERVATION",
          ratio >= 0.70,
          String.format(Locale.ROOT, "公众号与课程原稿的内容长度保留比例为 %.2f，要求不低于 0.70。", ratio),
          ratio < 0.70));
    }
    return rules;
  }

  /** 将独立公式规范为引用块样式，并尽量去掉 LaTeX 排版命令。 */
  public String normalizeFormulaBlocks(String content) {
    if (content == null) {
      return "";
    }
    String styled = normalizeFormulaDelimiters(content);
    String[] lines = styled.split("\\R", -1);
    for (int index = 0; index < lines.length; index++) {
      String stripped = lines[index].strip();
      if (!stripped.startsWith(">")) {
        continue;
      }
      String expression = stripped.substring(1).strip();
      if (looksLikeFormula(expression)) {
        lines[index] = "> " + normalizeFormulaExpression(expression);
      }
    }
    String result = String.join("\n", lines);
    return styled.endsWith("\n") && !result.endsWith("\n") ? result + "\n" : result;
  }

  /** 是否存在未使用引用块包裹的独立公式。 */
  public boolean hasUnstyledFormulaBlocks(String content) {
    return !normalizeFormulaDelimiters(content == null ? "" : content).equals(content == null ? "" : content);
  }

  /** 引用块公式中是否仍含 LaTeX 排版命令。 */
  public boolean hasLatexFormulaCommands(String content) {
    if (content == null) {
      return false;
    }
    for (String line : content.split("\\R", -1)) {
      if (line.strip().startsWith(">") && LATEX_FORMULA_COMMAND.matcher(line).find()) {
        return true;
      }
    }
    return false;
  }

  List<String> findAiCliches(String text) {
    List<String> matched = new ArrayList<>();
    for (Cliche cliche : AI_CLICHES) {
      if (cliche.pattern().matcher(text).find()) {
        matched.add(cliche.label());
      }
    }
    return matched;
  }

  String proseForQuoteCheck(String content) {
    String withoutCode = content.replaceAll("```[\\s\\S]*?```", "");
    withoutCode = withoutCode.replaceAll("`[^`\\n]+`", "");
    withoutCode = withoutCode.replaceAll("(!?\\[[^\\]\\n]*\\])\\([^\\n)]*\\)", "$1");
    withoutCode = withoutCode.replaceAll("<[^>\\n]+>", "");
    String[] lines = withoutCode.split("\\R", -1);
    List<String> prose = new ArrayList<>();
    int index = 0;
    while (index < lines.length) {
      if (!lines[index].matches("^\\s*>.*")) {
        if (!lines[index].matches("^(?: {4,}|\\t)\\S.*")) {
          prose.add(lines[index]);
        }
        index++;
        continue;
      }
      List<String> quoteBlock = new ArrayList<>();
      while (index < lines.length && lines[index].matches("^\\s*>.*")) {
        quoteBlock.add(lines[index]);
        index++;
      }
      List<String> quoteValues = new ArrayList<>();
      for (String line : quoteBlock) {
        quoteValues.add(line.replaceFirst("^\\s*>\\s?", "").strip());
      }
      boolean codeLike = quoteValues.stream().anyMatch(value -> BLOCKQUOTE_CODE_SIGNAL.matcher(value).find());
      if (!codeLike) {
        prose.addAll(quoteBlock);
      }
    }
    return String.join("\n", prose);
  }

  String preservationSourceText(String source) {
    String[] lines = source.split("\\R", -1);
    List<String> kept = new ArrayList<>();
    int index = 0;
    while (index < lines.length) {
      String marker = lines[index].replaceFirst("^>+\\s?", "").strip();
      String lower = marker.toLowerCase(Locale.ROOT);
      if (!marker.contains("插图位置") && !marker.contains("提示词") && !lower.contains("prompt")) {
        kept.add(lines[index]);
        index++;
        continue;
      }
      index++;
      boolean fenceOpened = false;
      while (index < lines.length) {
        String value = lines[index].replaceFirst("^>+\\s?", "").strip();
        if (value.startsWith("```")) {
          if (fenceOpened) {
            index++;
            break;
          }
          fenceOpened = true;
        } else if (!fenceOpened
            && !value.isEmpty()
            && !value.contains("提示词")
            && !value.toLowerCase(Locale.ROOT).contains("prompt")) {
          break;
        }
        index++;
      }
    }
    return String.join("\n", kept).strip();
  }

  String normalizeFormulaExpression(String expression) {
    String value = expression.strip();
    while (NAMED_LATEX.matcher(value).find()) {
      value = NAMED_LATEX.matcher(value).replaceAll("$1");
    }
    while (value.contains("\\frac")) {
      int command = value.indexOf("\\frac");
      int numeratorStart = command + "\\frac".length();
      Integer numeratorEnd = matchingBrace(value, numeratorStart);
      if (numeratorEnd == null || numeratorEnd + 1 >= value.length()) {
        break;
      }
      int denominatorStart = numeratorEnd + 1;
      Integer denominatorEnd = matchingBrace(value, denominatorStart);
      if (denominatorEnd == null) {
        break;
      }
      String numerator = normalizeFormulaExpression(value.substring(numeratorStart + 1, numeratorEnd));
      String denominator = normalizeFormulaExpression(value.substring(denominatorStart + 1, denominatorEnd));
      value = value.substring(0, command) + "(" + numerator + ") / (" + denominator + ")"
          + value.substring(denominatorEnd + 1);
    }
    value = Pattern.compile("\\^\\{([^{}]+)\\}").matcher(value).replaceAll(match -> {
      String body = match.group(1);
      if (body.length() > 1 || body.startsWith("-")) {
        return "^(" + body + ")";
      }
      return "^" + body;
    });
    value = value.replaceAll("_\\{([^{}]+)\\}", "_($1)");
    value = value.replace("\\left", "").replace("\\right", "")
        .replace("\\cdot", "·").replace("\\times", "×");
    value = value.replace('{', '(').replace('}', ')');
    value = value.replaceAll("\\s*=\\s*", " = ");
    value = value.replaceAll("(?<=[A-Za-z0-9)])\\s*([+-])\\s*(?=[A-Za-z(])", " $1 ");
    value = value.replaceAll("\\s*/\\s*", " / ");
    return value.replaceAll("[ \\t]+", " ").strip();
  }

  private String normalizeFormulaDelimiters(String content) {
    String[] lines = content.split("\\R", -1);
    List<String> normalized = new ArrayList<>();
    int index = 0;
    boolean changed = false;
    while (index < lines.length) {
      String stripped = lines[index].strip();
      if (WORKFLOW_PLACEHOLDER.matcher(stripped).matches()) {
        normalized.add(lines[index]);
        index++;
        continue;
      }
      boolean converted = false;
      for (String[] marker : FORMULA_MARKERS) {
        String opener = marker[0];
        String closer = marker[1];
        if (stripped.startsWith(opener) && stripped.endsWith(closer)
            && stripped.length() > opener.length() + closer.length()) {
          String body = stripped.substring(opener.length(), stripped.length() - closer.length()).strip();
          if (looksLikeFormula(body)) {
            normalized.add("> " + body);
            changed = converted = true;
            break;
          }
        }
        if (!stripped.equals(opener)) {
          continue;
        }
        List<String> formulaLines = new ArrayList<>();
        int cursor = index + 1;
        while (cursor < lines.length && !lines[cursor].strip().equals(closer)) {
          formulaLines.add(lines[cursor].strip());
          cursor++;
        }
        if (cursor < lines.length && looksLikeFormula(String.join(" ", formulaLines))) {
          for (String line : formulaLines) {
            if (!line.isEmpty()) {
              normalized.add("> " + line);
            }
          }
          index = cursor;
          changed = converted = true;
          break;
        }
      }
      if (!converted) {
        normalized.add(lines[index]);
      }
      index++;
    }
    if (!changed) {
      return content;
    }
    String result = String.join("\n", normalized);
    return content.endsWith("\n") ? result + "\n" : result;
  }

  private static boolean looksLikeFormula(String value) {
    return value.contains("=")
        || Pattern.compile("\\\\(?:operatorname|frac|sum|sqrt|cdot|times|exp|log)\\b").matcher(value).find()
        || Pattern.compile("[A-Za-z)][\\^_][A-Za-z0-9{]").matcher(value).find()
        || value.contains("·");
  }

  private static Integer matchingBrace(String value, int start) {
    if (start >= value.length() || value.charAt(start) != '{') {
      return null;
    }
    int depth = 0;
    for (int index = start; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (ch == '{') {
        depth++;
      } else if (ch == '}') {
        depth--;
        if (depth == 0) {
          return index;
        }
      }
    }
    return null;
  }
}
