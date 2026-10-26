package com.workbench.quality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.workbench.quality.domain.entity.ArticleAuditIssueEntity;
import com.workbench.quality.domain.entity.ArticleAuditRunEntity;
import com.workbench.quality.facade.constant.CourseAuditSummaryKeys;
import com.workbench.quality.facade.dto.view.AuditItemView;
import com.workbench.quality.facade.dto.view.AuditResultView;
import com.workbench.quality.facade.enums.ReviewStatus;
import com.workbench.quality.infrastructure.mapper.ArticleAuditIssueMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CourseAuditSummaryStore} 课程汇总 Map 往返。
 */
@ExtendWith(MockitoExtension.class)
class CourseAuditSummaryStoreTest {

  @Mock
  private ArticleAuditIssueMapper issueMapper;

  private CourseAuditSummaryStore store;
  private final List<ArticleAuditIssueEntity> storedIssues = new ArrayList<>();

  @BeforeEach
  void setUp() {
    store = new CourseAuditSummaryStore(issueMapper);
    doAnswer(invocation -> {
      storedIssues.add(invocation.getArgument(0));
      return 1;
    }).when(issueMapper).insert(any(ArticleAuditIssueEntity.class));
    when(issueMapper.delete(any())).thenAnswer(invocation -> {
      storedIssues.clear();
      return 1;
    });
    when(issueMapper.selectCount(any())).thenAnswer(invocation ->
        storedIssues.stream()
            .filter(issue -> ReviewStatus.PASSED.getValue().equals(issue.getStatus()))
            .count());
  }

  @Test
  void write_then_read_rebuilds_passed_count_from_issues() {
    ArticleAuditRunEntity run = ArticleAuditRunEntity.builder().id("course-run").build();
    AuditResultView result = AuditResultView.builder()
        .score(82)
        .passed(2)
        .warnings(1)
        .failed(1)
        .blocking(true)
        .items(List.of(
            AuditItemView.builder().code("A").title("通过").status("pass").blocking(false).detail("").build(),
            AuditItemView.builder().code("B").title("通过2").status("pass").blocking(false).detail("").build(),
            AuditItemView.builder().code("C").title("告警").status("warn").blocking(false).detail("w").build(),
            AuditItemView.builder().code("D").title("失败").status("fail").blocking(true).detail("f").build()))
        .build();

    store.write(run, result, LocalDateTime.now());

    assertThat(run.getScore()).isEqualTo(82);
    assertThat(run.getWarningCount()).isEqualTo(1);
    assertThat(run.getFailedCount()).isEqualTo(1);
    assertThat(run.getBlockingFailed()).isTrue();
    assertThat(run.getPassed()).isFalse();
    assertThat(storedIssues).hasSize(4);

    Map<String, Object> summary = store.read(run);
    assertThat(summary.get(CourseAuditSummaryKeys.SCORE)).isEqualTo(82);
    assertThat(summary.get(CourseAuditSummaryKeys.PASSED)).isEqualTo(2);
    assertThat(summary.get(CourseAuditSummaryKeys.WARNINGS)).isEqualTo(1);
    assertThat(summary.get(CourseAuditSummaryKeys.FAILED)).isEqualTo(1);
    assertThat(summary.get(CourseAuditSummaryKeys.BLOCKING)).isEqualTo(true);
  }
}
