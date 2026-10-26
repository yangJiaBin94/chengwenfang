package com.chengwenfang.publishing.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.publishing.facade.spi.OutputRenderer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 产出渲染器注册表：按 formatCode 查找 {@link OutputRenderer}。
 */
@Component
public class OutputRendererRegistry {

  /** formatCode → 渲染器。 */
  private final Map<String, OutputRenderer> byCode;

  /**
   * @param renderers Spring 注入的全部产出渲染器
   */
  public OutputRendererRegistry(List<OutputRenderer> renderers) {
    Map<String, OutputRenderer> map = new LinkedHashMap<>();
    for (OutputRenderer renderer : renderers) {
      map.put(renderer.formatCode(), renderer);
    }
    this.byCode = Map.copyOf(map);
  }

  /**
   * 查找产出渲染器。
   *
   * @param formatCode 格式 code
   * @return 渲染器；未注册为空
   */
  public Optional<OutputRenderer> find(String formatCode) {
    if (StrUtil.isBlank(formatCode)) {
      return Optional.empty();
    }
    return Optional.ofNullable(byCode.get(formatCode.trim()));
  }

  /**
   * 获取产出渲染器；未注册则抛业务异常。
   *
   * @param formatCode 格式 code
   * @return 渲染器
   */
  public OutputRenderer require(String formatCode) {
    return find(formatCode).orElseThrow(() -> new BusinessException(
        ErrorCodes.INVALID_CONTENT_KIND, "未注册的产出渲染器：" + formatCode, 404));
  }
}
