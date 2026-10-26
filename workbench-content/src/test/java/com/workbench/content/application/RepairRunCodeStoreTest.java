package com.workbench.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.workbench.content.domain.entity.RepairRunCodeEntity;
import com.workbench.content.facade.constant.RepairRunCodeTypes;
import com.workbench.content.infrastructure.mapper.RepairRunCodeMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RepairRunCodeStore} resolved / added 往返。
 */
@ExtendWith(MockitoExtension.class)
class RepairRunCodeStoreTest {

  @Mock
  private RepairRunCodeMapper codeMapper;

  private RepairRunCodeStore store;
  private final List<RepairRunCodeEntity> stored = new ArrayList<>();

  @BeforeEach
  void setUp() {
    store = new RepairRunCodeStore(codeMapper);
    doAnswer(invocation -> {
      stored.add(invocation.getArgument(0));
      return 1;
    }).when(codeMapper).insert(any(RepairRunCodeEntity.class));
    when(codeMapper.delete(any())).thenAnswer(invocation -> {
      stored.clear();
      return 1;
    });
    when(codeMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(stored));
  }

  @Test
  void replace_then_read_preserves_order_by_type() {
    store.replace("rr-1", List.of("A", "B"), List.of("C"));
    assertThat(stored).hasSize(3);
    assertThat(stored)
        .extracting(RepairRunCodeEntity::getCodeType)
        .containsExactly(
            RepairRunCodeTypes.RESOLVED,
            RepairRunCodeTypes.RESOLVED,
            RepairRunCodeTypes.ADDED);

    when(codeMapper.selectList(any())).thenAnswer(invocation ->
        stored.stream()
            .filter(row -> RepairRunCodeTypes.RESOLVED.equals(row.getCodeType()))
            .toList());
    assertThat(store.readResolved("rr-1")).containsExactly("A", "B");

    when(codeMapper.selectList(any())).thenAnswer(invocation ->
        stored.stream()
            .filter(row -> RepairRunCodeTypes.ADDED.equals(row.getCodeType()))
            .toList());
    assertThat(store.readAdded("rr-1")).containsExactly("C");
  }
}
