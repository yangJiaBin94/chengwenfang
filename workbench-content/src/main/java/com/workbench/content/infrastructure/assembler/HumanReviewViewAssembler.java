package com.workbench.content.infrastructure.assembler;

import com.workbench.content.domain.entity.HumanReviewEntity;
import com.workbench.content.facade.dto.view.HumanReviewView;
import org.springframework.stereotype.Component;

/**
 * 人工审核视图组装。
 */
@Component
public class HumanReviewViewAssembler {

  /**
   * 人工审核实体转视图（API 仍暴露 articleId / documentId）。
   *
   * @param review    审核实体
   * @param versionNo 当前版本号
   * @param articleId 文章 id（由调用方解析）
   * @return 人工审核视图
   */
  public HumanReviewView humanReviewView(
      HumanReviewEntity review, Integer versionNo, String articleId) {
    // comment 空串兜底，时间转 ISO 字符串；documentId = output_id
    String outputVersionId = review.getOutputVersionId();
    return HumanReviewView.builder()
        .id(review.getId())
        .articleId(articleId)
        .documentId(review.getOutputId())
        .documentVersionId(outputVersionId)
        .versionNo(versionNo)
        .status(review.getStatus())
        .comment(review.getComment() == null ? "" : review.getComment())
        .reviewedAt(review.getReviewedAt() == null ? null : review.getReviewedAt().toString())
        .build();
  }
}
