package com.workbench.crawler.facade;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 识别不宜作为热点选题/资料的页面：厂商产品首页、资讯栏目页、电商商品与推销页等。
 * <p>热点雷达与资料补充只要「某一具体事件/文章」，不要频道页或商城导购。
 */
public final class MarketingPageFilter {
  private MarketingPageFilter() {}

  private static final Set<String> VENDOR_HOSTS = Set.of(
      "openai.com",
      "chatgpt.com",
      "anthropic.com",
      "claude.ai",
      "deepseek.com",
      "chat.deepseek.com",
      "platform.deepseek.com",
      "api-docs.deepseek.com",
      "gemini.google.com",
      "ai.google",
      "bard.google.com",
      "aistudio.google.com",
      "x.ai",
      "grok.x.ai",
      "kimi.moonshot.cn",
      "moonshot.cn",
      "tongyi.aliyun.com",
      "qianwen.aliyun.com",
      "aliyun.com",
      "cloud.tencent.com",
      "hunyuan.tencent.com",
      "chatglm.cn",
      "zhipuai.cn",
      "bigmodel.cn",
      "yiyan.baidu.com",
      "qianfan.cloud.baidu.com",
      "xinghuo.xfyun.cn",
      "meta.ai",
      "ai.meta.com",
      "llama.meta.com",
      "copilot.microsoft.com",
      "perplexity.ai",
      "mistral.ai",
      "cohere.com",
      "character.ai",
      "midjourney.com",
      "stability.ai");

  /** 电商/导购主机（含子域）；整站商品页不进入热点与资料。 */
  private static final Set<String> COMMERCE_HOSTS = Set.of(
      "jd.com",
      "jd.hk",
      "3.cn",
      "jkcsjd.com",
      "yiyaojd.com",
      "taobao.com",
      "tmall.com",
      "tmall.hk",
      "liangxinyao.com",
      "pinduoduo.com",
      "yangkeduo.com",
      "suning.com",
      "vip.com",
      "dangdang.com",
      "kaola.com",
      "gome.com.cn",
      "amazon.cn",
      "amazon.com",
      "youzan.com",
      "weidian.com",
      "smzdm.com",
      "manmanbuy.com",
      "gwdang.com",
      "yx.gwdang.com",
      "detail.tmall.com",
      "item.taobao.com",
      "item.jd.com",
      "npcitem.jd.hk");

  private static final Set<String> SHALLOW_PATHS = Set.of(
      "/",
      "/chatgpt",
      "/claude",
      "/gemini",
      "/index",
      "/index.html",
      "/home",
      "/app",
      "/chat",
      "/login",
      "/signup",
      "/register",
      "/download",
      "/pricing",
      "/product",
      "/products",
      "/platform",
      "/console",
      "/dashboard",
      "/docs",
      "/api",
      "/zh",
      "/en",
      "/cn",
      "/jp",
      "/ko");

