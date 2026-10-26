package com.chengwenfang.skill.facade.constant;

/**
 * 工作流图定义 JSON / Map 字段名（开集结构键，供种子与读写边界共用）。
 */
public final class WorkflowGraphMapKeys {
  private WorkflowGraphMapKeys() {}

  /** 当前规范图 schema 版本字面量。 */
  public static final String SCHEMA_VERSION_V1 = "1";

  /** 图根：schema 版本。 */
  public static final String SCHEMA_VERSION = "schemaVersion";
  /** 图根：展示名。 */
  public static final String NAME = "name";
  /** 图根：描述。 */
  public static final String DESCRIPTION = "description";
  /** 图根：场景码。 */
  public static final String SCENE = "scene";
  /** 图根：模式 id（可空）。 */
  public static final String MODE_ID = "modeId";
  /** 图根：节点列表。 */
  public static final String NODES = "nodes";
  /** 图根：边列表。 */
  public static final String EDGES = "edges";

  /** 节点 / 边：id。 */
  public static final String ID = "id";
  /** 节点：业务 key（与边 source/target 对齐）。 */
  public static final String KEY = "key";
  /** 节点：类型键（规范字段）。 */
  public static final String TYPE_KEY = "typeKey";
  /** 节点：类型键（兼容旧字段）。 */
  public static final String TYPE = "type";
  /** 节点：展示名。 */
  public static final String LABEL = "label";
  /** 节点：是否启用。 */
  public static final String ENABLED = "enabled";
  /** 节点：运行配置。 */
  public static final String CONFIG = "config";
  /** 节点：Skill 绑定列表。 */
  public static final String SKILL_BINDINGS = "skillBindings";

  /** 配置：超时秒数。 */
  public static final String TIMEOUT_SECONDS = "timeoutSeconds";
  /** 配置：最大尝试次数。 */
  public static final String MAX_ATTEMPTS = "maxAttempts";
  /** 配置：失败策略。 */
  public static final String FAILURE_POLICY = "failurePolicy";
  /** 配置：模型类型。 */
  public static final String MODEL_TYPE = "modelType";
  /** 配置：前端进度步名。 */
  public static final String PROGRESS_STEP_KEY = "progressStepKey";

  /** 边：起点节点 key。 */
  public static final String SOURCE = "source";
  /** 边：终点节点 key。 */
  public static final String TARGET = "target";

  /** Skill 绑定：skillId。 */
  public static final String SKILL_ID = "skillId";
  /** Skill 绑定：优先级。 */
  public static final String PRIORITY = "priority";

  /** 线性边 id 前缀。 */
  public static final String EDGE_ID_PREFIX = "e_";

  /** Skill 绑定默认起始优先级。 */
  public static final int SKILL_BINDING_PRIORITY_START = 10;
  /** Skill 绑定优先级步长。 */
  public static final int SKILL_BINDING_PRIORITY_STEP = 10;
}
