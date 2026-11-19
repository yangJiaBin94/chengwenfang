package com.chengwenfang.knowledge.facade.spi;

import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import java.util.List;

/**
 * 知识图谱存储 SPI（Neo4j 实现；关闭 Graph 时用空实现）。
 *
 * <p>shortcut: 检索暂直接返回 {@link KnowledgeCitationView}；Community 管线落地后再收窄为内部 hit。
 */
public interface KnowledgeGraphStore {

  /**
   * 配置是否开启图能力（与瞬时连通性无关；NoOp 为 false）。
   *
   * @return 已开启
   */
  default boolean enabled() {
    return false;
  }

  /**
   * 当前是否可读写（开启且连接成功）。
   *
   * @return 可用
   */
  boolean available();

  /**
   * Local：按查询实体邻域取关联 chunk 引用。
   *
   * @param query 查询
   * @param topK  上限
   * @return 引用
   */
  List<KnowledgeCitationView> searchLocal(String query, int topK);

  /**
   * Global：社区摘要相关引用（可为空实现由摘要向量兜底）。
   *
   * @param query 查询
   * @param topK  上限
   * @return 引用
   */
  List<KnowledgeCitationView> searchGlobal(String query, int topK);

  /**
   * 按来源切块增量删除边（纠错后重建前调用）。
   *
   * @param sourceChunkId 切块 ID
   */
  void deleteEdgesBySourceChunkId(String sourceChunkId);

  /**
   * 按文档删除图中与该文档切块相关的边/节点痕迹。
   *
   * @param documentId 文档 ID
   * @param chunkIds   该文档切块 ID 列表
   */
  void deleteByDocumentId(String documentId, List<String> chunkIds);

  /**
   * 为切块写入/刷新概念提及边（低置信默认 disabled）。
   *
   * @param chunkId      切块 ID
   * @param documentTitle 文档标题
   * @param headingPath  标题路径
   * @param snippet      片段摘要
   * @param indexVersion 索引版本
   * @param confidence   置信度
   */
  void upsertChunkMentions(
      String chunkId,
      String documentTitle,
      String headingPath,
      String snippet,
      String indexVersion,
      double confidence);

  /**
   * 按边业务 UID 设置 enabled（纠错旁路同步到图）。
   *
   * @param edgeUid 边 UID
   * @param enabled 是否启用
   */
  void setEdgeEnabled(String edgeUid, boolean enabled);

  /**
   * 删除指定索引版本下的边与相关概念/作品节点（蓝绿切流后 GC 旧版）。
   *
   * @param indexVersion 索引版本
   */
  void deleteByIndexVersion(String indexVersion);
}
