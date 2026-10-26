package com.chengwenfang.app.golden.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 提示词相关 {@code static final String} 指纹。
 *
 * <p>比对「值的 SHA-256 集合」：允许 P5 搬迁改名，不允许改提示词正文。
 * 录制：{@code -Dgolden.fingerprint.record=true}。
 */
class PromptConstantFingerprintTest {
  @Test
  void promptConstantValueHashes_matchBaseline() throws Exception {
    Path baseline = GoldenRepoPaths.baselineDir().resolve("prompt-constants.json");
    Map<String, String> actual = PromptConstantScanner.scan(GoldenRepoPaths.repoRoot());
    assertThat(actual).isNotEmpty();

    if (Boolean.parseBoolean(System.getProperty("golden.fingerprint.record", "false"))) {
      Files.createDirectories(baseline.getParent());
      Map<String, String> sorted = new LinkedHashMap<>();
      actual.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
      Files.writeString(baseline, JSONUtil.toJsonPrettyStr(sorted) + "\n", StandardCharsets.UTF_8);
      return;
    }

    assertThat(baseline).as("缺少 prompt-constants.json，请先 -Dgolden.fingerprint.record=true").exists();
    JSONObject expectedJson = JSONUtil.parseObj(Files.readString(baseline, StandardCharsets.UTF_8));
    Map<String, String> expected = new LinkedHashMap<>();
    for (String key : expectedJson.keySet()) {
      expected.put(key, expectedJson.getStr(key));
    }

    Set<String> actualValues = new TreeSet<>(actual.values());
    Set<String> expectedValues = new TreeSet<>(expected.values());
    assertThat(actualValues)
        .as("提示词常量值哈希集合变化；若有意改提示词请 record 后人工确认。"
            + " actualNames=%s expectedNames=%s",
            actual.keySet(), expected.keySet())
        .isEqualTo(expectedValues);
  }
}
