package com.chengwenfang.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.content.facade.dto.view.ArticleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentFacadeImplTest {
  @Mock CourseShellService shell;
  @Mock LearningContentSyncService sync;
  @Mock ArticleVersionService articles;
  @Mock GenerationSettingsAndJobService generation;
  @Mock GeneratedImageService images;
  @Mock HumanReviewService reviews;
  @Mock ArticleLayoutService layouts;
  @Mock ThemeService themes;
  @Mock WorkContentStoreImpl workContents;

  ContentFacadeImpl facade;

  @BeforeEach
  void setUp() {
    facade = new ContentFacadeImpl(
        shell, sync, articles, generation, images, reviews, layouts, themes, workContents);
  }

  @Test
  void getArticle_delegatesToArticleVersionService() {
    ArticleView expected = ArticleView.builder().id("a1").title("t").build();
    when(articles.getArticle("a1")).thenReturn(expected);

    ArticleView actual = facade.getArticle("a1");

    assertThat(actual).isSameAs(expected);
    verify(articles).getArticle("a1");
  }

  @Test
  void ensureCourseShell_delegatesToCourseShellService() {
    facade.ensureCourseShell("c1");
    verify(shell).ensureCourseShell("c1");
  }

  @Test
  void syncLearningContent_delegatesToLearningContentSyncService() {
    facade.syncLearningContent();
    verify(sync).syncLearningContent();
  }
}
