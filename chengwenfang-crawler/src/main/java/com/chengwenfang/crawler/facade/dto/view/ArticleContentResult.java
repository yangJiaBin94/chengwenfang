package com.chengwenfang.crawler.facade.dto.view;

import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章正文解析结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleContentResult {
  /** 正文文本。 */
  private String text;
  /** 来源层级。 */
  private ArticleContentSource source;
  /**
   * 可选发布时间（门面一般不直接填；由 enrich 正则+AI 后再回填候选）。
   */
  private LocalDateTime publishedAt;
  /**
   * Trafilatura 元数据日期原始串；仅作 AI 失败后的回退，不优先采用。
   */
  private String metadataDate;
  /**
   * 发布日抽取输入：Jina=markdown；Trafilatura=HTML 去标签文本；摘要=摘要全文。
   */
  private String dateInputText;
  /** 耗时毫秒。 */
  private long costMs;
  /** 失败说明（可空）。 */
  private String errorMessage;
  /** 是否达到可用长度。 */
  private boolean usable;
}
