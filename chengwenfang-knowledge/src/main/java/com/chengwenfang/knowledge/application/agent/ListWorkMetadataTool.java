package com.chengwenfang.knowledge.application.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.WorkOutputKnowledgeSnapshot;
import com.chengwenfang.knowledge.facade.constant.KnowledgeToolNames;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 只读作品元数据列表（禁止写库）。
 */
@Component
@RequiredArgsConstructor
public class ListWorkMetadataTool implements KnowledgeAgentTool {

  /** 内容门面。 */
  private final ContentFacade contentFacade;

  /** {@inheritDoc} */
  @Override
  public String name() {
    return KnowledgeToolNames.LIST_WORK_METADATA;
  }

  /**
   * {@inheritDoc}
   *
   * @param args 可选关键词过滤
   * @return 摘要
   */
  @Override
  public String execute(String args) {
    List<WorkOutputKnowledgeSnapshot> snapshots = contentFacade.listMasterKnowledgeSnapshots();
    if (CollUtil.isEmpty(snapshots)) {
      return "works=0";
    }
    String keyword = StrUtil.nullToEmpty(args).trim().toLowerCase();
    List<WorkOutputKnowledgeSnapshot> filtered = snapshots.stream()
        .filter(snapshot -> StrUtil.isBlank(keyword)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(snapshot.getTitle()), keyword)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(snapshot.getOutputId()), keyword))
        .limit(12)
        .toList();
    return filtered.stream()
        .map(snapshot -> StrUtil.blankToDefault(snapshot.getOutputId(), "?")
            + "|"
            + StrUtil.maxLength(StrUtil.blankToDefault(snapshot.getTitle(), "未命名"), 40))
        .collect(Collectors.joining("; "));
  }
}
