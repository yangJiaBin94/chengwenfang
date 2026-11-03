package com.chengwenfang.mode.hotnews.facade.constant;

/**
 * 选题资料补充 AI Agent 预算常量。
 */
public final class HotNewsResearchAgentLimits {
  /** 模型决策最大轮次。 */
  public static final int MAX_ROUNDS = 3;
  /** 每轮最多搜索词条数。 */
  public static final int MAX_QUERIES_PER_ROUND = 4;
  /** 全局最多保留的原始搜索命中数。 */
  public static final int MAX_HITS = 20;
  /** 单条 query 的搜索条数上限。 */
  public static final int SEARCH_LIMIT_PER_QUERY = 5;
  /** 每轮最多 fetch 的 URL 数。 */
  public static final int MAX_FETCH_PER_ROUND = 5;
  /** finish 最多采纳条数。 */
  public static final int MAX_FINISH_ITEMS = 12;

  private HotNewsResearchAgentLimits() {}
}
