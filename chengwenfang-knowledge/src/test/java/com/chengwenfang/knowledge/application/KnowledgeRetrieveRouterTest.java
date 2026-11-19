package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.enums.KnowledgeRetrieveMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 检索路由：GRAPH_GLOBAL 降级 Hybrid。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeRetrieveRouterTest {

  @Mock
  private AiVisionFacade aiVisionFacade;
  @Mock
  private KnowledgePromptRegistry promptRegistry;

  private KnowledgeRetrieveRouter router;

  @BeforeEach
  void setUp() {
    ChengwenfangProperties properties = new ChengwenfangProperties();
    ChengwenfangProperties.KnowledgeSettings knowledge = new ChengwenfangProperties.KnowledgeSettings();
    ChengwenfangProperties.GraphSettings graph = new ChengwenfangProperties.GraphSettings();
    graph.setEnabled(true);
    knowledge.setGraph(graph);
    properties.setKnowledge(knowledge);
    when(promptRegistry.systemTemplate(any())).thenReturn("route");
    router = new KnowledgeRetrieveRouter(properties, aiVisionFacade, promptRegistry);
  }

  @Test
  void globalKeywordsDegradeToHybrid() {
    when(aiVisionFacade.complete(any(VisionCompleteRequest.class))).thenReturn(null);
    assertThat(router.route("请总结一下库的整体趋势与演进"))
        .isEqualTo(KnowledgeRetrieveMode.HYBRID);
  }

  @Test
  void llmGraphGlobalDegradesToHybrid() {
    when(aiVisionFacade.complete(any(VisionCompleteRequest.class)))
        .thenReturn(VisionCompleteResult.builder().text("{\"mode\":\"graph_global\"}").build());
    assertThat(router.route("社区主题对比如何")).isEqualTo(KnowledgeRetrieveMode.HYBRID);
  }

  @Test
  void localKeywordsStillLocal() {
    when(aiVisionFacade.complete(any(VisionCompleteRequest.class))).thenReturn(null);
    assertThat(router.route("A 与 B 的关系是什么")).isEqualTo(KnowledgeRetrieveMode.GRAPH_LOCAL);
  }
}
