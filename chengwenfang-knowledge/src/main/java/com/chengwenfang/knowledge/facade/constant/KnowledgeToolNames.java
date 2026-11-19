package com.chengwenfang.knowledge.facade.constant;

/**
 * 知识库检索可观测步骤名与 Agent allowlist 工具名。
 *
 * <p>其中 {@link #HYBRID_SEARCH}/{@link #GRAPH_LOCAL}/{@link #GRAPH_GLOBAL}/{@link #REWRITE}/
 * {@link #ROUTE} 等主要为 <b>观测 step 名</b>（写入 toolSteps），不等于
 * {@code KnowledgeAgentTool} 注册表中的可调度工具；当前 Agent allowlist 仅含
 * {@link #LIST_WORK_METADATA}。
 */
public final class KnowledgeToolNames {

  /** 混合检索（观测 step / 设计工具名）。 */
  public static final String HYBRID_SEARCH = "hybrid_search";
  /** 图局部检索（观测 step）。 */
  public static final String GRAPH_LOCAL = "graph_local";
  /** 图全局检索（观测 step；路由暂不产出该模式）。 */
  public static final String GRAPH_GLOBAL = "graph_global";
  /** 查询改写（观测 step）。 */
  public static final String REWRITE = "rewrite";
  /** 检索路由（观测 step）。 */
  public static final String ROUTE = "route";
  /** 忠实度校验（观测 step）。 */
  public static final String FAITHFULNESS = "faithfulness";
  /** 只读作品元数据（Agent allowlist 工具）。 */
  public static final String LIST_WORK_METADATA = "list_work_metadata";
  /** Context Budget 截断观测。 */
  public static final String CONTEXT_BUDGET = "context_budget";

  private KnowledgeToolNames() {}
}
