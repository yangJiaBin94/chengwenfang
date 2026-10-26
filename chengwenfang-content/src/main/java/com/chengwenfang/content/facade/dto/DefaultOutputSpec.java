package com.chengwenfang.content.facade.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 模式默认产出组合：渠道 × 格式，及可选标题后缀。
 */
@Value
@Builder
public class DefaultOutputSpec {
  /** 渠道编码（如 master / wechat_mp）。 */
  String channelCode;
  /** 格式编码（如 article）。 */
  String formatCode;
  /**
   * 产出标题相对作品标题的后缀；空表示标题与作品标题相同。
   * <p>课程原稿为「 原稿」，公众号为「 公众号」。
   */
  String titleSuffix;
}
