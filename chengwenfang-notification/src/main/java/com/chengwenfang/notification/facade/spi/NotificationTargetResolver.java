package com.chengwenfang.notification.facade.spi;

import java.util.Map;

/**
 * 通知目标解析扩展点：按任务类型补齐结构化 metadata，并返回标题展示名。
 */
public interface NotificationTargetResolver {

  /**
   * 是否处理该任务类型。
   *
   * @param jobType 任务类型
   * @return 支持则为 true
   */
  boolean supports(String jobType);

  /**
   * 按实体补齐跳转用 metadata，并返回可用于标题的展示名。
   *
   * @param entityId 业务实体 ID
   * @param metadata 可变 metadata（已含 jobType / output 等）
   * @return 展示名；无则空串
   */
  String resolve(String entityId, Map<String, Object> metadata);
}
