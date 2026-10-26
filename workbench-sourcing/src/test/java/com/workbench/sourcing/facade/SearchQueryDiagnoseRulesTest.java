package com.workbench.sourcing.facade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link SearchQueryDiagnoseRules} 阈值单测。 */
class SearchQueryDiagnoseRulesTest {
  @Test
  void requiresMinSkippedAndRatio() {
    assertFalse(SearchQueryDiagnoseRules.shouldBlock(4, 4));
    assertFalse(SearchQueryDiagnoseRules.shouldBlock(5, 10));
    assertTrue(SearchQueryDiagnoseRules.shouldBlock(5, 8));
    assertTrue(SearchQueryDiagnoseRules.shouldBlock(6, 10));
    assertTrue(SearchQueryDiagnoseRules.shouldBlock(9, 10));
  }

  @Test
  void rejectsNonPositiveTotal() {
    assertFalse(SearchQueryDiagnoseRules.shouldBlock(5, 0));
    assertFalse(SearchQueryDiagnoseRules.shouldBlock(5, -1));
  }
}
