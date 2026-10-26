package com.chengwenfang.content.facade.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 作品标题与跳转所需信息（模式定义解析结果）。
 */
@Value
@Builder
public class ModeWorkNavInfo {
  /** 作品 ID。 */
  String workId;
  /** 展示标题。 */
  String title;
  /** 主题 ID。 */
  String themeId;
  /** 原稿产出 ID。 */
  String draftOutputId;
  /** 公众号产出 ID。 */
  String wechatOutputId;
  /** 热点选题 ID；非热点为空。 */
  String topicId;
}
