package com.chengwenfang.publishing.domain.styling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

/**
 * 将受控主题 token 应用为内联样式，使公众号兼容 HTML 不依赖外部 CSS。
 */
public final class ThemeHtmlStylerUtil {
  /**
   * Jsoup 清洗会丢弃站内相对图片路径；清洗前临时改成绝对 URL，清洗后再还原。
   */
  private static final String RELATIVE_IMAGE_HOST = "https://chengwenfang.rel.invalid";
  /** 匹配站内图片相对路径 src。 */
  private static final Pattern RELATIVE_IMAGE_SRC = Pattern.compile(
      "(?i)(\\bsrc\\s*=\\s*[\"'])(/(?:api/(?:images|files)|assets)/[^\"']*)([\"'])");

  private static final Map<String, Object> DEFAULTS = Map.ofEntries(
      Map.entry("fontFamily", "Optima, PingFangSC-regular, serif"),
      Map.entry("fontSize", 16),
      Map.entry("lineHeight", 1.8),
      Map.entry("textColor", "#262626"),
      Map.entry("mutedColor", "#6b7280"),
      Map.entry("primaryColor", "#087ea4"),
      Map.entry("accentColor", "#e8f5f8"),
      Map.entry("contentWidth", 677),
      Map.entry("paragraphSpacing", 16),
      Map.entry("headingColor", "#111111"),
      Map.entry("headingFontSize", 24),
      Map.entry("paragraphPadding", 8),
      Map.entry("paragraphMargin", 10),
      Map.entry("blockquoteBorderColor", "#087ea4"),
      Map.entry("blockquoteBorderWidth", 4),
      Map.entry("blockquoteTextColor", "#6b7280"),
      Map.entry("imageRadius", 4),
      Map.entry("imageMargin", 18),
      Map.entry("rootBackground", "#ffffff"),
      Map.entry("headingBackground", "transparent"),
      Map.entry("headingTextColor", "#111111"),
      Map.entry("headingRadius", "0"),
      Map.entry("headingPadding", "0"),
      Map.entry("headingDisplay", "inline"),
      Map.entry("subheadingBorderColor", "#f44336"),
      Map.entry("headingWeight", 500),
      Map.entry("headingLineHeight", 1.5),
      Map.entry("inlineCodeBackground", "#f3f4f6"),
      Map.entry("inlineCodeColor", "#9654b5"));

  private ThemeHtmlStylerUtil() {}

