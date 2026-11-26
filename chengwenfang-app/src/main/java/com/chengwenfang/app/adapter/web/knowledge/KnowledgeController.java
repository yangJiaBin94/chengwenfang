package com.chengwenfang.app.adapter.web.knowledge;

import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeAnswerFeedbackRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatRenameRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeDocumentListRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeGraphEdgeCorrectRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeMemoryPatchRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeWebPageIngestRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeAnswerFeedbackView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeReconcileResultView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeMemoryView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeRetrievalTraceView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
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
   * 手动上传文件入库。
   *
   * @param file 上传文件
   * @return 文档视图
   */
  @Operation(summary = "上传知识库资料文件")
  @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<KnowledgeDocumentView> uploadDocument(
      @org.springframework.web.bind.annotation.RequestPart("file") MultipartFile file) throws java.io.IOException {
    String fileName = file == null ? null : file.getOriginalFilename();
    log.info("上传知识库资料 fileName={} size={}", fileName, file == null ? null : file.getSize());
    return ApiResult.ok(knowledgeFacade.uploadManualDocument(
        fileName,
        file == null ? null : file.getContentType(),
        file == null ? null : file.getBytes()));
  }

  /**
   * 网页 URL 入库。
   *
   * @param request URL 请求
   * @return 文档视图
   */
  @Operation(summary = "网页正文入库")
  @PostMapping("/documents/ingest-url")
  public ApiResult<KnowledgeDocumentView> ingestWebPage(@RequestBody KnowledgeWebPageIngestRequest request) {
    log.info("网页正文入库 url={}", request == null ? null : request.getUrl());
    return ApiResult.ok(knowledgeFacade.ingestWebPage(request));
  }

  /**
   * 分页列出知识文档。
   *
   * @param page        页码，从 1 开始
   * @param pageSize    每页条数
   * @param sourceKind  可选来源过滤
   * @param keyword     标题关键字
   * @param mediaKind   可选介质过滤
   * @param createdFrom 入库日起（含）
   * @param createdTo   入库日止（含）
   * @return 分页结果
   */
  @Operation(summary = "分页列出知识库文档")
  @GetMapping("/documents")
  public ApiResult<PageResult<KnowledgeDocumentView>> listDocuments(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String sourceKind,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String mediaKind,
      @RequestParam(required = false) LocalDate createdFrom,
      @RequestParam(required = false) LocalDate createdTo) {
    KnowledgeSourceKind source = null;
    if (sourceKind != null && !sourceKind.isBlank()) {
      try {
        source = KnowledgeSourceKind.from(sourceKind);
      } catch (IllegalArgumentException exception) {
        throw new BusinessException(ErrorCodes.INVALID_STATUS, "无效的来源种类：" + sourceKind, 400);
      }
    }
    KnowledgeMediaKind media = null;
    if (mediaKind != null && !mediaKind.isBlank()) {
      try {
        media = KnowledgeMediaKind.from(mediaKind);
      } catch (IllegalArgumentException exception) {
        throw new BusinessException(ErrorCodes.INVALID_STATUS, "无效的介质种类：" + mediaKind, 400);
      }
    }
    KnowledgeDocumentListRequest request = KnowledgeDocumentListRequest.builder()
        .page(page)
        .pageSize(pageSize)
        .sourceKind(source)
        .keyword(keyword)
        .mediaKind(media)
        .createdFrom(createdFrom)
        .createdTo(createdTo)
        .build();
    return ApiResult.ok(knowledgeFacade.listDocuments(request));
  }

  /**
   * 删除知识文档。
   *
   * @param id 文档 ID
   * @return 空
   */
  @Operation(summary = "删除知识库文档")
  @DeleteMapping("/documents/{id}")
  public ApiResult<Void> deleteDocument(@PathVariable String id) {
    log.info("删除知识库文档 documentId={}", id);
    knowledgeFacade.deleteDocument(id);
    return ApiResult.ok(null);
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
   * 入队 MySQL/向量/图对账。
   *
   * @param dryRun true 只预览，不删库、不入队重建
   * @return 任务视图
   */
  @Operation(summary = "入队知识库三存储对账")
  @PostMapping("/reconcile")
  public ApiResult<JobView> reconcile(
      @RequestParam(defaultValue = "false") boolean dryRun) {
    log.info("入队知识库对账 dryRun={}", dryRun);
    return ApiResult.ok(knowledgeFacade.enqueueReconcile(dryRun));
  }

  /**
   * 同步预览对账（dryRun，不删库、不入队重建）。
   *
   * @return 对账预览结果
   */
  @Operation(summary = "预览知识库三存储对账（同步 dryRun）")
  @PostMapping("/reconcile/preview")
  public ApiResult<KnowledgeReconcileResultView> reconcilePreview() {
    log.info("同步预览知识库对账");
    return ApiResult.ok(knowledgeFacade.runReconcile(true));
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
   * 当前活跃索引版本。
   *
   * @return 版本号
   */
  @Operation(summary = "知识库活跃索引版本")
  @GetMapping("/index-version")
  public ApiResult<String> activeIndexVersion() {
    return ApiResult.ok(knowledgeFacade.activeIndexVersion());
  }

  /**
   * 运维切流到指定索引版本。
   *
   * @param version 目标版本
   * @return 空
   */
  @Operation(summary = "切换知识库活跃索引版本")
  @PostMapping("/index-version/promote")
  public ApiResult<Void> promoteIndexVersion(@RequestParam String version) {
    log.info("切换知识库索引版本 version={}", version);
    knowledgeFacade.promoteIndexVersion(version);
    return ApiResult.ok(null);
  }

  /**
   * 图边纠错与按切块增量删边。
   *
   * @param request 纠错请求
   * @return 空
   */
  @Operation(summary = "知识库图边纠错")
  @PostMapping("/graph/edges/correct")
  public ApiResult<Void> correctGraphEdge(@RequestBody KnowledgeGraphEdgeCorrectRequest request) {
    knowledgeFacade.correctGraphEdge(request);
    return ApiResult.ok(null);
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
   * 列出长期记忆。
   *
   * @return 记忆列表
   */
  @Operation(summary = "列出知识库长期记忆")
  @GetMapping("/memories")
  public ApiResult<List<KnowledgeMemoryView>> listMemories() {
    return ApiResult.ok(knowledgeFacade.listMemories(100));
  }

  /**
   * 删除长期记忆。
   *
   * @param id 记忆 ID
   * @return 空
   */
  @Operation(summary = "删除知识库长期记忆")
  @DeleteMapping("/memories/{id}")
  public ApiResult<Void> deleteMemory(@PathVariable String id) {
    knowledgeFacade.deleteMemory(id);
    return ApiResult.ok(null);
  }

  /**
   * 暂停或恢复长期记忆。
   *
   * @param id      记忆 ID
   * @param request 含 paused
   * @return 空
   */
  @Operation(summary = "暂停或恢复知识库长期记忆")
  @PatchMapping("/memories/{id}")
  public ApiResult<Void> patchMemory(
      @PathVariable String id, @RequestBody KnowledgeMemoryPatchRequest request) {
    boolean paused = request != null && Boolean.TRUE.equals(request.getPaused());
    knowledgeFacade.setMemoryPaused(id, paused);
    return ApiResult.ok(null);
  }

  /**
   * 重命名会话。
   *
   * @param id      会话 ID
   * @param request 重命名请求
   * @return 会话视图
   */
  @Operation(summary = "重命名知识库对话会话")
  @PatchMapping("/chats/{id}")
  public ApiResult<KnowledgeChatSessionView> renameChat(
      @PathVariable String id, @RequestBody KnowledgeChatRenameRequest request) {
    return ApiResult.ok(
        knowledgeFacade.renameSession(id, request == null ? null : request.getTitle()));
  }

  /**
   * 删除会话及其消息。
   *
   * @param id 会话 ID
   * @return 空
   */
  @Operation(summary = "删除知识库对话会话")
  @DeleteMapping("/chats/{id}")
  public ApiResult<Void> deleteChat(@PathVariable String id) {
    knowledgeFacade.deleteSession(id);
    return ApiResult.ok(null);
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
   * 列出会话检索轨迹（含工具步骤）。
   *
   * @param id    会话 ID
   * @param limit 上限
   * @return 轨迹列表
   */
  @Operation(summary = "列出会话检索轨迹")
  @GetMapping("/chats/{id}/traces")
  public ApiResult<List<KnowledgeRetrievalTraceView>> listTraces(
      @PathVariable String id, @RequestParam(defaultValue = "20") int limit) {
    return ApiResult.ok(knowledgeFacade.listRetrievalTraces(id, limit));
  }

  /**
   * 提交助手消息赞踩。
   *
   * @param messageId 消息 ID
   * @param request   请求
   * @return 反馈视图
   */
  @Operation(summary = "提交答案赞踩反馈")
  @PostMapping("/messages/{messageId}/feedback")
  public ApiResult<KnowledgeAnswerFeedbackView> submitFeedback(
      @PathVariable String messageId, @RequestBody KnowledgeAnswerFeedbackRequest request) {
    return ApiResult.ok(knowledgeFacade.submitAnswerFeedback(messageId, request));
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
    // 问答在请求线程执行，保留登录态（虚拟线程无 Sa-Token 会话，读文件会误 401）
    KnowledgeChatAnswerView answer = knowledgeFacade.ask(id, request);
    SseEmitter emitter = new SseEmitter(0L);
    Thread.startVirtualThread(() -> pushAnswer(id, answer, emitter));
    return emitter;
  }

  /**
   * 将已算好的问答结果推送到 SSE。
   *
   * @param sessionId 会话 ID（仅日志）
   * @param answer    问答结果
   * @param emitter   SSE 发射器
   */
  private void pushAnswer(String sessionId, KnowledgeChatAnswerView answer, SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event()
          .name("citations")
          .data(JSONUtil.toJsonStr(answer.getCitations() == null ? List.of() : answer.getCitations())));
      emitter.send(SseEmitter.event()
          .name("meta")
          .data(JSONUtil.toJsonStr(answer)));
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
