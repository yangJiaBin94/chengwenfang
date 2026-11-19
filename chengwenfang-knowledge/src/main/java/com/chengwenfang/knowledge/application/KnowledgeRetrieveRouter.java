package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.enums.KnowledgeRetrieveMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 问法路由：规则预筛 + LLM JSON 分类；失败回退 Hybrid。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrieveRouter {

  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 补全。 */
  private final AiVisionFacade aiVisionFacade;
  /** Prompt 版本。 */
  private final KnowledgePromptRegistry promptRegistry;

  /**
   * 根据问法选择模式（不含 Agent；Agent 由证据门后触发）。
   *
   * @param query 查询
   * @return 模式
   */
  public KnowledgeRetrieveMode route(String query) {
    if (StrUtil.isBlank(query)) {
      return KnowledgeRetrieveMode.HYBRID;
    }
    boolean graphEnabled = properties.getKnowledge() != null
        && properties.getKnowledge().getGraph() != null
        && properties.getKnowledge().getGraph().isEnabled();
    if (!graphEnabled) {
      return KnowledgeRetrieveMode.HYBRID;
    }
    // 极短问免 LLM
    if (query.trim().length() <= 4) {
      return KnowledgeRetrieveMode.HYBRID;
    }
    KnowledgeRetrieveMode llmMode = routeByLlm(query.trim());
    if (llmMode != null) {
      return llmMode;
    }
    return routeByKeywords(query.trim());
  }

  /**
   * LLM 短分类；失败返回 null。
   *
   * @param query 查询
   * @return 模式或 null
   */
  private KnowledgeRetrieveMode routeByLlm(String query) {
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(promptRegistry.systemTemplate(ModelCallScenes.KNOWLEDGE_RETRIEVE_ROUTE))
          .prompt(query)
          .scene(ModelCallScenes.KNOWLEDGE_RETRIEVE_ROUTE)
          .build());
      String text = result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
      if (StrUtil.isBlank(text)) {
        return null;
      }
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return null;
      }
      JSONObject json = JSONUtil.parseObj(text.substring(start, end + 1));
      String mode = StrUtil.nullToEmpty(json.getStr("mode")).trim().toLowerCase();
      // GRAPH_GLOBAL：尚无 Community 写入管线，命中时降级 Hybrid
      return switch (mode) {
        case "graph_local" -> KnowledgeRetrieveMode.GRAPH_LOCAL;
        case "graph_global" -> KnowledgeRetrieveMode.HYBRID;
        case "hybrid" -> KnowledgeRetrieveMode.HYBRID;
        default -> null;
      };
    } catch (Exception exception) {
      log.warn("知识库检索路由 LLM 失败，回退规则：{}", exception.getMessage());
      return null;
    }
  }

  /**
   * 关键词回退路由。
   *
   * @param text 查询
   * @return 模式
   */
  private static KnowledgeRetrieveMode routeByKeywords(String text) {
    // 全局/社区类问法暂降 Hybrid，待 Community 管线就绪再开 GRAPH_GLOBAL
    if (containsAny(text, "整体", "全局", "趋势", "演进", "对比", "社区", "全貌", "总结一下库")) {
      return KnowledgeRetrieveMode.HYBRID;
    }
    if (containsAny(text, "关系", "关联", "多跳", "谁提到", "出现在哪些", "相关实体")) {
      return KnowledgeRetrieveMode.GRAPH_LOCAL;
    }
    return KnowledgeRetrieveMode.HYBRID;
  }

  /**
   * 是否包含任一关键词。
   *
   * @param text     文本
   * @param keywords 关键词
   * @return 命中
   */
  private static boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
