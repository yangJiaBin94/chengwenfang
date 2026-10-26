package com.chengwenfang.app.golden.stub;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.chengwenfang.publishing.facade.constant.WechatMpApiPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 冒烟：全部外部站点桩可返回固定内容。
 */
class ExternalSiteStubsTest {
  private WireMockServer server;

  @BeforeEach
  void start() {
    server = ExternalSiteStubs.startServer();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  void all_external_fixtures_are_reachable() {
    // RSS / SearXNG / GitHub 为动态桩（对齐雷达黄金标题），不以 static fixture 文案断言
    assertGetContains(
        ExternalSiteStubs.PATH_RSS, "Sleep mechanism study: how deep sleep restores memory");
    assertGetContains(
        ExternalSiteStubs.PATH_SEARXNG + "?q=ai&format=json",
        "Melatonin myth: does bedtime supplement really fix insomnia");
    assertGetContains(ExternalSiteStubs.PATH_GITHUB + "?q=ai", "golden-repo");
    assertGetContains(ExternalSiteStubs.PATH_ARTICLE, "Golden Article Title");
    assertGetContains("/html/?q=test", "Golden DDG Hit");
    assertGetContains("/html/?q=test", server.baseUrl() + ExternalSiteStubs.PATH_ARTICLE);

    try (HttpResponse tavily =
        HttpRequest.post(server.baseUrl() + ExternalSiteStubs.PATH_TAVILY)
            .body("{\"query\":\"ai\"}")
            .contentType("application/json")
            .execute()) {
      assertThat(tavily.getStatus()).isEqualTo(200);
      assertThat(tavily.body()).contains("Golden Tavily Hit");
    }

    try (HttpResponse token =
        HttpRequest.post(server.baseUrl() + WechatMpApiPaths.STABLE_TOKEN)
            .body("{\"grant_type\":\"client_credential\"}")
            .contentType("application/json")
            .execute()) {
      assertThat(token.getStatus()).isEqualTo(200);
      assertThat(JSONUtil.parseObj(token.body()).getStr("access_token")).isEqualTo("GOLDEN_TOKEN");
    }

    try (HttpResponse draft =
        HttpRequest.post(server.baseUrl() + WechatMpApiPaths.DRAFT_ADD + "?access_token=x")
            .body("{\"articles\":[]}")
            .contentType("application/json")
            .execute()) {
      assertThat(draft.getStatus()).isEqualTo(200);
      assertThat(JSONUtil.parseObj(draft.body()).getStr("media_id")).contains("DRAFT");
    }
  }

  private void assertGetContains(String pathAndQuery, String needle) {
    try (HttpResponse response = HttpRequest.get(server.baseUrl() + pathAndQuery).execute()) {
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.body()).contains(needle);
    }
  }
}
