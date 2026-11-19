package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 知识库 Prompt 版本注册表：scene → 版本化模板；配置 active-version 可回滚。
 */
@Component
@RequiredArgsConstructor
public class KnowledgePromptRegistry {

  /** 默认版本。 */
  public static final String VERSION_V1 = "v1";

  /** 配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 当前活跃版本。
   *
   * @return 版本号
   */
  public String activeVersion() {
    if (properties.getKnowledge() != null
        && properties.getKnowledge().getPrompt() != null
        && StrUtil.isNotBlank(properties.getKnowledge().getPrompt().getActiveVersion())) {
      return properties.getKnowledge().getPrompt().getActiveVersion().trim();
    }
    return VERSION_V1;
  }

  /**
   * 取场景 system 模板（不含动态摘录）。
   *
   * @param scene 场景常量
   * @return 模板
   */
  public String systemTemplate(String scene) {
    String version = activeVersion();
    Map<String, String> byScene = templatesFor(version);
    String template = byScene.get(scene);
    if (StrUtil.isNotBlank(template)) {
      return template;
    }
    return templatesFor(VERSION_V1).getOrDefault(scene, "");
  }

  /**
   * 版本 → scene → 模板。
   *
   * @param version 版本
   * @return 映射
   */
  private static Map<String, String> templatesFor(String version) {
    Map<String, String> map = new LinkedHashMap<>();
    // v1 / 未知版本共用同一套正文；后续可在 v2 分支差异化
    map.put(
        ModelCallScenes.KNOWLEDGE_RETRIEVE_ROUTE,
        "你是检索路由器。只输出 JSON："
            + "{\"mode\":\"hybrid|graph_local|graph_global\"}。"
            + "hybrid=普通事实/定义；graph_local=实体关系/多跳；"
            + "graph_global=整体趋势/社区/库级综括。不要解释。");
    map.put(
        ModelCallScenes.KNOWLEDGE_ANSWER_FAITHFULNESS,
        "你是答案忠实度校验器。只回答 YES 或 NO。");
    map.put(
        ModelCallScenes.KNOWLEDGE_RAG_CHAT,
        "（主问答 system 由 DocumentExcerptIsolation 动态装配，本项仅记录版本） version="
            + version);
    if ("v2".equalsIgnoreCase(version)) {
      map.put(
          ModelCallScenes.KNOWLEDGE_RETRIEVE_ROUTE,
          "你是检索路由器（v2）。只输出 JSON："
              + "{\"mode\":\"hybrid|graph_local|graph_global\"}。"
              + "优先 hybrid；仅明确多跳/关系时用 graph_local；仅库级综括用 graph_global。不要解释。");
    }
    return map;
  }
}
