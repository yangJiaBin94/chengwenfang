package com.chengwenfang.publishing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 推送公众号草稿结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatMpDraftPushResult {
  /** 微信草稿 media_id。 */
  private String mediaId;
  /** 实际使用的标题。 */
  private String title;
  /** 正文内上传并替换的图片数量。 */
  private int uploadedImageCount;
  /** 推送完成时间。 */
  private LocalDateTime pushedAt;
  /**
   * 是否覆盖了已有草稿（true=update；false=新建 add）。
   */
  private boolean updatedExisting;
}
