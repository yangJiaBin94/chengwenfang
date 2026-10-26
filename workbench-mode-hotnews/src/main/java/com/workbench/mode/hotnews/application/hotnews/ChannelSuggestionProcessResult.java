package com.workbench.mode.hotnews.application.hotnews;

import com.workbench.mode.hotnews.facade.dto.view.HotNewsSourceChannelSkippedView;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 单候选处理结果。
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
final class ChannelSuggestionProcessResult {
  /** 新建视图；未创建时 null。 */
  private final HotNewsChannelView created;
  /** 跳过视图；未跳过时 null。 */
  private final HotNewsSourceChannelSkippedView skipped;
  /** 本候选占用的去重键。 */
  private final Set<String> acceptedKeys;

  /**
   * 创建成功。
   *
   * @param view 新建视图
   * @param keys 去重键
   * @return 结果
   */
  static ChannelSuggestionProcessResult created(HotNewsChannelView view, Set<String> keys) {
    return new ChannelSuggestionProcessResult(view, null, keys == null ? Set.of() : keys);
  }

  /**
   * 跳过。
   *
   * @param name    名称
   * @param type    类型
   * @param baseUrl 地址
   * @param reason  原因
   * @return 结果
   */
  static ChannelSuggestionProcessResult skipped(String name, String type, String baseUrl, String reason) {
    return new ChannelSuggestionProcessResult(
        null,
        HotNewsSourceChannelSkippedView.builder()
            .name(name)
            .type(type)
            .baseUrl(baseUrl)
            .reason(reason)
            .build(),
        Set.of());
  }
}
