package com.chengwenfang.crawler.application;

import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import com.chengwenfang.crawler.facade.SearchFacade;
import com.chengwenfang.crawler.facade.constant.ChannelFetchDefaults;
import com.chengwenfang.crawler.facade.dto.view.SearchHitView;

/**
 * 基于 DuckDuckGo HTML 版的搜索实现；失败或非 2xx 时返回空列表而非抛错。
 * <p>默认拦截环回/私网主机（SSRF）；测试可通过 {@code chengwenfang.crawler-allow-loopback} 放开。
 */
@Service
public class DuckDuckGoHtmlSearch implements SearchFacade {
  private static final Pattern UDDG = Pattern.compile("[?&]uddg=([^&]+)");

  private final ChengwenfangProperties properties;
  private final HttpClient http;

  public DuckDuckGoHtmlSearch(ChengwenfangProperties properties) {
    this.properties = properties;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  @Override
  public List<SearchHitView> search(String query, int limit) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    int capped = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));
    String base = properties.getSearchBaseUrl() == null || properties.getSearchBaseUrl().isBlank()
        ? "https://html.duckduckgo.com"
        : properties.getSearchBaseUrl().replaceAll("/$", "");
    String url = base + "/html/?q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
    validateUrl(url);

    try {
      // 拉取 HTML 结果页；非 2xx 或异常时返回空列表（搜索为尽力而为）
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "chengwenfang-crawler/0.1")
          .GET()
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        return List.of();
      }
      return parseHits(response.body(), capped);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  /** 从 result__a 锚点解析标题/链接/摘要，并去重、做 SSRF 校验。 */
  private List<SearchHitView> parseHits(String html, int limit) {
    Document doc = Jsoup.parse(html == null ? "" : html);
    Elements anchors = doc.select("a.result__a");
    List<SearchHitView> hits = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (Element anchor : anchors) {
      if (hits.size() >= limit) {
        break;
      }
      String title = anchor.text() == null ? "" : anchor.text().trim();
      String href = resolveResultUrl(anchor.attr("href"));
      if (href == null || href.isBlank() || !seen.add(href)) {
        continue;
      }
      try {
        validateUrl(href);
      } catch (Exception ignored) {
        continue;
      }
      String snippet = "";
      Element parent = anchor.closest(".result");
      if (parent != null) {
        Element snip = parent.selectFirst(".result__snippet");
        if (snip != null) {
          snippet = snip.text();
        }
      }
      hits.add(new SearchHitView(title.isBlank() ? href : title, href, snippet));
    }
    return hits;
  }

  private String resolveResultUrl(String href) {
    if (href == null || href.isBlank()) {
      return null;
    }
    String value = href.trim();
    if (value.startsWith("//")) {
      value = "https:" + value;
    }
    Matcher matcher = UDDG.matcher(value);
    if (matcher.find()) {
      try {
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
      } catch (Exception ignored) {
        // fall through
      }
    }
    if (value.startsWith("http://") || value.startsWith("https://")) {
      return value;
    }
    return null;
  }

  private void validateUrl(String url) {
    URI uri = URI.create(url);
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!ChannelFetchDefaults.SCHEME_HTTP.equals(scheme) && !ChannelFetchDefaults.SCHEME_HTTPS.equals(scheme)) {
      throw new IllegalArgumentException("仅允许 http/https");
    }
    if (properties.isCrawlerAllowLoopback()) {
      return;
    }
    try {
      InetAddress address = InetAddress.getByName(uri.getHost());
      if (address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress()
          || address.isMulticastAddress()) {
        throw new IllegalArgumentException("SSRF 拦截主机：" + uri.getHost());
      }
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("无效主机：" + uri.getHost(), exception);
    }
  }
}
