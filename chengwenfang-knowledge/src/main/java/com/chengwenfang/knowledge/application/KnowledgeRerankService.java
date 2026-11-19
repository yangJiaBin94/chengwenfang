package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 可选 LLM listwise rerank；关闭时原样返回以支持无 rerank 基线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRerankService {

  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 多模态补全。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * 是否启用 rerank。
   *
   * @return true 时走 LLM 精排
   */
  public boolean isEnabled() {
    return properties.getKnowledge() != null
        && properties.getKnowledge().getRerank() != null
        && properties.getKnowledge().getRerank().isEnabled();
  }

  /**
   * 精排引用列表。
   *
   * @param query     查询
   * @param citations 候选
   * @param topK      保留条数
   * @return 精排后列表
   */
  public List<KnowledgeCitationView> rerank(
      String query, List<KnowledgeCitationView> citations, int topK) {
    if (CollUtil.isEmpty(citations) || topK <= 0) {
      return List.of();
    }
    if (!isEnabled() || citations.size() == 1) {
      return citations.size() <= topK ? citations : citations.subList(0, topK);
    }
    try {
      StringBuilder prompt = new StringBuilder();
      prompt.append("问题：").append(query).append("\n候选（按原序编号，从 1 开始）：\n");
      for (int index = 0; index < citations.size(); index++) {
        KnowledgeCitationView citation = citations.get(index);
        prompt.append(index + 1)
            .append(". ")
            .append(StrUtil.blankToDefault(citation.getTitle(), ""))
            .append(" | ")
            .append(StrUtil.blankToDefault(citation.getSnippet(), ""))
            .append("\n");
      }
      prompt.append("请按与问题相关性从高到低输出 JSON 数组，元素为原编号整数，例如 [2,1,3]。只输出 JSON。");
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt("你是检索精排器，只输出合法 JSON 数组。")
          .prompt(prompt.toString())
          .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
          .build());
      String text = result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
      List<Integer> order = parseOrder(text, citations.size());
      if (CollUtil.isEmpty(order)) {
        return citations.size() <= topK ? citations : citations.subList(0, topK);
      }
      Map<Integer, KnowledgeCitationView> byIndex = new LinkedHashMap<>();
      for (int index = 0; index < citations.size(); index++) {
        byIndex.put(index + 1, citations.get(index));
      }
      List<KnowledgeCitationView> ranked = new ArrayList<>();
      for (Integer oneBased : order) {
        KnowledgeCitationView citation = byIndex.remove(oneBased);
        if (citation != null) {
          ranked.add(citation);
        }
        if (ranked.size() >= topK) {
          break;
        }
      }
      for (KnowledgeCitationView leftover : byIndex.values()) {
        if (ranked.size() >= topK) {
          break;
        }
        ranked.add(leftover);
      }
      return ranked;
    } catch (Exception exception) {
      log.warn("知识库 rerank 失败，回退原序：{}", exception.getMessage());
      return citations.size() <= topK ? citations : citations.subList(0, topK);
    }
  }

  /**
   * 解析模型输出的编号数组。
   *
   * @param text       模型文本
   * @param candidateN 候选数
   * @return 1-based 编号列表
   */
  private static List<Integer> parseOrder(String text, int candidateN) {
    if (StrUtil.isBlank(text)) {
      return List.of();
    }
    int start = text.indexOf('[');
    int end = text.lastIndexOf(']');
    if (start < 0 || end <= start) {
      return List.of();
    }
    try {
      JSONArray array = JSONUtil.parseArray(text.substring(start, end + 1));
      List<Integer> order = new ArrayList<>();
      for (Object item : array) {
        int value = Integer.parseInt(String.valueOf(item).trim());
        if (value >= 1 && value <= candidateN && !order.contains(value)) {
          order.add(value);
        }
      }
      return order;
    } catch (Exception ignored) {
      return List.of();
    }
  }
}
