package com.chengwenfang.crawler.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.crawler.application.article.HostConcurrencyGate;
import com.chengwenfang.crawler.application.article.JinaArticleContentClient;
import com.chengwenfang.crawler.application.article.JinaFetchResult;
import com.chengwenfang.crawler.application.article.LightpandaArticleExtractor;
import com.chengwenfang.crawler.application.article.RawHtmlFetchClient;
import com.chengwenfang.crawler.application.article.TrafilaturaArticleExtractor;
import com.chengwenfang.crawler.application.article.TrafilaturaCliResult;
import com.chengwenfang.crawler.application.article.UrlReachabilityProbe;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.HtmlDateTextSupport;
import com.chengwenfang.crawler.facade.constant.ArticleContentDefaults;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.dto.view.UrlReachabilityView;
import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文章正文四级瀑布：Jina → Trafilatura → Lightpanda（可选）→ 搜索摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultArticleContentFacade implements ArticleContentFacade {
  /** 成文坊配置。 */
  private final ChengwenfangProperties properties;
  /** Jina 客户端。 */
  private final JinaArticleContentClient jinaClient;
  /** Trafilatura 客户端。 */
  private final TrafilaturaArticleExtractor trafilaturaClient;
  /** Lightpanda 浏览器渲染客户端。 */
  private final LightpandaArticleExtractor lightpandaClient;
  /** URL 直连探测。 */
  private final UrlReachabilityProbe urlReachabilityProbe;
  /** 源站原始 HTML 拉取（日期 Pass2）。 */
  private final RawHtmlFetchClient rawHtmlFetch;
  /** 主机并发闸门（启动后初始化）。 */
  private volatile HostConcurrencyGate hostGate;

  /**
   * 初始化主机闸门。
   */
  @PostConstruct
  public void initGate() {
    this.hostGate = newHostGate();
  }

  @Override
  public UrlReachabilityView probeUrlReachability(String url) {
    UrlReachabilityProbe.ProbeResult probe = urlReachabilityProbe.probe(url);
    return UrlReachabilityView.builder()
        .reachable(probe.reachable())
        .httpStatus(probe.status())
        .reason(probe.reason())
        .build();
  }

  @Override
  public String fetchRawHtmlDateInputText(String url) {
    return rawHtmlFetch.fetchDateInputText(url);
  }

  @Override
  public ArticleContentResult resolve(ArticleContentRequest request) {
    long startedAt = System.currentTimeMillis();
    String url = request == null ? null : request.getUrl();
    String title = request == null ? null : request.getTitle();
    String summary = request == null ? null : request.getFallbackSummary();
    try {
      if (StrUtil.isBlank(url)) {
        return finishWithSummary(summary, startedAt, "URL 为空", title, url);
      }
      if (shouldSkipHost(url)) {
        return finishWithSummary(summary, startedAt, "跳过强防护域名", title, url);
      }
      HostConcurrencyGate gate = hostGate == null ? newHostGate() : hostGate;
      return gate.run(url, () -> resolveInsideGate(url, summary, title, startedAt));
    } catch (Exception exception) {
      log.warn("文章正文解析异常 title={} url={} err={}", title, url, exception.getMessage());
      return finishWithSummary(summary, startedAt, exception.getMessage(), title, url);
    }
  }

  /**
   * @return 按配置创建的主机闸门
   */
  private HostConcurrencyGate newHostGate() {
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    int permits = settings == null
        ? ArticleContentDefaults.DEFAULT_PER_HOST_CONCURRENCY
        : settings.getPerHostConcurrency();
    return new HostConcurrencyGate(permits);
  }

  /**
   * 闸门内执行瀑布。
   *
   * @param url       链接
   * @param summary   摘要
   * @param title     标题
   * @param startedAt 开始时间
   * @return 结果
   */
  private ArticleContentResult resolveInsideGate(
      String url, String summary, String title, long startedAt) {
    // L1 Jina：dateInputText = markdown（发布日由 enrich 再抽；抽空可再下 HTML）
    JinaFetchResult jinaFetch = jinaClient.fetch(url);
    ArticleContentResult jinaResult = usableResult(
        jinaFetch.getText(),
        ArticleContentSource.JINA,
        jinaFetch.getText(),
        null,
        startedAt,
        jinaFetch.getError());
    if (jinaResult.isUsable()) {
      log.info(
          "文章正文就绪 source={} costMs={} title={} url={}",
          ArticleContentSource.JINA.getValue(),
          jinaResult.getCostMs(),
          title,
          url);
      return jinaResult;
    }
    // Jina 超时/失败：改用本机 Trafilatura 原生爬取源站
    if (jinaFetch.isTimedOut()) {
      log.info(
          "Jina 超时，回退原生爬取(Trafilatura) title={} url={} err={}",
          title,
          url,
          jinaFetch.getError());
    } else if (StrUtil.isNotBlank(jinaFetch.getError()) || !jinaFetch.hasText()) {
      log.info(
          "Jina 未拿到正文，回退原生爬取(Trafilatura) title={} url={} err={}",
          title,
          url,
          StrUtil.blankToDefault(jinaFetch.getError(), "empty"));
    }
    // L2 Trafilatura：正文 + 去标签日期输入；metadata 仅作回退串，不直接写 publishedAt
    TrafilaturaCliResult cli = trafilaturaClient.extract(url);
    String trafText = cli == null ? "" : StrUtil.nullToEmpty(cli.getText());
    String dateInput = cli == null ? "" : StrUtil.nullToEmpty(cli.getDateInputText());
    if (StrUtil.isBlank(dateInput) && StrUtil.isNotBlank(trafText)) {
      dateInput = HtmlDateTextSupport.clipHead(trafText, HtmlDateTextSupport.DATE_INPUT_MAX_CHARS);
    }
    String metadataDate = cli == null ? null : StrUtil.trim(StrUtil.nullToEmpty(cli.getDate()));
    if (StrUtil.isBlank(metadataDate)) {
      metadataDate = null;
    }
    ArticleContentResult trafResult = usableResult(
        trafText,
        ArticleContentSource.TRAFILATURA,
        dateInput,
        metadataDate,
        startedAt,
        cli == null ? null : cli.getError());
    if (trafResult.isUsable()) {
      log.info(
          "文章正文就绪 source={} costMs={} title={} url={}",
          ArticleContentSource.TRAFILATURA.getValue(),
          trafResult.getCostMs(),
          title,
          url);
      return trafResult;
    }
    // L3 Lightpanda：仅配置开启且 Trafilatura 失败后补 JS 渲染；不绕过 skip-hosts
    String browserDateInput = "";
    String browserMeta = metadataDate;
    String browserError = null;
    ChengwenfangProperties.ArticleContentSettings contentSettings = properties.getArticleContent();
    if (contentSettings != null && contentSettings.isBrowserEnabled()) {
      TrafilaturaCliResult browserCli = lightpandaClient.extract(url);
      String browserText = browserCli == null ? "" : StrUtil.nullToEmpty(browserCli.getText());
      browserDateInput =
          browserCli == null ? "" : StrUtil.nullToEmpty(browserCli.getDateInputText());
      if (StrUtil.isBlank(browserDateInput) && StrUtil.isNotBlank(browserText)) {
        browserDateInput =
            HtmlDateTextSupport.clipHead(browserText, HtmlDateTextSupport.DATE_INPUT_MAX_CHARS);
      }
      browserMeta = browserCli == null ? null : StrUtil.trim(StrUtil.nullToEmpty(browserCli.getDate()));
      if (StrUtil.isBlank(browserMeta)) {
        browserMeta = metadataDate;
      }
      browserError = browserCli == null ? null : browserCli.getError();
      ArticleContentResult browserResult = usableResult(
          browserText,
          ArticleContentSource.LIGHTPANDA,
          StrUtil.blankToDefault(browserDateInput, dateInput),
          browserMeta,
          startedAt,
          browserError);
      if (browserResult.isUsable()) {
        log.info(
            "文章正文就绪 source={} costMs={} title={} url={}",
            ArticleContentSource.LIGHTPANDA.getValue(),
            browserResult.getCostMs(),
            title,
            url);
        return browserResult;
      }
      if (StrUtil.isNotBlank(browserError)) {
        log.info("Lightpanda 未拿到正文 title={} url={} err={}", title, url, browserError);
      }
    }
    // L4 摘要：正文用搜索摘要；日期输入优先用已下到的去标签 HTML
    String summaryDateInput =
        StrUtil.blankToDefault(browserDateInput, StrUtil.blankToDefault(dateInput, summary));
    String failReason = StrUtil.firstNonBlank(
        browserError,
        cli == null ? null : cli.getError(),
        "Jina/Trafilatura 均未拿到足够正文");
    return finishWithSummary(
        summary, summaryDateInput, browserMeta, startedAt, failReason, title, url);
  }

  /**
   * 用摘要收口。
   *
   * @param summary   摘要
   * @param startedAt 开始
   * @param reason    原因
   * @param title     标题
   * @param url       链接
   * @return 结果
   */
  private ArticleContentResult finishWithSummary(
      String summary, long startedAt, String reason, String title, String url) {
    return finishWithSummary(summary, summary, null, startedAt, reason, title, url);
  }

  /**
   * 用摘要收口，并可带上已有的日期输入/元数据日期。
   *
   * @param summary        摘要正文
   * @param dateInputText  发布日抽取输入
   * @param metadataDate   元数据日期串
   * @param startedAt      开始
   * @param reason         原因
   * @param title          标题
   * @param url            链接
   * @return 结果
   */
  private ArticleContentResult finishWithSummary(
      String summary,
      String dateInputText,
      String metadataDate,
      long startedAt,
      String reason,
      String title,
      String url) {
    ArticleContentResult result = usableResult(
        summary,
        ArticleContentSource.SUMMARY,
        dateInputText,
        metadataDate,
        startedAt,
        reason);
    if (result.isUsable()) {
      log.info(
          "文章正文就绪 source={} costMs={} title={} url={}",
          ArticleContentSource.SUMMARY.getValue(),
          result.getCostMs(),
          title,
          url);
      return result;
    }
    long costMs = System.currentTimeMillis() - startedAt;
    log.info(
        "文章正文不可用 source={} costMs={} reason={} title={} url={}",
        ArticleContentSource.NONE.getValue(),
        costMs,
        reason,
        title,
        url);
    return ArticleContentResult.builder()
        .text("")
        .source(ArticleContentSource.NONE)
        .costMs(costMs)
        .errorMessage(reason)
        .usable(false)
        .build();
  }

  /**
   * 截断并判定是否可用。
   *
   * @param raw            正文
   * @param source         来源
   * @param dateInputText  发布日抽取输入
   * @param metadataDate   Trafilatura 元数据日期串
   * @param startedAt      开始
   * @param error          错误
   * @return 结果
   */
  private static ArticleContentResult usableResult(
      String raw,
      ArticleContentSource source,
      String dateInputText,
      String metadataDate,
      long startedAt,
      String error) {
    String text = StrUtil.nullToEmpty(raw).trim();
    if (text.length() > ArticleContentDefaults.MAX_TEXT_CHARS) {
      text = text.substring(0, ArticleContentDefaults.MAX_TEXT_CHARS);
    }
    boolean usable = text.length() >= ArticleContentDefaults.MIN_USABLE_CHARS;
    String dateInput = HtmlDateTextSupport.clipHead(
        StrUtil.blankToDefault(dateInputText, usable ? text : ""),
        HtmlDateTextSupport.DATE_INPUT_MAX_CHARS);
    return ArticleContentResult.builder()
        .text(usable ? text : "")
        .source(usable ? source : ArticleContentSource.NONE)
        .publishedAt(null)
        .metadataDate(usable ? metadataDate : null)
        .dateInputText(usable ? dateInput : "")
        .costMs(System.currentTimeMillis() - startedAt)
        .errorMessage(usable ? null : error)
        .usable(usable)
        .build();
  }

  /**
   * 是否命中跳过域名（仅读配置 {@code skip-hosts}）。
   *
   * @param url 链接
   * @return true 表示跳过远端抓取
   */
  private boolean shouldSkipHost(String url) {
    if (StrUtil.isBlank(url)) {
      return false;
    }
    ChengwenfangProperties.ArticleContentSettings settings = properties.getArticleContent();
    List<String> skipHosts = settings == null ? null : settings.getSkipHosts();
    if (CollUtil.isEmpty(skipHosts)) {
      return false;
    }
    String host = HostConcurrencyGate.hostKey(url);
    for (String skip : skipHosts) {
      if (StrUtil.isBlank(skip)) {
        continue;
      }
      String needle = skip.trim().toLowerCase();
      if (host.equals(needle) || host.endsWith("." + needle)) {
        return true;
      }
    }
    return false;
  }
}