  private static final Pattern LOCALE_ONLY = Pattern.compile(
      "^/(zh|en|cn|jp|ko)(-[a-z]{2})?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PRODUCT_PATH = Pattern.compile(
      "^/(product|products|pricing|platform|console|dashboard|chat|app|download|signup|login|register|docs|api)(/|$)",
      Pattern.CASE_INSENSITIVE);
  /** 厂商站上的文章路径特征（有具体 slug / 日期），允许通过。 */
  private static final Pattern VENDOR_ARTICLE_PATH = Pattern.compile(
      "(news|blog|research|press|announcement|announce|release|updates?|changelog|article|post|index)/.+"
          + "|/20\\d{2}/\\d{1,2}/",
      Pattern.CASE_INSENSITIVE);
  /**
   * 资讯栏目/频道列表页：只有频道名、没有文章 slug。
   * 例：/news、/ai_news、/zh/ai_news、/category/ai
   */
  private static final Pattern NEWS_LISTING_PATH = Pattern.compile(
      "^/(?:[a-z]{2}(?:-[a-z]{2})?/)?"
          + "(?:ai[_-]?)?(?:news|资讯|articles?|posts?|blogs?|feeds?|headlines?|latest|topics?|"
          + "category|categories|tag|tags|channel|channels)(?:/[^/]+)?"
          + "/?$",
      Pattern.CASE_INSENSITIVE);
  /** 路径末段像文章 id/slug（含日期或较长连字符 slug）则不算列表页。 */
  private static final Pattern ARTICLE_SLUG = Pattern.compile(
      "(20\\d{2}|/p/|/post/|/article/|/detail/|/entry/|/[a-z0-9]+-[a-z0-9-]{8,})",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern SEO_PRODUCT_TITLE = Pattern.compile(
      "(一站式|推理和部署|大模型服务|_AI大模型_|官方网站|立即体验|免费试用|控制台|产品页)");
  private static final Pattern BRAND_ONLY_TITLE = Pattern.compile(
      "^(DeepSeek|OpenAI|ChatGPT|Claude|Anthropic|Gemini|通义千问|千问|Kimi|月之暗面|智谱|文心一言|豆包)"
          + "(\\s*[|｜\\-—]\\s*.{0,12})?$",
      Pattern.CASE_INSENSITIVE);
  /** 资讯站/栏目首页标题：描述「每日更新资讯」而非某一具体事件。 */
  private static final Pattern PORTAL_TITLE = Pattern.compile(
      "(每日.{0,12}(资讯|新闻|动态)|最新.{0,12}(资讯|新闻|动态)|行业(资讯|动态|新闻)|"
          + "资讯聚合|新闻中心|中文社区|AI\\s*News\\s*[-—|]|AI资讯\\s*[-—|]"
          + "|快速掌握.{0,16}(进展|动态)|聚焦大模型)",
      Pattern.CASE_INSENSITIVE);
  /**
   * 电商商品/导购标题：自营店铺、规格装数、促销话术等。
   * <p>例：「钙尔奇…礼盒装」「…180片…京东自营」。
   */
  private static final Pattern COMMERCE_PRODUCT_TITLE = Pattern.compile(
      "(京东自营|天猫超市|天猫国际|天猫旗舰|淘宝网|拼多多|唯品会|苏宁易购|"
          + "官方旗舰店|品牌旗舰店|旗舰店|专卖店|专营店|自营旗舰|"
          + "包邮|券后|满减|秒杀价|特价|促销价|限时购|下单立减|买一送|加购|"
          + "礼盒装|送礼装|孝敬长辈|送长辈|"
          + "\\d{2,4}\\s*(片|粒|瓶|盒|袋|罐|支|袋装|瓶装|盒装)|"
          + "(补钙|氨糖|软骨素|钙片|维生素[A-Z0-9]*|保健品|护关节).{0,12}(片|瓶|盒|袋)|"
          + "(片|瓶|盒|袋).{0,8}(补钙|氨糖|软骨素|钙片|维生素|护关节))",
      Pattern.CASE_INSENSITIVE);
  /**
   * 软广/导购话术：引导购买而非报道事件。
   * <p>例：「宝宝补钙别乱选，买对才是关键」。
   */
  private static final Pattern SOFT_SELL_TITLE = Pattern.compile(
      "(别乱选|别乱买|别踩坑|买对才是|选对才是|怎么选才|买哪个好|哪款好|"
          + "推荐购买|闭眼入|种草推荐|入手推荐|必买清单|便宜又好用|性价比之王|"
          + "官方正品|正品保障|限时优惠|立即抢购|点击购买|下单立得|"
          + "赶紧试试|赶紧冲|马上买|同款链接|橱窗有|链接在评论|点击领取|"
          + "关节越来越年轻|越吃越)");
  /**
   * 保健品成分堆砌 + 剂量/CTA 口播带货标题。
   * <p>例：「每天2次，氨糖+钙+软骨素，关节越来越年轻！赶紧试试」。
   */
  private static final Pattern SUPPLEMENT_HARD_SELL_TITLE = Pattern.compile(
      "(每天.?[0-9一二两三四五六七八九十]+.?次).{0,40}(氨糖|软骨素|钙片|补钙|保健品|维骨力)|"
          + "(氨糖|软骨素).{0,8}(\\+|＋|和|与).{0,8}(钙|软骨素|氨糖).{0,40}"
          + "(赶紧|试试|冲|年轻|买|胶囊|礼盒)");
  /**
   * 电商货架式商品标题：季节新款 + 品类/卖点堆叠。
   * <p>例：「足力健老人鞋2026秋季新款轻便软底中老年健步鞋爸爸妈妈休闲运动」。
   */
  private static final Pattern PRODUCT_LISTING_TITLE = Pattern.compile(
      "\\d{4}(春|夏|秋|冬)季新款|"
          + "(新款|爆款|正品).{0,24}(鞋|靴|袜|裤|衣|衫|裙|帽|外套)|"
          + "(老人鞋|健步鞋|运动鞋|休闲鞋|爸爸妈妈).{0,20}(新款|轻便|软底|休闲运动)|"
          + "(轻便软底|爸爸妈妈休闲|中老年健步鞋|中老年运动鞋)|"
          + "(训练营|招生中|名额有限).{0,8}(减脂|减肥|瘦身|养生)|"
          + "(减脂|减肥|瘦身).{0,12}(训练营|招生|报名)");
  /** 电商商品详情路径。 */
  private static final Pattern COMMERCE_PRODUCT_PATH = Pattern.compile(
      "(^|/)(item|product|products|sku|goods|detail|dp|gp)(/|$)|"
          + "/[0-9]{5,}\\.html|/sku/|/product\\.aspx",
      Pattern.CASE_INSENSITIVE);

  /**
   * @param url   页面 URL
   * @param title 标题（可为 null）
   * @return true 表示应跳过，不进入热点素材
   */
  public static boolean shouldSkip(String url, String title) {
    if (looksLikeBrandHomeTitle(title)
        || looksLikeNewsPortalTitle(title)
        || looksLikeCommerceProductTitle(title)) {
      return true;
    }
    if (url == null || url.isBlank()) {
      return false;
    }
    try {
      URI uri = URI.create(url.trim());
      String host = normalizeHost(uri.getHost());
      if (host == null) {
        return false;
      }
      String path = normalizePath(uri.getPath());

      // 电商宿主，或商城域名上的商品详情路径
      if (isCommerceHost(host)
          || (COMMERCE_PRODUCT_PATH.matcher(path).find() && isLikelyCommerceHost(host))) {
        return true;
      }

      // 任意站点的资讯栏目列表页（无文章 slug）
      if (isNewsListingPath(path)) {
        return true;
      }

      if (isVendorHost(host)) {
        if (VENDOR_ARTICLE_PATH.matcher(path).find() && !isNewsListingPath(path)) {
          if (path.matches("(?i)^/(docs|api)(/)?$")) {
            return true;
          }
          return false;
        }
        if (SHALLOW_PATHS.contains(path) || LOCALE_ONLY.matcher(path).matches()) {
          return true;
        }
        if (PRODUCT_PATH.matcher(path).find()) {
          return true;
        }
        if (pathDepth(path) <= 2) {
          return true;
        }
      }
      if ((host.endsWith("aliyun.com") || host.endsWith("tencent.com") || host.endsWith("huaweicloud.com"))
          && PRODUCT_PATH.matcher(path).find()) {
        return true;
      }
      return false;
    } catch (Exception ex) {
      return false;
    }
  }

  public static boolean looksLikeBrandHomeTitle(String title) {
    if (title == null || title.isBlank()) {
      return false;
    }
    String t = title.trim();
    if (SEO_PRODUCT_TITLE.matcher(t).find()) {
      return true;
    }
    return BRAND_ONLY_TITLE.matcher(t).matches();
  }

  /** 资讯栏目/聚合站首页标题（非具体事件）。 */
  public static boolean looksLikeNewsPortalTitle(String title) {
    if (title == null || title.isBlank()) {
      return false;
    }
    return PORTAL_TITLE.matcher(title.trim()).find();
  }

  /**
   * 电商商品、软广导购或货架式推销标题（非资讯事件）。
   *
   * @param title 标题
   * @return true 表示应跳过
   */
  public static boolean looksLikeCommerceProductTitle(String title) {
    if (title == null || title.isBlank()) {
      return false;
    }
    String text = title.trim();
    return COMMERCE_PRODUCT_TITLE.matcher(text).find()
        || SOFT_SELL_TITLE.matcher(text).find()
        || PRODUCT_LISTING_TITLE.matcher(text).find()
        || SUPPLEMENT_HARD_SELL_TITLE.matcher(text).find();
  }

  static boolean isNewsListingPath(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return false;
    }
    // 已有文章特征则放行
    if (ARTICLE_SLUG.matcher(path).find()) {
      return false;
    }
    return NEWS_LISTING_PATH.matcher(path).matches();
  }

