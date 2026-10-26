package com.chengwenfang.app.adapter.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chengwenfang.app.application.generation.custom.CustomAiNodeHandler;
import com.chengwenfang.mode.course.application.generation.CourseArticleGraphExecutor;
import com.chengwenfang.mode.hotnews.application.generation.HotNewsArticleGraphExecutor;
import com.chengwenfang.mode.hotnews.application.generation.HotNewsRadarCollectGraphExecutor;
import com.chengwenfang.skill.domain.runtime.WorkflowNodeHandlerRegistry;
import com.chengwenfang.skill.facade.constant.WorkflowNodeHandlerOrders;
import com.chengwenfang.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import com.chengwenfang.skill.facade.spi.WorkflowNodeHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 节点处理器注册表路由：内置 typeKey 须命中与原 if 分支相同的处理器类。
 */
class WorkflowNodeHandlerRegistryRoutingTest {

  /** 被测注册表（故意打乱注入顺序，验证按 order 排序）。 */
  private WorkflowNodeHandlerRegistry registry;

  /** 初始化四个家族处理器。 */
  @BeforeEach
  void setUp() {
    // 依赖仅用于 supports/order 路由，构造时传空/null 即可
    CustomAiNodeHandler customAi = new CustomAiNodeHandler(null, null, null, null);
    CourseArticleGraphExecutor course = new CourseArticleGraphExecutor(null);
    HotNewsRadarCollectGraphExecutor radar =
        new HotNewsRadarCollectGraphExecutor(List.of(), null);
    HotNewsArticleGraphExecutor hotNews = new HotNewsArticleGraphExecutor(List.of());
    // 注入顺序故意与期望优先级相反
    registry = new WorkflowNodeHandlerRegistry(List.of(hotNews, radar, course, customAi));
  }

  /** 注册表内部应按 order 升序排列。 */
  @Test
  void sortsHandlersByOrder() {
    List<WorkflowNodeHandler> handlers = registry.handlers();
    assertEquals(4, handlers.size());
    assertEquals(WorkflowNodeHandlerOrders.CUSTOM_AI, handlers.get(0).order());
    assertEquals(WorkflowNodeHandlerOrders.COURSE, handlers.get(1).order());
    assertEquals(WorkflowNodeHandlerOrders.RADAR_COLLECT, handlers.get(2).order());
    assertEquals(WorkflowNodeHandlerOrders.HOT_NEWS, handlers.get(3).order());
  }

  /** 自定义 AI 节点路由到 CustomAiNodeHandler。 */
  @Test
  void routesCustomAi() {
    assertHandler(WorkflowStructuralNodeKeys.CUSTOM_AI, CustomAiNodeHandler.class);
  }

  /** 课程内置节点全部路由到 CourseArticleGraphExecutor。 */
  @Test
  void routesCourseBuiltinKeys() {
    for (String typeKey : CourseWorkflowNodeType.builtinTypeKeys()) {
      assertHandler(typeKey, CourseArticleGraphExecutor.class);
    }
  }

  /** 雷达内置节点全部路由到 HotNewsRadarCollectGraphExecutor。 */
  @Test
  void routesRadarBuiltinKeys() {
    for (String typeKey : HotNewsRadarCollectNodeType.builtinTypeKeys()) {
      assertHandler(typeKey, HotNewsRadarCollectGraphExecutor.class);
    }
  }

  /** 热点内置（含已废弃）节点全部路由到 HotNewsArticleGraphExecutor。 */
  @Test
  void routesHotNewsTypeKeys() {
    for (HotNewsWorkflowNodeType nodeType : HotNewsWorkflowNodeType.values()) {
      assertHandler(nodeType.getValue(), HotNewsArticleGraphExecutor.class);
    }
  }

  /** 未注册类型（如 llm_text）不命中，留给通用回退。 */
  @Test
  void leavesGenericKeysUnresolved() {
    assertTrue(registry.findHandler(WorkflowStructuralNodeKeys.LLM_TEXT).isEmpty());
    assertTrue(registry.findHandler(WorkflowStructuralNodeKeys.START).isEmpty());
    assertTrue(registry.findHandler(WorkflowStructuralNodeKeys.END).isEmpty());
  }

  /**
   * 断言 typeKey 命中期望处理器类。
   *
   * @param typeKey       节点类型键
   * @param expectedClass 期望处理器类
   */
  private void assertHandler(String typeKey, Class<?> expectedClass) {
    WorkflowNodeHandler handler = registry.findHandler(typeKey)
        .orElseThrow(() -> new AssertionError("未命中处理器 typeKey=" + typeKey));
    assertEquals(expectedClass, handler.getClass(), () -> "typeKey=" + typeKey);
  }
}
