package com.chengwenfang.quality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.quality.domain.entity.ArticleAuditIssueEntity;
import com.chengwenfang.quality.domain.entity.ArticleAuditRunEntity;
import com.chengwenfang.quality.domain.entity.AuditIssueRefEntity;
import com.chengwenfang.quality.facade.constant.AuditIssueMapKeys;
import com.chengwenfang.quality.facade.constant.AuditIssueRefTypes;
import com.chengwenfang.quality.facade.constant.HotAuditResultKeys;
import com.chengwenfang.quality.infrastructure.mapper.ArticleAuditIssueMapper;
import com.chengwenfang.quality.infrastructure.mapper.AuditIssueRefMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link HotAuditResultStore} 热点结果 Map 往返。
 */
@ExtendWith(MockitoExtension.class)
class HotAuditResultStoreTest {

  @Mock
  private ArticleAuditIssueMapper issueMapper;
  @Mock
  private AuditIssueRefMapper refMapper;

  private HotAuditResultStore store;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<ArticleAuditIssueEntity> storedIssues = new ArrayList<>();
  private final List<AuditIssueRefEntity> storedRefs = new ArrayList<>();

  @BeforeEach
  void setUp() {
    store = new HotAuditResultStore(issueMapper, refMapper, objectMapper);
    lenient().when(issueMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(storedIssues));
    lenient().when(refMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(storedRefs));
    doAnswer(invocation -> {
      ArticleAuditIssueEntity issue = invocation.getArgument(0);
      storedIssues.add(issue);
      return 1;
    }).when(issueMapper).insert(any(ArticleAuditIssueEntity.class));
    doAnswer(invocation -> {
      AuditIssueRefEntity ref = invocation.getArgument(0);
      storedRefs.add(ref);
      return 1;
    }).when(refMapper).insert(any(AuditIssueRefEntity.class));
    lenient().when(issueMapper.delete(any())).thenAnswer(invocation -> {
      storedIssues.clear();
      return 1;
    });
    lenient().when(refMapper.delete(any())).thenAnswer(invocation -> {
      storedRefs.clear();
      return 1;
    });
  }

  @Test
  void write_then_read_roundtrips_result_map() {
    ArticleAuditRunEntity run = ArticleAuditRunEntity.builder().id("run-1").build();
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("path", "body");
    location.put("offset", 12);
    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("quote", "原文片段");

    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put(AuditIssueMapKeys.CODE, "DUP_HEADING");
    issue.put(AuditIssueMapKeys.ENGINE, "rule");
    issue.put(AuditIssueMapKeys.CATEGORY, "structure");
    issue.put(AuditIssueMapKeys.STATUS, "failed");
    issue.put(AuditIssueMapKeys.SEVERITY, "blocking");
    issue.put(AuditIssueMapKeys.BLOCKING, true);
    issue.put(AuditIssueMapKeys.TITLE, "重复标题");
    issue.put(AuditIssueMapKeys.DETAIL, "发现重复");
    issue.put(AuditIssueMapKeys.LOCATIONS, List.of(location));
    issue.put(AuditIssueMapKeys.EVIDENCE, List.of(evidence));
    issue.put(AuditIssueMapKeys.REPAIRABLE, true);
    issue.put(AuditIssueMapKeys.REQUIRES_HUMAN_CONFIRMATION, false);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(HotAuditResultKeys.STATUS, "failed");
    payload.put(HotAuditResultKeys.BLOCKING_FAILED, true);
    payload.put(HotAuditResultKeys.RESULTS, List.of(issue));
    payload.put(HotAuditResultKeys.DECISION_STATUS, "approved");
    payload.put(HotAuditResultKeys.DECISION_COMMENT, "可发");
    payload.put(HotAuditResultKeys.DECIDED_AT, LocalDateTime.of(2026, 10, 22, 11, 0).toString());

    store.write(run, payload, LocalDateTime.now());

    assertThat(run.getResultStatus()).isEqualTo("failed");
    assertThat(run.getBlockingFailed()).isTrue();
    assertThat(run.getDecisionStatus()).isEqualTo("approved");
    assertThat(run.getDecisionComment()).isEqualTo("可发");
    assertThat(run.getDecidedAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 11, 0).toString());

    ArgumentCaptor<ArticleAuditIssueEntity> issueCaptor =
        ArgumentCaptor.forClass(ArticleAuditIssueEntity.class);
    verify(issueMapper).insert(issueCaptor.capture());
    assertThat(issueCaptor.getValue().getCode()).isEqualTo("DUP_HEADING");
    assertThat(issueCaptor.getValue().getSeq()).isEqualTo(0);

    Map<String, Object> read = store.read(run);
    assertThat(read.get(HotAuditResultKeys.STATUS)).isEqualTo("failed");
    assertThat(read.get(HotAuditResultKeys.BLOCKING_FAILED)).isEqualTo(true);
    assertThat(read.get(HotAuditResultKeys.DECISION_STATUS)).isEqualTo("approved");
    assertThat(read.get(HotAuditResultKeys.DECISION_COMMENT)).isEqualTo("可发");
    assertThat(read.get(HotAuditResultKeys.DECIDED_AT))
        .isEqualTo(LocalDateTime.of(2026, 10, 22, 11, 0).toString());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) read.get(HotAuditResultKeys.RESULTS);
    assertThat(results).hasSize(1);
    Map<String, Object> roundtrip = results.get(0);
    assertThat(roundtrip.get(AuditIssueMapKeys.CODE)).isEqualTo("DUP_HEADING");
    assertThat(roundtrip.get(AuditIssueMapKeys.ENGINE)).isEqualTo("rule");
    assertThat(roundtrip.get(AuditIssueMapKeys.CATEGORY)).isEqualTo("structure");
    assertThat(roundtrip.get(AuditIssueMapKeys.SEVERITY)).isEqualTo("blocking");
    assertThat(roundtrip.get(AuditIssueMapKeys.REPAIRABLE)).isEqualTo(true);
    assertThat(roundtrip.get(AuditIssueMapKeys.REQUIRES_HUMAN_CONFIRMATION)).isEqualTo(false);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> locations =
        (List<Map<String, Object>>) roundtrip.get(AuditIssueMapKeys.LOCATIONS);
    assertThat(locations).containsExactly(location);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> evidences =
        (List<Map<String, Object>>) roundtrip.get(AuditIssueMapKeys.EVIDENCE);
    assertThat(evidences).containsExactly(evidence);

    assertThat(storedRefs)
        .extracting(AuditIssueRefEntity::getRefType)
        .containsExactly(AuditIssueRefTypes.LOCATION, AuditIssueRefTypes.EVIDENCE);
  }
}
