package com.workbench.content.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.content.facade.constant.ContentModeIds;
import com.workbench.content.facade.spi.ModeDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 模式定义注册表：按 modeCode 查找 {@link ModeDefinition}。
 */
@Component
public class ModeDefinitionRegistry {

  /** modeCode → 定义。 */
  private final Map<String, ModeDefinition> byCode;

  /**
   * @param definitions Spring 注入的全部模式定义
   */
  public ModeDefinitionRegistry(List<ModeDefinition> definitions) {
    Map<String, ModeDefinition> map = new LinkedHashMap<>();
    for (ModeDefinition definition : definitions) {
      String code = ContentModeIds.resolveCode(definition.modeCode());
      map.put(code, definition);
    }
    this.byCode = Map.copyOf(map);
  }

  /**
   * 查找模式定义。
   *
   * @param modeCode 模式 code 或历史 id
   * @return 定义；未注册为空
   */
  public Optional<ModeDefinition> find(String modeCode) {
    if (StrUtil.isBlank(modeCode)) {
      return Optional.empty();
    }
    return Optional.ofNullable(byCode.get(ContentModeIds.resolveCode(modeCode)));
  }

  /**
   * 获取模式定义；未注册则抛业务异常。
   *
   * @param modeCode 模式 code 或历史 id
   * @return 定义
   */
  public ModeDefinition require(String modeCode) {
    return find(modeCode).orElseThrow(() -> new BusinessException(
        ErrorCodes.MODE_NOT_FOUND, "未注册的内容模式：" + modeCode, 404));
  }
}
