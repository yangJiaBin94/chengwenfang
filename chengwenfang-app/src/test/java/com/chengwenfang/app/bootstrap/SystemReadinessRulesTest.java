package com.chengwenfang.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.ai.facade.dto.view.ProviderPublicView;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 默认档就绪与热点未加装、内存向量库的判定。
 */
class SystemReadinessRulesTest {

  @Test
  void memoryVectorStoreIsNotReady() {
    SystemReadinessItem item = SystemReadinessRules.qdrant(true, true, "qdrant", 6334);

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.FAIL);
    assertThat(item.isRequired()).isTrue();
    assertThat(item.getDetail()).contains("内存向量库不算就绪");
  }

  @Test
  void liveQdrantPasses() {
    SystemReadinessItem item = SystemReadinessRules.qdrant(false, false, "127.0.0.1", 6334);

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.FAIL);
    assertThat(item.getDetail()).contains("未启用");
  }

  @Test
  void connectedQdrantPasses() {
    SystemReadinessItem item = SystemReadinessRules.qdrant(true, false, "qdrant", 6334);

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.PASS);
    assertThat(item.getDetail()).contains("qdrant:6334");
  }

  @Test
  void missingEmbeddingKeyCannotLookSuccessful() {
    SystemReadinessItem item = SystemReadinessRules.apiKey(
        SystemReadinessKeys.EMBEDDING_KEY,
        "嵌入 Key",
        ProviderPublicView.builder().apiKeyConfigured(false).model("text-embedding-3-small").build(),
        "未配置");

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.FAIL);
    assertThat(item.isRequired()).isTrue();
  }

  @Test
  void configuredEmbeddingKeyPassesWithoutEchoingSecret() {
    SystemReadinessItem item = SystemReadinessRules.apiKey(
        SystemReadinessKeys.EMBEDDING_KEY,
        "嵌入 Key",
        ProviderPublicView.builder()
            .apiKeyConfigured(true)
            .enabled(true)
            .model("text-embedding-3-small")
            .build(),
        "未配置");

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.PASS);
    assertThat(item.getDetail()).doesNotContain("sk-");
  }

  @Test
  void missingCliIsNotInstalledRatherThanFailedDefault() {
    SystemReadinessItem item = SystemReadinessRules.cli(
        SystemReadinessKeys.CLI_ARTICLE_EXTRACT,
        "article-extract",
        false,
        false,
        "/repo/scripts/article-extract/.venv/bin/python",
        "热点正文会缺。");

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.NOT_INSTALLED);
    assertThat(item.isRequired()).isFalse();
    assertThat(item.getSection()).isEqualTo(SystemReadinessSection.HOT_NEWS);
  }

  @Test
  void graphOffIsOptionalNotAFailure() {
    SystemReadinessItem item = SystemReadinessRules.optionalSwitch(
        SystemReadinessKeys.GRAPH,
        "Graph",
        false,
        false,
        "默认关闭",
        "已开启",
        "未连通");

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.OPTIONAL_OFF);
    assertThat(item.isRequired()).isFalse();
  }

  @Test
  void promptPacksFollowPackDomainNotWorkflow() {
    assertThat(SystemReadinessRules.promptPacksReady(12, 12, true)).isTrue();
    assertThat(SystemReadinessRules.promptPacksReady(12, 12, false)).isFalse();
    assertThat(SystemReadinessRules.promptPacksReady(3, 12, true)).isFalse();
  }

  @Test
  void imageKeyCanBeOptionalForDefaultProfile() {
    SystemReadinessItem item = SystemReadinessRules.apiKey(
        SystemReadinessKeys.IMAGE_KEY,
        "图像 Key",
        ProviderPublicView.builder().apiKeyConfigured(false).build(),
        "未配置",
        false);

    assertThat(item.getStatus()).isEqualTo(SystemReadinessStatus.FAIL);
    assertThat(item.isRequired()).isFalse();
  }

  @Test
  void bareInterpreterCommandIsRecognizedForPathFallback() {
    assertThat(SystemReadinessProbe.isBareInterpreterCommand(Path.of("python"))).isTrue();
    assertThat(SystemReadinessProbe.isBareInterpreterCommand(Path.of("python3"))).isTrue();
    assertThat(SystemReadinessProbe.isBareInterpreterCommand(
        Path.of("scripts/article-extract/.venv/bin/python"))).isFalse();
  }
}