  public static Map<String, Object> mergeConfig(Map<String, Object> config) {
    Map<String, Object> theme = new LinkedHashMap<>(DEFAULTS);
    if (config != null) {
      for (Map.Entry<String, Object> entry : config.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          theme.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return theme;
  }

  public static String style(String bodyHtml, Map<String, Object> config) {
    Map<String, Object> theme = mergeConfig(config);
    Document doc = Jsoup.parseBodyFragment(bodyHtml == null ? "" : bodyHtml);
    doc.outputSettings()
        .prettyPrint(false)
        .escapeMode(Entities.EscapeMode.xhtml);

    for (Element heading : doc.body().select("h1, h2, h3, h4, h5, h6")) {
      styleHeading(heading, theme);
    }
    for (Element paragraph : doc.body().select("p")) {
      boolean inQuote = paragraph.parents().stream().anyMatch(el -> "blockquote".equals(el.normalName()));
      String margin = inQuote ? "0" : (intValue(theme, "paragraphMargin") + "px 0");
      paragraph.attr(
          "style",
          "box-sizing:border-box;cursor:pointer;color:"
              + css(theme, "mutedColor")
              + ";font-size:15px;line-height:"
              + floatValue(theme, "lineHeight")
              + "em;letter-spacing:0.02em;text-align:left;text-indent:0;padding:"
              + intValue(theme, "paragraphPadding")
              + "px 0;font-weight:normal;background-color:transparent;margin:"
              + margin
              + ";");
    }
    for (Element quote : doc.body().select("blockquote")) {
      quote.attr(
          "style",
          "box-sizing:border-box;margin:20px 0;cursor:pointer;padding:10px 10px 10px 20px;"
              + "overflow:auto;border-style:none solid;border-width:3px 1px 3px "
              + intValue(theme, "blockquoteBorderWidth")
              + "px;border-color:rgba(0,0,0,0.4) "
              + css(theme, "blockquoteBorderColor")
              + ";border-radius:0;background:"
              + css(theme, "accentColor")
              + ";color:"
              + css(theme, "blockquoteTextColor")
              + ";font-family:"
              + css(theme, "fontFamily")
              + ";font-size:16px;line-height:1.6;text-align:left;");
    }
    for (Element list : doc.body().select("ul, ol")) {
      String type = "ul".equals(list.normalName()) ? "disc" : "decimal";
      list.attr(
          "style",
          "box-sizing:border-box;margin:8px 0;cursor:pointer;list-style-type:"
              + type
              + ";padding:0 0 0 25px;color:#000000;background-color:transparent;font-family:"
              + css(theme, "fontFamily")
              + ";font-size:16px;line-height:1.6;text-align:left;");
    }
    for (Element li : doc.body().select("li")) {
      li.attr("style", "box-sizing:border-box;cursor:pointer;margin:0;");
      if (li.select("> section").isEmpty()) {
        Element section = new Element("section");
        section.attr(
            "style",
            "box-sizing:border-box;cursor:pointer;margin-top:5px;margin-bottom:5px;color:"
                + css(theme, "mutedColor")
                + ";font-size:15px;line-height:1.8em;letter-spacing:0.02em;"
                + "text-align:left;font-weight:normal;");
        section.appendChildren(li.childNodes());
        li.empty().appendChild(section);
      }
    }
    for (Element code : doc.body().select("code")) {
      if (code.parent() != null && "pre".equals(code.parent().normalName())) {
        code.attr(
            "style",
            "font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:14px;line-height:1.6;");
      } else {
        code.attr(
            "style",
            "box-sizing:border-box;padding:2px 4px;border-radius:3px;background:"
                + css(theme, "inlineCodeBackground")
                + ";color:"
                + css(theme, "inlineCodeColor")
                + ";font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:0.9em;");
      }
    }
    for (Element pre : doc.body().select("pre")) {
      pre.attr(
          "style",
          "margin:16px 0;padding:14px;overflow:auto;background:#f3f4f6;border-radius:4px;"
              + "white-space:pre-wrap;word-break:break-word;");
    }
    for (Element img : doc.body().select("img")) {
      img.attr(
          "style",
          "box-sizing:border-box;display:block;max-width:100%;height:auto;margin:"
              + intValue(theme, "imageMargin")
              + "px auto;border-radius:"
              + intValue(theme, "imageRadius")
              + "px;");
    }
    for (Element anchor : doc.body().select("a")) {
      anchor.attr("style", "color:" + css(theme, "primaryColor") + ";text-decoration:underline;");
    }
    for (Element hr : doc.body().select("hr")) {
      Element div = new Element("div");
      div.attr(
          "style",
          "height:1px;margin:24px 0;background:" + css(theme, "accentColor") + ";");
      hr.replaceWith(div);
    }

    String inner = doc.body().html();
    Safelist safelist = Safelist.relaxed()
        .addTags("section", "span", "div")
        .addAttributes(":all", "style", "class")
        .addAttributes("code", "data-language")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "http", "https", "data");
    // 清洗前保护站内相对图床路径，清洗后还原，避免预览空白
    String protectedInner = protectRelativeImageSrc(inner);
    String cleaned = Jsoup.clean(
        protectedInner, "", safelist, new Document.OutputSettings().prettyPrint(false));
    cleaned = restoreRelativeImageSrc(cleaned);
    return "<section style=\"box-sizing:border-box;width:"
        + intValue(theme, "contentWidth")
        + "px;max-width:100%;margin:0 auto;padding:0;background:"
        + css(theme, "rootBackground")
        + ";font-family:"
        + css(theme, "fontFamily")
        + ";font-size:"
        + intValue(theme, "fontSize")
        + "px;line-height:"
        + floatValue(theme, "lineHeight")
        + ";color:"
        + css(theme, "textColor")
        + ";\">"
        + cleaned
        + "</section>";
  }

  /**
   * 将站内相对图片 src 临时改为绝对 URL，以便通过协议白名单。
   *
   * @param html 待清洗 HTML
   * @return 保护后的 HTML
   */
  static String protectRelativeImageSrc(String html) {
    if (html == null || html.isBlank()) {
      return html == null ? "" : html;
    }
    return RELATIVE_IMAGE_SRC.matcher(html).replaceAll("$1" + RELATIVE_IMAGE_HOST + "$2$3");
  }

  /**
   * 还原清洗阶段临时改写的站内图片路径。
   *
   * @param html 清洗后 HTML
   * @return 还原相对路径后的 HTML
   */
  static String restoreRelativeImageSrc(String html) {
    if (html == null || html.isBlank()) {
      return html == null ? "" : html;
    }
    return html.replace(RELATIVE_IMAGE_HOST, "");
  }

  private static void styleHeading(Element heading, Map<String, Object> theme) {
    int level = Character.isDigit(heading.normalName().charAt(1))
        ? Character.getNumericValue(heading.normalName().charAt(1))
        : 3;
    int size = level == 2
        ? intValue(theme, "headingFontSize")
        : Math.max(18, intValue(theme, "headingFontSize") - 6);
    String headingColor = level == 2
        ? css(theme, "headingTextColor", css(theme, "headingColor"))
        : css(theme, "headingColor");
    String headingBackground = level == 2
        ? css(theme, "rootBackground", "#fbfbfb")
        : "#ffffff";
    String base =
        "box-sizing:border-box;margin:30px 0 15px;color:rgba(0,0,0,0.85);background:"
            + headingBackground
            + ";font-size:"
            + (level == 2 || level == 3 ? 16 : size)
            + "px;line-height:1.5em;font-weight:500;position:relative;text-align:left;";
    if (level == 3) {
      heading.attr(
          "style",
          base
              + "padding:8px 0 8px 12px;border-left:4px solid "
              + css(theme, "subheadingBorderColor")
              + ";cursor:pointer;");
      return;
    }
    heading.attr("style", base + "padding:0;cursor:pointer;");
    if (level == 2) {
      Element span = new Element("span").addClass("content");
      span.attr(
          "style",
          "box-sizing:border-box;display:"
              + css(theme, "headingDisplay", "inline-block")
              + ";background:"
              + css(theme, "headingBackground")
              + ";color:"
              + headingColor
              + ";border-radius:"
              + css(theme, "headingRadius")
              + ";padding:"
              + css(theme, "headingPadding", "0 30px 0 15px")
              + ";font-size:16px;line-height:"
              + floatValue(theme, "headingLineHeight")
              + ";font-weight:"
              + intValue(theme, "headingWeight")
              + ";");
      span.appendChildren(heading.childNodes());
      if (span.childNodeSize() == 0) {
        span.appendChild(new TextNode(heading.text()));
      }
      heading.empty().appendChild(span);
    }
  }

  private static String css(Map<String, Object> theme, String key) {
    return css(theme, key, String.valueOf(DEFAULTS.getOrDefault(key, "")));
  }

  private static String css(Map<String, Object> theme, String key, String fallback) {
    Object value = theme.getOrDefault(key, fallback);
    String text = value == null ? fallback : String.valueOf(value);
    return text.replace("\"", "'").replace(";", "");
  }

  private static int intValue(Map<String, Object> theme, String key) {
    Object value = theme.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception ignored) {
      Object fallback = DEFAULTS.get(key);
      return fallback instanceof Number number ? number.intValue() : 0;
    }
  }

  private static String floatValue(Map<String, Object> theme, String key) {
    Object value = theme.get(key);
    double parsed;
    if (value instanceof Number numberValue) {
      parsed = numberValue.doubleValue();
    } else {
      try {
        parsed = Double.parseDouble(String.valueOf(value));
      } catch (Exception ignored) {
        Object fallback = DEFAULTS.get(key);
        parsed = fallback instanceof Number numberFallback ? numberFallback.doubleValue() : 1.0;
      }
    }
    if (Math.rint(parsed) == parsed) {
      return String.valueOf((int) parsed);
    }
    return String.valueOf(parsed);
  }
}
