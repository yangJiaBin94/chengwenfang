package com.workbench.crawler.facade.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link ArticleBrowserModes}。 */
class ArticleBrowserModesTest {

  @Test
  void normalizeDefaultsToDockerExec() {
    assertEquals(ArticleBrowserModes.DOCKER_EXEC, ArticleBrowserModes.normalize(null));
    assertEquals(ArticleBrowserModes.DOCKER_EXEC, ArticleBrowserModes.normalize(" "));
    assertEquals(ArticleBrowserModes.DOCKER_EXEC, ArticleBrowserModes.normalize("other"));
    assertEquals(ArticleBrowserModes.BINARY, ArticleBrowserModes.normalize("BINARY"));
  }

  @Test
  void modeMatchers() {
    assertTrue(ArticleBrowserModes.isDockerExec("docker-exec"));
    assertTrue(ArticleBrowserModes.isBinary("binary"));
    assertFalse(ArticleBrowserModes.isBinary("docker-exec"));
  }
}
