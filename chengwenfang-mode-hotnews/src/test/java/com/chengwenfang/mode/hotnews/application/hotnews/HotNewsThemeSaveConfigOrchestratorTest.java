package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link HotNewsThemeSaveConfigOrchestrator}：进行中任务拒绝、委托自动配源同步。
 */
class HotNewsThemeSaveConfigOrchestratorTest {
  private ContentFacade content;
  private HotNewsFacade hotNews;
  private HotNewsThemeChannelAutoWireSupport autoWire;
  private HotNewsThemeSaveConfigOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    content = mock(ContentFacade.class);
    hotNews = mock(HotNewsFacade.class);
    autoWire = mock(HotNewsThemeChannelAutoWireSupport.class);
    orchestrator = new HotNewsThemeSaveConfigOrchestrator(content, hotNews, autoWire);
  }

  @Test
  void saveConfig_rejectsWhenThemeHasActiveJobs() {
    when(content.getTheme("theme-1")).thenReturn(ContentThemeView.builder()
        .id("theme-1")
        .status("enabled")
        .build());
    when(hotNews.hasActiveJobsForTheme("theme-1")).thenReturn(true);

    assertThatThrownBy(() -> orchestrator.saveConfig("theme-1", new ThemeBriefingPatchRequest()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("进行中");

    verify(content, never()).saveHotNewsConfigBriefing(any(), any());
    verify(autoWire, never()).syncOnSaveConfig(any(), any());
  }

  @Test
  void saveConfig_delegatesToAutoWireSync() {
    String briefingJson = """
        {
          "naming":{"name":"AI"},
          "radar":{"search_queries":["Claude 发布"],"seed_channels":[],"github_query":""},
          "core_intent":{"summary":"抓前沿","material_wants":["发布"],"material_avoids":["旧闻"],
            "success_looks_like":"可写","confirmed":true},
          "collect_strategy":{"mode":"structured","rationale":"固定词","user_override":false},
          "scoring_rubric":{"version":1,"score_guidance":"按价值",
            "dimensions":[{"key":"a","label":"A","weight":100,"description":"d"}]}
        }
        """;
    ContentThemeView theme = ContentThemeView.builder()
        .id("theme-1")
        .status("enabled")
        .briefingJson(briefingJson)
        .build();
    when(content.getTheme("theme-1")).thenReturn(theme);
    when(hotNews.hasActiveJobsForTheme("theme-1")).thenReturn(false);
    when(content.saveHotNewsConfigBriefing(eq("theme-1"), any())).thenReturn(theme);
    when(autoWire.syncOnSaveConfig(eq("theme-1"), any()))
        .thenReturn(new HotNewsThemeChannelAutoWireSupport.AutoWireResult(3, List.of("searxng")));

    HotNewsThemeCommitView result =
        orchestrator.saveConfig("theme-1", ThemeBriefingPatchRequest.builder().build());

    verify(autoWire).syncOnSaveConfig(eq("theme-1"), any());
    verify(hotNews, never()).deleteChannel(any());
    assertThat(result.getChannelCount()).isEqualTo(3);
    assertThat(result.getSeededChannelTypes()).containsExactly("searxng");
  }
}
