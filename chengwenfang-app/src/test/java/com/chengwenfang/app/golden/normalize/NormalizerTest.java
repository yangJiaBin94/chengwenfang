package com.chengwenfang.app.golden.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link Normalizer} 单测：UUID 映射稳定、时间与端口替换、产物键排序。
 */
class NormalizerTest {
  @Test
  void sameUuidMapsToSamePlaceholderAcrossText() {
    Normalizer normalizer = new Normalizer();
    String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    String text = "first=" + uuid + " second=" + uuid;
    assertThat(normalizer.normalizeText(text))
        .isEqualTo("first=<ID-1> second=<ID-1>");
  }

  @Test
  void differentUuidsGetIncrementalPlaceholders() {
    Normalizer normalizer = new Normalizer();
    String first = "11111111-1111-1111-1111-111111111111";
    String second = "22222222-2222-2222-2222-222222222222";
    assertThat(normalizer.normalizeText(first + "|" + second))
        .isEqualTo("<ID-1>|<ID-2>");
  }

  @Test
  void hex32IdIsNormalized() {
    Normalizer normalizer = new Normalizer();
    assertThat(normalizer.normalizeText("id=abcdef0123456789abcdef0123456789"))
        .isEqualTo("id=<ID-1>");
  }

  @Test
  void themeShellCodeAndContentShaAreNormalized() {
    Normalizer normalizer = new Normalizer();
    String text =
        "code=g_course_c65a2f4c hash=dfb96df0abd8c21bd7e022a12ca7fd427000d84786fefaee0f1aa44800c06cd7";
    // SHA-256 模式先于主题 code 应用，故 hash 先占 <ID-1>
    assertThat(normalizer.normalizeText(text)).isEqualTo("code=<ID-2> hash=<ID-1>");
  }

  @Test
  void githubUpdatedQualifierAndChannelLabelAreNormalized() {
    Normalizer normalizer = new Normalizer();
    assertThat(
            normalizer.normalizeText(
                "q=sleep updated:>2026-10-18 「GitHub 搜索」收录 1 条 · 累计候选 1 条"))
        .isEqualTo("q=sleep updated:><DATE> 「<CHANNEL>」收录 <N> 条 · 累计候选 <N> 条");
    assertThat(
            normalizer.normalizeText(
                "q=sleep%20OR%20melatonin%20updated:%3E2026-10-19&sort=stars"))
        .isEqualTo("q=sleep%20OR%20melatonin%20updated:><DATE>&sort=stars");
    assertThat(
            normalizer.normalizeText(
                "q=sleep%20OR%20melatonin%20updated%3A%3E2026-10-18&sort=stars"))
        .isEqualTo("q=sleep%20OR%20melatonin%20updated:><DATE>&sort=stars");
  }

  @Test
  void externalJsonlLinesAreSorted() {
    Normalizer normalizer = new Normalizer();
    String jsonl =
        "{\"method\":\"GET\",\"url\":\"/b\"}\n{\"method\":\"GET\",\"url\":\"/a\"}\n";
    assertThat(normalizer.normalizeExternalJsonl(jsonl))
        .isEqualTo("{\"method\":\"GET\",\"url\":\"/a\"}\n{\"method\":\"GET\",\"url\":\"/b\"}\n");
  }

  @Test
  void timestampsAndDurationAndPortAreReplaced() {
    Normalizer normalizer = new Normalizer();
    String text =
        "at=2026-03-21T12:34:56.789Z local=2026-03-21 12:34:56"
            + " {\"durationMs\":123,\"firstByteMs\":45,\"updatedAt\":1792568869909}"
            + " escaped=updatedAt\\\":1792568869910"
            + " url=http://127.0.0.1:54321/path";
    String normalized = normalizer.normalizeText(text);
    assertThat(normalized).contains("at=<TS>");
    assertThat(normalized).contains("local=<TS>");
    assertThat(normalized).contains("\"updatedAt\":<TS>");
    assertThat(normalized).contains("updatedAt\\\":<TS>");
    assertThat(normalized).contains("\"durationMs\":<MS>");
    assertThat(normalized).contains("\"firstByteMs\":<MS>");
    assertThat(normalized).contains("http://127.0.0.1:<PORT>/path");
  }

  @Test
  void hutoolMultipartBoundaryIsReplaced() {
    Normalizer normalizer = new Normalizer();
    assertThat(normalizer.normalizeText("----------------------Hutool_GMRCr8j2hJ0q0gcL\\r\\n"))
        .isEqualTo("----------------------Hutool_<BOUNDARY>\\r\\n");
  }

  @Test
  void runtimePrefixIsReplaced() {
    Normalizer normalizer = new Normalizer().runtimeDir("D:\\tmp\\golden-run");
    assertThat(normalizer.normalizeText("file=D:\\tmp\\golden-run\\out.png"))
        .isEqualTo("file=<RUNTIME>\\out.png");
  }

  @Test
  void outputsJsonSortsObjectKeys() {
    Normalizer normalizer = new Normalizer();
    String json = "{\"z\":1,\"a\":{\"c\":3,\"b\":2}}";
    String normalized = normalizer.normalizeOutputsJson(json);
    assertThat(normalized.replaceAll("\\s+", ""))
        .isEqualTo("{\"a\":{\"b\":2,\"c\":3},\"z\":1}");
  }
}
