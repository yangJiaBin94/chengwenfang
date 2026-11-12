package com.chengwenfang.knowledge.facade;

import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
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
}
