package com.chengwenfang.skill.facade.constant;

/**
 * {@link com.chengwenfang.skill.facade.spi.WorkflowNodeHandler} 路由优先级（数值越小越优先）。
 * <p>顺序须与正式图文本分派一致：自定义 → 课程 → 雷达 → 热点 → 通用回退。
 */
public final class WorkflowNodeHandlerOrders {
  private WorkflowNodeHandlerOrders() {}

  /** 自定义 AI 节点。 */
  public static final int CUSTOM_AI = 100;
  /** 课程成文节点。 */
  public static final int COURSE = 200;
  /** 雷达采集节点。 */
  public static final int RADAR_COLLECT = 300;
  /** 热点成文节点。 */
  public static final int HOT_NEWS = 400;
}
