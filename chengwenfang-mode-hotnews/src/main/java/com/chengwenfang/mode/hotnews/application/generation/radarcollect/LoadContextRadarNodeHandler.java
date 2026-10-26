package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.sourcing.facade.constant.HotNewsFetchKeys;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 读取主题、渠道与采集配额，并固化主题相关性信号。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadContextRadarNodeHandler implements RadarCollectNodeHandler {
  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 热点门面（写入雷达批次）。 */
  private final HotNewsFacade hotNews;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.LOAD_CONTEXT;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String themeId = stringVal(lightState.get(RadarCollectGraphStateKeys.THEME_ID));
    if (StrUtil.isBlank(themeId)) {
      themeId = HotNewsFetchKeys.DEFAULT_THEME_ID;
    }
    session.setThemeId(themeId);
    session.setFetchRunId(stringVal(lightState.get(RadarCollectGraphStateKeys.FETCH_RUN_ID)));
    session.setHours(Convert.toInt(lightState.get(RadarCollectGraphStateKeys.HOURS), 24));
    session.getChannelIds().clear();
    Object channelIds = lightState.get(RadarCollectGraphStateKeys.CHANNEL_IDS);
    if (channelIds instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
          session.getChannelIds().add(String.valueOf(item));
        }
      }
    }
    int maxArticle = RadarCollectQuotaDefaults.MAX_ARTICLE_PER_FETCH;
    int maxVideo = properties.getVideoTranscript() == null
        ? RadarCollectQuotaDefaults.MAX_VIDEO_PER_FETCH
        : Math.max(0, properties.getVideoTranscript().getMaxPerFetch());
    session.setMaxArticlePerFetch(maxArticle);
    session.setMaxVideoPerFetch(maxVideo);
    String radarBatchId = IdsUtil.newId();
    session.setRadarBatchId(radarBatchId);
    // 批次行先于落库成簇写入，满足 topic_clusters.radar_batch_id FK
    String jobId = stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID));
    hotNews.ensureRadarBatch(radarBatchId, themeId, jobId);
    // 固化主题名/搜索词/关注点，供获取后与素材筛选使用
    session.setThemeRelevanceProfile(resolveThemeProfile(themeId));
    lightState.put(RadarCollectGraphStateKeys.THEME_ID, themeId);
    lightState.put(RadarCollectGraphStateKeys.RADAR_BATCH_ID, radarBatchId);
    lightState.put(RadarCollectGraphStateKeys.MAX_ARTICLE_PER_FETCH, maxArticle);
    lightState.put(RadarCollectGraphStateKeys.MAX_VIDEO_PER_FETCH, maxVideo);
    lightState.put(RadarCollectGraphStateKeys.HOURS, session.getHours());
  }

  /**
   * 从主题问卷组装相关性 Profile。
   *
   * @param themeId 主题 ID
   * @return Profile；失败时退回空信号（后续过滤放行）
   */
  private ThemeTitleRelevance.Profile resolveThemeProfile(String themeId) {
    try {
      ContentThemeView theme = content.getTheme(themeId);
      if (theme == null) {
        return new ThemeTitleRelevance.Profile("", List.of(), List.of());
      }
      Map<String, Object> briefing = readBriefing(theme.getBriefingJson());
      String name = StrUtil.firstNonBlank(
          HotNewsThemeBriefingSupport.themeName(briefing),
          theme.getName());
      List<String> queries = HotNewsThemeBriefingSupport.searchQueries(briefing);
      List<String> mustWatch = HotNewsThemeBriefingSupport.mustWatch(briefing);
      log.info(
          "雷达主题信号已加载 themeId={} name={} queries={} mustWatch={}",
          themeId,
          name,
          queries.size(),
          mustWatch.size());
      return new ThemeTitleRelevance.Profile(name, queries, mustWatch);
    } catch (Exception exception) {
      log.warn("加载主题相关性信号失败 themeId={} err={}", themeId, exception.getMessage());
      return new ThemeTitleRelevance.Profile("", List.of(), List.of());
    }
  }

  private static Map<String, Object> readBriefing(String json) {
    if (StrUtil.isBlank(json)) {
      return Map.of();
    }
    try {
      Object parsed = JSONUtil.parse(json);
      if (parsed instanceof Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          if (entry.getKey() != null) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
          }
        }
        return out;
      }
    } catch (Exception ignored) {
      // 问卷损坏时退回空 map
    }
    return Map.of();
  }

  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
