package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查询改写：结合会话摘要消解指代，产出独立可检索问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQueryRewriteService {

  /** 多模态补全。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * 改写用户问题。
   *
   * @param question      原问题
   * @param sessionSummary 会话摘要；可空
   * @return 改写后问题；失败时回退原问题
   */
  public String rewrite(String question, String sessionSummary) {
    if (StrUtil.isBlank(question)) {
      return "";
    }
    if (StrUtil.isBlank(sessionSummary)) {
      return question.trim();
    }
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(
              "你把用户追问改写成一条独立、可检索的中文问题。只输出改写后的问题，不要解释。")
          .prompt("会话摘要：\n" + sessionSummary + "\n\n用户问题：\n" + question.trim())
          .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
          .build());
      String rewritten = result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
      return StrUtil.blankToDefault(rewritten, question.trim());
    } catch (Exception exception) {
      log.warn("知识库查询改写失败，回退原问题：{}", exception.getMessage());
      return question.trim();
    }
  }
}
