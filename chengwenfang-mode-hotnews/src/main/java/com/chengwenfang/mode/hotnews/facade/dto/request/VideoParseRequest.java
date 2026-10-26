package com.chengwenfang.mode.hotnews.facade.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启动资料池视频解析请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoParseRequest {
  /**
   * 要解析的条目 ID；空则解析该选题下全部未成功的视频。
   */
  private List<String> itemIds;
  /**
   * 从该步骤起重跑（含）；空则自动从合适步骤开始。
   */
  private String fromStep;
}
