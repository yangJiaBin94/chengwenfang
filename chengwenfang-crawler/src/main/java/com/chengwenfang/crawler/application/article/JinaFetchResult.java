package com.chengwenfang.crawler.application.article;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jina Reader 单次拉取结果（区分超时，便于回退原生爬取）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JinaFetchResult {
  /** 正文 Markdown；失败为空。 */
  private String text;
  /** 是否因连接/读取超时失败。 */
  private boolean timedOut;
  /** 错误信息；成功为空。 */
  private String error;

  /**
   * @return 成功且有正文
   */
  public boolean hasText() {
    return StrUtil.isNotBlank(text);
  }

  /**
   * 成功结果。
   *
   * @param text 正文
   * @return 结果
   */
  public static JinaFetchResult success(String text) {
    return JinaFetchResult.builder().text(StrUtil.nullToEmpty(text).trim()).build();
  }

  /**
   * 失败结果。
   *
   * @param timedOut 是否超时
   * @param error    错误
   * @return 结果
   */
  public static JinaFetchResult failure(boolean timedOut, String error) {
    return JinaFetchResult.builder()
        .text("")
        .timedOut(timedOut)
        .error(StrUtil.nullToEmpty(error))
        .build();
  }
}
