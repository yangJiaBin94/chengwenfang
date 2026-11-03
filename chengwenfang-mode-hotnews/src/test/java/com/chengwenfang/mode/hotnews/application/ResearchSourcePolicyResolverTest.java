package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ResearchSourcePolicyResolver} 静态规则。 */
class ResearchSourcePolicyResolverTest {

  @Test
  void build_angle_queries_without_site() {
    ResearchSourcePolicy policy = ResearchSourcePolicy.builder()
        .queryAngles(List.of("上市", "公告"))
        .build();
    List<String> queries = ResearchSourcePolicyResolver.buildAngleQueries("皇冠新材", policy);
    assertThat(queries).contains("皇冠新材", "皇冠新材 上市", "皇冠新材 公告");
    assertThat(queries).noneMatch(q -> q.contains("site:"));
  }

  @Test
  void build_site_scoped_queries_with_angles() {
    ResearchSourcePolicy policy = ResearchSourcePolicy.builder()
        .siteSearchEnabled(true)
        .preferredDomains(List.of("eastmoney.com", "cninfo.com.cn"))
        .queryAngles(List.of("上市", "公告"))
        .build();
    List<String> queries = ResearchSourcePolicyResolver.buildSiteScopedQueries("皇冠新材", policy);
    assertThat(queries).isNotEmpty();
    assertThat(queries).anyMatch(q -> q.contains("site:eastmoney.com") && q.contains("上市"));
    assertThat(queries).anyMatch(q -> q.contains("site:cninfo.com.cn"));
    assertThat(queries.size()).isLessThanOrEqualTo(12);
  }

  @Test
  void blocked_url_matches_subdomain() {
    ResearchSourcePolicy policy = ResearchSourcePolicy.builder()
        .blockedDomains(List.of("bilibili.com"))
        .build();
    assertThat(ResearchSourcePolicyResolver.isBlockedUrl(
        "https://www.bilibili.com/video/av1", policy)).isTrue();
    assertThat(ResearchSourcePolicyResolver.isBlockedUrl(
        "https://finance.eastmoney.com/a/1.html", policy)).isFalse();
  }

  @Test
  void parse_policy_from_ai_json() {
    ResearchSourcePolicy policy = ResearchSourcePolicyResolver.parsePolicy("""
        {"packKey":"finance","preferredDomains":["eastmoney.com","cninfo.com.cn"],
         "blockedDomains":["bilibili.com"],"queryAngles":["上市","公告"],
         "siteSearchEnabled":true,"openSearchEnabled":true}
        """);
    assertThat(policy).isNotNull();
    assertThat(policy.getPackKey()).isEqualTo("finance");
    assertThat(policy.getPreferredDomains()).contains("eastmoney.com", "cninfo.com.cn");
    assertThat(policy.getQueryAngles()).contains("上市", "公告");
  }
}
