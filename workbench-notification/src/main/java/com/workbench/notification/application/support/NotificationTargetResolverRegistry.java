package com.workbench.notification.application.support;

import cn.hutool.core.util.StrUtil;
import com.workbench.notification.facade.spi.NotificationTargetResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 通知目标解析注册表：首个 {@code supports} 命中的实现负责补齐 metadata。
 */
@Component
public class NotificationTargetResolverRegistry {

  /** 全部解析器。 */
  private final List<NotificationTargetResolver> resolvers;

  /**
   * @param resolvers Spring 注入的全部 {@link NotificationTargetResolver}
   */
  public NotificationTargetResolverRegistry(List<NotificationTargetResolver> resolvers) {
    this.resolvers = List.copyOf(resolvers);
  }

  /**
   * 按任务类型解析通知目标。
   *
   * @param jobType   任务类型
   * @param entityId  实体 ID
   * @param metadata  可变 metadata
   * @return 展示名；无匹配解析器时 empty
   */
  public Optional<String> resolve(String jobType, String entityId, Map<String, Object> metadata) {
    if (StrUtil.isBlank(jobType)) {
      return Optional.empty();
    }
    for (NotificationTargetResolver resolver : resolvers) {
      if (resolver.supports(jobType)) {
        return Optional.ofNullable(resolver.resolve(entityId, metadata));
      }
    }
    return Optional.empty();
  }
}
