package com.chengwenfang.knowledge.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import org.junit.jupiter.api.Test;

/** {@link AnswerFaithfulnessJudge} 单元测试。 */
class AnswerFaithfulnessJudgeTest {

  @Test
  void parsesYesNo() {
    assertThat(AnswerFaithfulnessJudge.passes("YES")).isTrue();
    assertThat(AnswerFaithfulnessJudge.passes("NO")).isFalse();
  }

  @Test
  void skipsAbstain() {
    assertThat(AnswerFaithfulnessJudge.shouldSkip(KnowledgeRagConstants.ABSTAIN_MESSAGE)).isTrue();
  }
}
