package com.chengwenfang.content.infrastructure.assembler;

import com.chengwenfang.content.domain.entity.ArticleLayoutEntity;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 文章排版布局视图组装。
 */
@Component
public class ArticleLayoutViewAssembler {

  /**
   * 将布局实体转为视图；options 恒为空对象。
   *
   * @param layout    布局实体
   * @param articleId 文章 ID（表已无此列，由调用方传入）
   * @return 布局视图
   */
  public ArticleLayoutView layoutView(ArticleLayoutEntity layout, String articleId) {
    return ArticleLayoutView.builder()
        .id(layout.getId())
        .articleId(articleId)
        .articleVersionNo(layout.getOutputVersionNo())
        .themeVersionId(
            layout.getLayoutThemeVersionId() == null ? "" : layout.getLayoutThemeVersionId())
        .options(Map.of())
        .updatedAt(layout.getUpdatedAt() == null ? null : layout.getUpdatedAt().toString())
        .build();
  }
}
