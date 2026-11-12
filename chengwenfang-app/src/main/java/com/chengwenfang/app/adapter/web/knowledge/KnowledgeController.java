package com.chengwenfang.app.adapter.web.knowledge;

import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.ApiResult;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 知识库文档索引与 RAG 对话 API。 */
@Tag(name = "知识库", description = "文档索引、统计与 RAG 对话")
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;

  /**
   * 登记或更新来源文档。
   *
   * @param request 登记请求
   * @return 文档视图
   */
  @Operation(summary = "登记知识库文档")
  @PostMapping("/documents/register")
  public ApiResult<KnowledgeDocumentView> register(@RequestBody RegisterKnowledgeDocumentRequest request) {
    log.info("登记知识库文档 sourceKind={} sourceRefId={}",
        request == null ? null : request.getSourceKind(),
        request == null ? null : request.getSourceRefId());
    return ApiResult.ok(knowledgeFacade.upsertSourceDocument(request));
  }

  /**
   * 单文档重新入队索引。
   *
   * @param id 文档 ID
   * @return 任务视图
   */
  @Operation(summary = "单文档重建索引")
  @PostMapping("/documents/{id}/reindex")
  public ApiResult<JobView> reindexDocument(@PathVariable String id) {
    log.info("单文档重建索引 documentId={}", id);
    return ApiResult.ok(knowledgeFacade.enqueueIndexDocument(id));
  }

  /**
   * 全量或按来源重建索引。
   *
   * @param request 重建请求
   * @return 任务视图
   */
  @Operation(summary = "全量重建知识库索引")
  @PostMapping("/reindex")
  public ApiResult<JobView> reindexAll(@RequestBody(required = false) KnowledgeReindexRequest request) {
    log.info("全量重建知识库索引 sourceKind={}", request == null ? null : request.getSourceKind());
    return ApiResult.ok(knowledgeFacade.reindexAll(request));
  }

  /**
   * 索引统计。
   *
   * @return 统计视图
   */
  @Operation(summary = "知识库索引统计")
  @GetMapping("/stats")
  public ApiResult<KnowledgeIndexStatsView> stats() {
    return ApiResult.ok(knowledgeFacade.stats());
  }

  /**
   * 创建对话会话。
   *
   * @param request 创建请求
   * @return 会话视图
   */
  @Operation(summary = "创建知识库对话会话")
  @PostMapping("/chats")
  public ApiResult<KnowledgeChatSessionView> createChat(@RequestBody(required = false) KnowledgeChatCreateRequest request) {
    return ApiResult.ok(knowledgeFacade.createSession(request));
  }

  /**
   * 列出会话。
   *
   * @return 会话列表
   */
  @Operation(summary = "列出知识库对话会话")
  @GetMapping("/chats")
  public ApiResult<List<KnowledgeChatSessionView>> listChats() {
    return ApiResult.ok(knowledgeFacade.listSessions());
  }

  /**
   * 列出会话消息。
   *
   * @param id 会话 ID
   * @return 消息列表
   */
  @Operation(summary = "列出会话消息")
  @GetMapping("/chats/{id}/messages")
  public ApiResult<List<KnowledgeChatMessageView>> listMessages(@PathVariable String id) {
    return ApiResult.ok(knowledgeFacade.listMessages(id));
  }

  /**
   * 同步问答并以 SSE 推送引用与回答。
   *
   * @param id      会话 ID
   * @param request 提问
   * @return SSE 流
   */
  @Operation(summary = "知识库问答（SSE）")
  @PostMapping(path = "/chats/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter askWithSse(@PathVariable String id, @RequestBody KnowledgeChatMessageRequest request) {
    SseEmitter emitter = new SseEmitter(0L);
    Thread.startVirtualThread(() -> streamAnswer(id, request, emitter));
    return emitter;
  }

  /**
   * 执行问答并通过 SSE 推送 citations、answer、done。
   *
   * @param sessionId 会话 ID
   * @param request     提问
   * @param emitter     SSE 发射器
   */
  private void streamAnswer(String sessionId, KnowledgeChatMessageRequest request, SseEmitter emitter) {
    try {
      KnowledgeChatAnswerView answer = knowledgeFacade.ask(sessionId, request);
      emitter.send(SseEmitter.event()
          .name("citations")
          .data(JSONUtil.toJsonStr(answer.getCitations())));
      emitter.send(SseEmitter.event()
          .name("answer")
          .data(JSONUtil.toJsonStr(answer.getMessage())));
      emitter.send(SseEmitter.event().name("done").data("{}"));
      emitter.complete();
    } catch (Exception exception) {
      log.warn("知识库问答 SSE 失败 sessionId={} err={}", sessionId, exception.getMessage());
      try {
        emitter.completeWithError(exception);
      } catch (Exception ignored) {
        emitter.complete();
      }
    }
  }
}
