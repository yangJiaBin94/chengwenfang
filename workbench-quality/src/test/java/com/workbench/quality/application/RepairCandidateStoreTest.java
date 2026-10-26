package com.workbench.quality.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.quality.domain.entity.HotArticleRepairCandidateEntity;
import com.workbench.quality.domain.entity.RepairCandidateIssueEntity;
import com.workbench.quality.infrastructure.mapper.RepairCandidateIssueMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RepairCandidateStore} 正文文件与 issueIdsJson 往返。
 */
@ExtendWith(MockitoExtension.class)
class RepairCandidateStoreTest {

  @Mock
  private RepairCandidateIssueMapper issueMapper;
  @Mock
  private FileFacade files;

  private RepairCandidateStore store;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<RepairCandidateIssueEntity> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    store = new RepairCandidateStore(issueMapper, files, objectMapper);
    lenient().doAnswer(invocation -> {
      stored.add(invocation.getArgument(0));
      return 1;
    }).when(issueMapper).insert(any(RepairCandidateIssueEntity.class));
    lenient().when(issueMapper.delete(any())).thenAnswer(invocation -> {
      stored.clear();
      return 1;
    });
    lenient().when(issueMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(stored));
  }

  @Test
  void empty_issue_ids_json_is_brackets_and_body_roundtrips() throws Exception {
    HotArticleRepairCandidateEntity candidate = HotArticleRepairCandidateEntity.builder()
        .id("cand-1")
        .outputId("out-1")
        .build();
    when(files.writeText(anyString(), anyString(), eq("候选正文")))
        .thenReturn(StoredFileView.builder().id("file-1").build());
    when(files.readText("file-1")).thenReturn("候选正文");

    store.writeBody(candidate, "候选正文");
    assertThat(candidate.getBodyFileId()).isEqualTo("file-1");
    assertThat(store.readBody(candidate)).isEqualTo("候选正文");
    verify(files).writeText(eq("repairs/out-1/cand-1.md"), eq("cand-1.md"), eq("候选正文"));

    boolean repairAll = store.replaceIssues(candidate.getId(), List.of());
    assertThat(repairAll).isTrue();
    assertThat(store.issueIdsJson(candidate.getId())).isEqualTo("[]");
    assertThat(store.issueIdsJson(candidate.getId()))
        .isEqualTo(objectMapper.writeValueAsString(List.of()));
  }

  @Test
  void issue_ids_json_matches_object_mapper() throws Exception {
    boolean repairAll = store.replaceIssues("cand-2", List.of("i1", "i2"));
    assertThat(repairAll).isFalse();
    assertThat(store.readIssueIds("cand-2")).containsExactly("i1", "i2");
    assertThat(store.issueIdsJson("cand-2"))
        .isEqualTo(objectMapper.writeValueAsString(List.of("i1", "i2")));
  }
}
