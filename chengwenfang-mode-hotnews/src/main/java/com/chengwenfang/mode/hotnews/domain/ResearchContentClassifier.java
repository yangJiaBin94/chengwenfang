package com.chengwenfang.mode.hotnews.domain;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 选题资料内容形态与语种启发式：文章/视频、站点来源、是否需要译成中文。
 */
public final class ResearchContentClassifier {
  /** 无法从 URL 识别站点时的展示名。 */
  public static final String UNKNOWN_SITE_LABEL = "未知来源";

  private static final Set<String> VIDEO_HOSTS = Set.of(
      "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
      "bilibili.com", "www.bilibili.com", "m.bilibili.com", "b23.tv",
      "douyin.com", "www.douyin.com", "v.douyin.com",
      "tiktok.com", "www.tiktok.com",
      "vimeo.com", "www.vimeo.com",
      "youku.com", "v.youku.com",
      "iqiyi.com", "www.iqiyi.com");
  /**
   * SQL LIKE 用的视频域名片段（去重、不含 www. 前缀变体）。
   */
  private static final List<String> VIDEO_HOST_SQL_KEYWORDS = List.of(
      "youtube.com", "youtu.be", "bilibili.com", "b23.tv",
      "douyin.com", "tiktok.com", "vimeo.com", "youku.com", "iqiyi.com");
  /**
   * SQL LIKE 用的视频路径片段，近似 {@link #VIDEO_PATH}。
   */
  private static final List<String> VIDEO_PATH_SQL_KEYWORDS = List.of(
      "/watch", "/video/", "/videos/", "/reel/", "/shorts", "v=", "/av", "/BV", "/bv");
  /**
   * SQL LIKE 用的标题关键词，近似 {@link #VIDEO_TITLE}。
   */
  private static final List<String> VIDEO_TITLE_SQL_KEYWORDS = List.of(
      "video", "youtube", "bilibili", "抖音", "短视频", "观看", "trailer", "pv");
  private static final Pattern VIDEO_PATH = Pattern.compile(
      "(?i)/(watch|video|videos|av\\d+|bv[0-9a-z]+|reel|shorts)(/|\\?|$)|[?&]v=");
  private static final Pattern VIDEO_TITLE = Pattern.compile(
      "(?i)(\\bvideo\\b|youtube|bilibili|抖音|短视频|观看|trailer|\\bpv\\b)");
  /**
   * 常见站点展示名；按插入顺序匹配，更具体的域名在前。
   */
  private static final Map<String, String> SITE_LABELS = new LinkedHashMap<>();

  static {
    SITE_LABELS.put("mp.weixin.qq.com", "微信公众号");
    SITE_LABELS.put("youtu.be", "YouTube");
    SITE_LABELS.put("youtube.com", "YouTube");
    SITE_LABELS.put("b23.tv", "B站");
    SITE_LABELS.put("bilibili.com", "B站");
    SITE_LABELS.put("douyin.com", "抖音");
    SITE_LABELS.put("tiktok.com", "TikTok");
    SITE_LABELS.put("vimeo.com", "Vimeo");
    SITE_LABELS.put("youku.com", "优酷");
    SITE_LABELS.put("iqiyi.com", "爱奇艺");
    SITE_LABELS.put("xiaohongshu.com", "小红书");
    SITE_LABELS.put("xhslink.com", "小红书");
    SITE_LABELS.put("zhihu.com", "知乎");
    SITE_LABELS.put("weibo.com", "微博");
    SITE_LABELS.put("x.com", "X");
    SITE_LABELS.put("twitter.com", "X");
    SITE_LABELS.put("github.com", "GitHub");
    SITE_LABELS.put("medium.com", "Medium");
    SITE_LABELS.put("sspai.com", "少数派");
    SITE_LABELS.put("36kr.com", "36氪");
    SITE_LABELS.put("jiemian.com", "界面新闻");
    SITE_LABELS.put("thepaper.cn", "澎湃新闻");
    SITE_LABELS.put("cls.cn", "财联社");
    SITE_LABELS.put("wallstreetcn.com", "华尔街见闻");
    SITE_LABELS.put("reuters.com", "路透社");
    SITE_LABELS.put("bloomberg.com", "彭博");
    SITE_LABELS.put("techcrunch.com", "TechCrunch");
    SITE_LABELS.put("theverge.com", "The Verge");
    SITE_LABELS.put("bbc.com", "BBC");
    SITE_LABELS.put("bbc.co.uk", "BBC");
    SITE_LABELS.put("cnn.com", "CNN");
    SITE_LABELS.put("nytimes.com", "纽约时报");
    SITE_LABELS.put("wikipedia.org", "维基百科");
    SITE_LABELS.put("csdn.net", "CSDN");
    SITE_LABELS.put("juejin.cn", "掘金");
    SITE_LABELS.put("infoq.cn", "InfoQ");
    SITE_LABELS.put("163.com", "网易");
    SITE_LABELS.put("sohu.com", "搜狐");
    SITE_LABELS.put("sina.com.cn", "新浪");
    SITE_LABELS.put("qq.com", "腾讯网");
  }