  private static boolean isVendorHost(String host) {
    if (VENDOR_HOSTS.contains(host)) {
      return true;
    }
    for (String vendor : VENDOR_HOSTS) {
      if (host.endsWith("." + vendor)) {
        return true;
      }
    }
    return host.endsWith(".aliyun.com")
        || host.endsWith(".openai.com")
        || host.endsWith(".anthropic.com")
        || host.endsWith(".deepseek.com");
  }

  /**
   * 是否电商主站或其子域。
   *
   * @param host 规范化主机名
   * @return true 表示电商站
   */
  static boolean isCommerceHost(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    if (COMMERCE_HOSTS.contains(host)) {
      return true;
    }
    for (String commerce : COMMERCE_HOSTS) {
      if (host.endsWith("." + commerce) || host.equals(commerce)) {
        return true;
      }
    }
    return host.endsWith(".jd.com")
        || host.endsWith(".tmall.com")
        || host.endsWith(".taobao.com")
        || host.endsWith(".yangkeduo.com");
  }

  /**
   * 宽松判断：路径已像商品详情时，主机名含常见商城关键词也拦截。
   *
   * @param host 规范化主机名
   * @return true 表示疑似商城
   */
  private static boolean isLikelyCommerceHost(String host) {
    if (isCommerceHost(host)) {
      return true;
    }
    return host.contains("jd.")
        || host.contains("tmall")
        || host.contains("taobao")
        || host.contains("pinduoduo")
        || host.contains("yangkeduo")
        || host.contains("suning")
        || host.contains("amazon.")
        || host.contains("smzdm")
        || host.contains("manmanbuy");
  }

  private static String normalizeHost(String host) {
    if (host == null || host.isBlank()) {
      return null;
    }
    host = host.toLowerCase(Locale.ROOT);
    if (host.startsWith("www.")) {
      host = host.substring(4);
    }
    return host;
  }

  private static String normalizePath(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "/";
    }
    while (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path.toLowerCase(Locale.ROOT);
  }

  private static int pathDepth(String path) {
    if ("/".equals(path)) {
      return 0;
    }
    int depth = 0;
    for (String part : path.split("/")) {
      if (!part.isBlank()) {
        depth++;
      }
    }
    return depth;
  }
}
