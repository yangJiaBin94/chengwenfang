package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeToolStepView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一轮问答内累积工具步骤。
 */
public final class KnowledgeToolStepRecorder {

  /** 步骤列表。 */
  private final List<KnowledgeToolStepView> steps = new ArrayList<>();

  /**
   * 记录一步。
   *
   * @param name          工具名
   * @param args          参数
   * @param resultSummary 结果摘要
   * @param latencyMs     耗时
   * @param ok            是否成功
   */
  public void append(String name, String args, String resultSummary, long latencyMs, boolean ok) {
    steps.add(KnowledgeToolStepView.builder()
        .name(StrUtil.nullToEmpty(name))
        .args(StrUtil.nullToEmpty(args))
        .resultSummary(StrUtil.nullToEmpty(resultSummary))
        .latencyMs(latencyMs)
        .ok(ok)
        .build());
  }

  /**
   * @return 不可变步骤列表
   */
  public List<KnowledgeToolStepView> snapshot() {
    return Collections.unmodifiableList(new ArrayList<>(steps));
  }
}