  private ResearchContentClassifier() {}

  /** 是否为视频资料（优先看 URL，其次标题关键词）。 */
  public static boolean isVideo(String url, String title) {
    String host = hostOf(url);
    if (!host.isBlank()) {
      for (String known : VIDEO_HOSTS) {
        if (host.equals(known) || host.endsWith("." + known.replace("www.", ""))) {
          return true;
        }
      }
      if (host.contains("youtube") || host.contains("bilibili") || host.contains("douyin")
          || host.contains("tiktok") || host.contains("youku") || host.contains("iqiyi")) {
        return true;
      }
    }
    String rawUrl = url == null ? "" : url.trim();
    if (!rawUrl.isBlank() && VIDEO_PATH.matcher(rawUrl).find()) {
      return true;
    }
    String rawTitle = title == null ? "" : title.trim();
    return !rawTitle.isBlank() && VIDEO_TITLE.matcher(rawTitle).find();
  }

  /** 展示标签：视频 / 文章。 */
  public static String mediaLabel(String url, String title) {
    return isVideo(url, title) ? "视频" : "文章";
  }

  /**
   * 供资料池 SQL 形态筛选的视频域名片段。
   *
   * @return 不可变列表
   */
  public static List<String> videoHostSqlKeywords() {
    return Collections.unmodifiableList(VIDEO_HOST_SQL_KEYWORDS);
  }

  /**
   * 供资料池 SQL 形态筛选的视频路径片段。
   *
   * @return 不可变列表
   */
  public static List<String> videoPathSqlKeywords() {
    return Collections.unmodifiableList(VIDEO_PATH_SQL_KEYWORDS);
  }

  /**
   * 供资料池 SQL 形态筛选的标题关键词。
   *
   * @return 不可变列表
   */
  public static List<String> videoTitleSqlKeywords() {
    return Collections.unmodifiableList(VIDEO_TITLE_SQL_KEYWORDS);
  }

  /**
   * 根据链接识别站点来源展示名（如 B站、YouTube）；未知站点回退为去掉 www 的主机名。
   *
   * @param url 资料链接
   * @return 站点中文/品牌名；无链接时返回「未知来源」
   */
  public static String siteLabel(String url) {
    String host = hostOf(url);
    if (StrUtil.isBlank(host)) {
      return UNKNOWN_SITE_LABEL;
    }
    for (Map.Entry<String, String> entry : SITE_LABELS.entrySet()) {
      String domain = entry.getKey();
      if (host.equals(domain) || host.endsWith("." + domain)) {
        return entry.getValue();
      }
    }
    return host.startsWith("www.") ? host.substring(4) : host;
  }

  /**
   * 是否已识别到具体站点（非空白、非「未知来源」）。
   *
   * @param siteLabel 站点展示名
   * @return 已识别为 true
   */
  public static boolean isKnownSite(String siteLabel) {
    return StrUtil.isNotBlank(siteLabel) && !UNKNOWN_SITE_LABEL.equals(siteLabel);
  }

  /**
   * 标题是否需要译成中文。
   * <p>含日文假名/韩文 → 译；否则汉字占比过低（英文等）→ 译。
   */
  public static boolean needsChineseTranslation(String title) {
    if (title == null || title.isBlank()) {
      return false;
    }
    String text = title.trim();
    int han = 0;
    int letters = 0;
    boolean hasKanaOrHangul = false;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      i += Character.charCount(cp);
      Character.UnicodeScript script = Character.UnicodeScript.of(cp);
      if (script == Character.UnicodeScript.HIRAGANA
          || script == Character.UnicodeScript.KATAKANA
          || script == Character.UnicodeScript.HANGUL) {
        hasKanaOrHangul = true;
      }
      boolean letterLike = Character.isLetter(cp)
          || script == Character.UnicodeScript.HAN
          || script == Character.UnicodeScript.HIRAGANA
          || script == Character.UnicodeScript.KATAKANA
          || script == Character.UnicodeScript.HANGUL;
      if (!letterLike) {
        continue;
      }
      letters++;
      if (script == Character.UnicodeScript.HAN) {
        han++;
      }
    }
    if (hasKanaOrHangul) {
      return true;
    }
    if (letters < 4) {
      return false;
    }
    return (han * 1.0 / letters) < 0.25;
  }

  private static String hostOf(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    try {
      String host = URI.create(url.trim()).getHost();
      return host == null ? "" : host.toLowerCase(Locale.ROOT);
    } catch (Exception ignored) {
      return "";
    }
  }
}
