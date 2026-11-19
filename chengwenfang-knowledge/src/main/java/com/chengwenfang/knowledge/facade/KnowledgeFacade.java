package com.chengwenfang.knowledge.facade;

import com.chengwenfang.common.PageResult;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeAnswerFeedbackRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeDocumentListRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeGraphEdgeCorrectRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeWebPageIngestRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeAnswerFeedbackView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeMemoryView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeReconcileResultView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeRetrievalTraceView;
import java.util.List;

/** 知识库 RAG 门面：文档索引、统计与对话。 */
public interface KnowledgeFacade {

  /**
   * 登记或按来源更新知识文档行。
   *
   * @param request 登记请求
   * @return 文档视图
   */
  KnowledgeDocumentView upsertSourceDocument(RegisterKnowledgeDocumentRequest request);

  /**
   * 手动上传文件并登记为知识文档、入队索引。
   *
   * @param fileName    原始文件名
   * @param contentType MIME
   * @param bytes       文件字节
   * @return 文档视图
   */
  KnowledgeDocumentView uploadManualDocument(String fileName, String contentType, byte[] bytes);

  /**
   * 拉取网页正文并登记入库。
   *
   * @param request URL 请求
   * @return 文档视图
   */
  KnowledgeDocumentView ingestWebPage(KnowledgeWebPageIngestRequest request);

  /**
   * 分页列出知识文档（入库时间倒序；可按来源/名称/介质/日期过滤）。
   *
   * @param request 分页与筛选
   * @return 分页结果
   */
  PageResult<KnowledgeDocumentView> listDocuments(KnowledgeDocumentListRequest request);

  /**
   * 删除知识文档及其切块与向量（并尝试删除关联文件）。
   *
   * @param documentId 文档 ID
   */
  void deleteDocument(String documentId);

  /**
   * 入队单文档索引任务。
   *
   * @param documentId 文档 ID
   * @return 任务视图
   */
  JobView enqueueIndexDocument(String documentId);

  /**
   * 入队全量或按来源重建索引。
   *
   * @param request 重建请求
   * @return 任务视图
   */
  JobView reindexAll(KnowledgeReindexRequest request);

  /**
   * 索引统计。
   *
   * @return 统计视图
   */
  KnowledgeIndexStatsView stats();

  /**
   * 创建对话会话。
   *
   * @param request 创建请求
   * @return 会话视图
   */
  KnowledgeChatSessionView createSession(KnowledgeChatCreateRequest request);

  /**
   * 重命名对话会话。
   *
   * @param sessionId 会话 ID
   * @param title     新标题
   * @return 会话视图
   */
  KnowledgeChatSessionView renameSession(String sessionId, String title);

  /**
   * 删除对话会话及其消息。
   *
   * @param sessionId 会话 ID
   */
  void deleteSession(String sessionId);

  /**
   * 列出全部会话（按更新时间倒序由实现约定）。
   *
   * @return 会话列表
   */
  List<KnowledgeChatSessionView> listSessions();

  /**
   * 列出会话内消息。
   *
   * @param sessionId 会话 ID
   * @return 消息列表
   */
  List<KnowledgeChatMessageView> listMessages(String sessionId);

  /**
   * 同步问答（Controller 可再包 SSE）。
   *
   * @param sessionId 会话 ID
   * @param request   提问
   * @return 回答视图
   */
  KnowledgeChatAnswerView ask(String sessionId, KnowledgeChatMessageRequest request);

  /**
   * 立即执行单文档索引（Job 流水线调用）。
   *
   * @param documentId 文档 ID
   */
  void indexDocumentNow(String documentId);

  /**
   * 同步执行全量或按来源重建索引（Job 流水线调用）。
   *
   * @param request 重建请求
   */
  void runReindexAll(KnowledgeReindexRequest request);

  /**
   * 图边纠错：登记覆盖并可选按切块增量删边。
   *
   * @param request 纠错请求
   */
  void correctGraphEdge(KnowledgeGraphEdgeCorrectRequest request);

  /**
   * 当前活跃索引版本。
   *
   * @return 版本号
   */
  String activeIndexVersion();

  /**
   * 运维切流：将读路径活跃版本设为指定值。
   *
   * @param version 目标版本
   */
  void promoteIndexVersion(String version);

  /**
   * 列出长期记忆。
   *
   * @param limit 上限
   * @return 记忆列表
   */
  List<KnowledgeMemoryView> listMemories(int limit);

  /**
   * 删除长期记忆。
   *
   * @param memoryId 记忆 ID
   */
  void deleteMemory(String memoryId);

  /**
   * 暂停或恢复长期记忆召回。
   *
   * @param memoryId 记忆 ID
   * @param paused   是否暂停
   */
  void setMemoryPaused(String memoryId, boolean paused);

  /**
   * 列出会话检索轨迹（调试）。
   *
   * @param sessionId 会话 ID
   * @param limit     上限
   * @return 轨迹列表
   */
  List<KnowledgeRetrievalTraceView> listRetrievalTraces(String sessionId, int limit);

  /**
   * 入队三存储对账任务（默认落库执行）。
   *
   * @return 任务视图
   */
  default JobView enqueueReconcile() {
    return enqueueReconcile(false);
  }

  /**
   * 入队三存储对账任务。
   *
   * @param dryRun true 只预览（不删库、不入队重建）
   * @return 任务视图
   */
  JobView enqueueReconcile(boolean dryRun);

  /**
   * 同步执行对账（Job Handler 调用）。
   *
   * @return 对账结果
   */
  KnowledgeReconcileResultView runReconcile();

  /**
   * 同步执行对账。
   *
   * @param dryRun true 只预览
   * @return 对账结果
   */
  KnowledgeReconcileResultView runReconcile(boolean dryRun);

  /**
   * 提交答案赞踩反馈。
   *
   * @param messageId 助手消息 ID
   * @param request   请求
   * @return 反馈视图
   */
  KnowledgeAnswerFeedbackView submitAnswerFeedback(
      String messageId, KnowledgeAnswerFeedbackRequest request);
}
