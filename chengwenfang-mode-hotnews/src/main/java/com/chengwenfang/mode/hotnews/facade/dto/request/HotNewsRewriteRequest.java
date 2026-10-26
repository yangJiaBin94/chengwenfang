package com.chengwenfang.mode.hotnews.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点文章改写请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsRewriteRequest {
  /** 改写指令；空白时由服务端使用默认文案。 */
  private String instruction;
}
