package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.application.agent.KnowledgeAgentToolRegistry;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.constant.KnowledgeToolNames;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeRetrieveMode;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 有界 Agentic 检索：最多 N 轮，支持提前停。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgenticRetrieveLoop {

  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** Hybrid。 */
  private final HybridRetriever hybridRetriever;
  /** 改写。 */
  private final KnowledgeQueryRewriteService rewriteService;
  /** 图。 */
  private final KnowledgeGraphStore graphStore;
  /** 路由。 */
  private final KnowledgeRetrieveRouter retrieveRouter;
  /** 只读工具注册表。 */
  private final KnowledgeAgentToolRegistry toolRegistry;
  /** 选工具用 LLM。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * Agent 检索结果。
   */
  @Data
  @Builder
  public static class AgentRetrieveResult {
    /** 引用。 */
    private List<KnowledgeCitationView> citations;
    /** 轮次。 */
    private int rounds;
    /** 最终查询。 */
    private String query;
    /** 是否提前停。 */
    private boolean earlyStopped;
    /** 只读工具结果（拼进生成上下文）。 */
    private String toolContext;
  }

  /**
   * 是否启用 Agent。
   *
   * @return 启用
   */
  public boolean isEnabled() {
    return properties.getKnowledge() != null
        && properties.getKnowledge().getAgent() != null
        && properties.getKnowledge().getAgent().isEnabled();
  }

  /**
   * 有界迭代检索。
   *
   * @param question     问题
   * @param summary      会话摘要（可含长期记忆）
   * @param sourceKinds  来源
   * @param mediaKinds   介质
   * @param topK         每轮 topK
   * @param toolSteps    工具步骤记录器（可空）
   * @param initialQuery 首轮已改写查询；非空则跳过首次 rewrite
   * @return 结果
   */
  public AgentRetrieveResult run(
      String question,
      String summary,
      List<KnowledgeSourceKind> sourceKinds,
      List<KnowledgeMediaKind> mediaKinds,
      int topK,
      KnowledgeToolStepRecorder toolSteps,
      String initialQuery) {
    int maxRounds = properties.getKnowledge().getAgent().getMaxRounds();
    if (maxRounds <= 0) {
      maxRounds = 3;
    }
    // 弱证据进入 Agent 时先 JSON 选工具；失败则仅 hybrid 检索
    String toolContext = maybeRunSelectedReadonlyTool(question, toolSteps);
    String rewriteSummary = StrUtil.isBlank(toolContext)
        ? summary
        : (StrUtil.isBlank(summary) ? toolContext : summary + "\n" + toolContext);

    String query;
    if (StrUtil.isNotBlank(initialQuery)) {
      // 复用首轮改写，避免轨迹双倍 rewrite
      query = initialQuery.trim();
    } else {
      long rewriteStart = System.currentTimeMillis();
      query = rewriteService.rewrite(question, rewriteSummary);
      if (toolSteps != null) {
        toolSteps.append(
            KnowledgeToolNames.REWRITE,
            "question=" + StrUtil.maxLength(question, 80),
            "query=" + StrUtil.maxLength(query, 120),
            System.currentTimeMillis() - rewriteStart,
            true);
      }
    }
    Map<String, KnowledgeCitationView> merged = new LinkedHashMap<>();
    String previousFingerprint = "";
    boolean earlyStopped = false;
    int round;
    for (round = 1; round <= maxRounds; round++) {
      long routeStart = System.currentTimeMillis();
      KnowledgeRetrieveMode mode = retrieveRouter.route(query);
      if (toolSteps != null) {
        toolSteps.append(
            KnowledgeToolNames.ROUTE,
            "round=" + round,
            mode.getValue(),
            System.currentTimeMillis() - routeStart,
            true);
      }
      long searchStart = System.currentTimeMillis();
      String toolName = switch (mode) {
        case GRAPH_LOCAL -> KnowledgeToolNames.GRAPH_LOCAL;
        case GRAPH_GLOBAL -> KnowledgeToolNames.GRAPH_GLOBAL;
        default -> KnowledgeToolNames.HYBRID_SEARCH;
      };
      List<KnowledgeCitationView> batch = switch (mode) {
        case GRAPH_LOCAL -> hybridRetriever.retainActiveCitations(
            graphStore.searchLocal(query, topK));
        case GRAPH_GLOBAL -> hybridRetriever.retainActiveCitations(
            graphStore.searchGlobal(query, topK));
        default -> hybridRetriever.retrieve(query, topK, sourceKinds, mediaKinds);
      };
      if (CollUtil.isEmpty(batch) && mode != KnowledgeRetrieveMode.HYBRID) {
        batch = hybridRetriever.retrieve(query, topK, sourceKinds, mediaKinds);
        toolName = KnowledgeToolNames.HYBRID_SEARCH;
      }
      if (toolSteps != null) {
        toolSteps.append(
            toolName,
            "topK=" + topK,
            "hits=" + (batch == null ? 0 : batch.size()),
            System.currentTimeMillis() - searchStart,
            true);
      }
      for (KnowledgeCitationView citation : batch) {
        String key = StrUtil.blankToDefault(citation.getChunkId(), citation.getDocumentId() + citation.getSnippet());
        merged.putIfAbsent(key, citation);
      }
      String fingerprint = merged.keySet().stream().sorted().collect(Collectors.joining(","));
      boolean enough = merged.size() >= KnowledgeRagConstants.AGENT_ENOUGH_CITATIONS;
      boolean noGain = fingerprint.equals(previousFingerprint) && round > 1;
      previousFingerprint = fingerprint;
      if (enough || noGain) {
        earlyStopped = true;
        break;
      }
      long rewriteAgainStart = System.currentTimeMillis();
      query = rewriteService.rewrite(question + "（补充检索，避免重复已有摘录）", rewriteSummary);
      if (toolSteps != null) {
        toolSteps.append(
            KnowledgeToolNames.REWRITE,
            "round=" + round,
            "query=" + StrUtil.maxLength(query, 120),
            System.currentTimeMillis() - rewriteAgainStart,
            true);
      }
    }
    return AgentRetrieveResult.builder()
        .citations(new ArrayList<>(merged.values()))
        .rounds(round)
        .query(query)
        .earlyStopped(earlyStopped)
        .toolContext(toolContext)
        .build();
  }

  /**
   * 兼容旧调用：不传 initialQuery、不记录工具步骤。
   *
   * @param question    问题
   * @param summary     摘要
   * @param sourceKinds 来源
   * @param mediaKinds  介质
   * @param topK        topK
   * @return 结果
   */
  public AgentRetrieveResult run(
      String question,
      String summary,
      List<KnowledgeSourceKind> sourceKinds,
      List<KnowledgeMediaKind> mediaKinds,
      int topK) {
    return run(question, summary, sourceKinds, mediaKinds, topK, null, null);
  }

  /**
   * 兼容旧调用：不传 initialQuery。
   *
   * @param question    问题
   * @param summary     摘要
   * @param sourceKinds 来源
   * @param mediaKinds  介质
   * @param topK        topK
   * @param toolSteps   步骤记录
   * @return 结果
   */
  public AgentRetrieveResult run(
      String question,
      String summary,
      List<KnowledgeSourceKind> sourceKinds,
      List<KnowledgeMediaKind> mediaKinds,
      int topK,
      KnowledgeToolStepRecorder toolSteps) {
    return run(question, summary, sourceKinds, mediaKinds, topK, toolSteps, null);
  }

  /**
   * LLM 选择 allowlist 只读工具并执行一次；失败忽略。
   *
   * @param question  问题
   * @param toolSteps 步骤记录
   * @return 可拼进生成上下文的工具结果；未选中则为空
   */
  private String maybeRunSelectedReadonlyTool(String question, KnowledgeToolStepRecorder toolSteps) {
    if (toolRegistry == null || !toolRegistry.has(KnowledgeToolNames.LIST_WORK_METADATA)) {
      return "";
    }
    long started = System.currentTimeMillis();
    String selected = KnowledgeToolNames.HYBRID_SEARCH;
    String args = "";
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(
              "你是工具选择器。只输出 JSON："
                  + "{\"tool\":\"hybrid_search|list_work_metadata\",\"args\":\"\"}。"
                  + "仅当问题明确询问作品/产出列表或标题时选 list_work_metadata；否则 hybrid_search。"
                  + "禁止选择写库或任意 HTTP 工具。")
          .prompt(StrUtil.maxLength(question, 400))
          .scene(ModelCallScenes.KNOWLEDGE_RETRIEVE_ROUTE)
          .build());
      String text = result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        JSONObject json = JSONUtil.parseObj(text.substring(start, end + 1));
        selected = StrUtil.blankToDefault(json.getStr("tool"), KnowledgeToolNames.HYBRID_SEARCH);
        args = StrUtil.nullToEmpty(json.getStr("args"));
      }
    } catch (Exception exception) {
      log.warn("知识库 Agent 工具选择失败，回退 hybrid：{}", exception.getMessage());
      selected = KnowledgeToolNames.HYBRID_SEARCH;
    }
    if (!KnowledgeToolNames.LIST_WORK_METADATA.equals(selected)) {
      if (toolSteps != null) {
        toolSteps.append(
            KnowledgeToolNames.ROUTE,
            "tool_select",
            selected,
            System.currentTimeMillis() - started,
            true);
      }
      return "";
    }
    String toolResult = toolRegistry.execute(KnowledgeToolNames.LIST_WORK_METADATA, args);
    if (toolSteps != null) {
      toolSteps.append(
          KnowledgeToolNames.LIST_WORK_METADATA,
          args,
          StrUtil.maxLength(toolResult, 240),
          System.currentTimeMillis() - started,
          true);
    }
    if (StrUtil.isBlank(toolResult) || toolResult.startsWith("tool_not_allowed")) {
      return "";
    }
    return "作品元数据（只读工具）：\n" + toolResult;
  }
}
