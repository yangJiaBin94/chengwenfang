package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.HotNewsThemeBriefingSupport;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.crawler.facade.enums.ChannelType;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsSourceChannelGenerateView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsSourceChannelSkippedView;
import com.workbench.sourcing.facade.constant.HotNewsChannelLimits;
import com.workbench.sourcing.facade.dto.view.HotNewsChannelView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 渠道建议落库：AI 候选经校验后创建来源渠道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSuggestionPersister {

  /** 内容主题门面。 */
  private final ContentFacade content;
  /** 热点门面（列渠道）。 */
  private final HotNewsFacade hotNews;
  /** 提示词与 AI。 */
  private final ChannelSuggestionPromptBuilder prompts;
  /** 校验与连通性。 */
  private final ChannelSuggestionValidator validator;

  public HotNewsSourceChannelGenerateView generate(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在", 404);
    }
    ContentThemeView theme = content.getTheme(themeId);
    if (theme == null || theme.getDeletedAt() != null) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "主题不存在", 404);
    }

    Map<String, Object> briefing = ChannelSuggestionParser.readJsonMap(theme.getBriefingJson());
    if (briefing.isEmpty()) {
      briefing = ChannelSuggestionParser.readJsonMap(theme.getDefaultsJson());
    }
    String themeName = StrUtil.blankToDefault(theme.getName(), HotNewsThemeBriefingSupport.themeName(briefing));
    String description = ChannelSuggestionPromptBuilder.resolveDescription(theme, briefing);
    List<String> searchQueries = HotNewsThemeBriefingSupport.searchQueries(briefing);
    List<String> mustWatch = HotNewsThemeBriefingSupport.mustWatch(briefing);
    String githubQuery = HotNewsThemeBriefingSupport.githubQuery(briefing);
    String themeContext = HotNewsThemeBriefingSupport.themeContextPrompt(briefing, themeName);

    List<HotNewsChannelView> existing = hotNews.listChannels(themeId);
    if (existing == null) {
      existing = List.of();
    }
    ChannelSuggestionExistingIndex existingIndex = ChannelSuggestionExistingIndex.from(existing);

    List<ChannelSuggestionParser.AiChannelCandidate> candidates = prompts.tryAi(
        themeName, description, searchQueries, mustWatch, githubQuery, themeContext, existingIndex);
    if (CollUtil.isEmpty(candidates)) {
      log.warn("热点主题来源渠道 AI 未产出可用候选 themeId={} name={}", themeId, themeName);
      return HotNewsSourceChannelGenerateView.builder()
          .created(List.of())
          .skipped(List.of())
          .fromAi(false)
          .build();
    }

    List<HotNewsChannelView> created = new ArrayList<>();
    List<HotNewsSourceChannelSkippedView> skipped = new ArrayList<>();
    // 本轮已采纳的地址，避免同批重复
    Set<String> acceptedKeys = new LinkedHashSet<>(existingIndex.dedupeKeys());
    // 本轮是否已新建网页渠道（提示词要求网页合并为一条）
    boolean webCreatedThisRound = false;
    // 仅已启用渠道占配额（与 createChannel 校验一致）
    int remainingSlots =
        HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - existingIndex.enabledCount();

    for (ChannelSuggestionParser.AiChannelCandidate candidate : candidates) {
      if (remainingSlots <= 0) {
        skipped.add(HotNewsSourceChannelSkippedView.builder()
            .name(candidate == null ? "" : StrUtil.trim(StrUtil.nullToEmpty(candidate.name())))
            .type(candidate == null ? "" : StrUtil.nullToEmpty(candidate.type()))
            .baseUrl(candidate == null ? "" : StrUtil.nullToEmpty(candidate.baseUrl()))
            .reason(HotNewsSourceChannelSkipReasons.THEME_CHANNEL_LIMIT)
            .build());
        continue;
      }
      ChannelType candidateType = ChannelType.from(candidate == null ? null : candidate.type());
      if (candidateType == ChannelType.WEB && webCreatedThisRound) {
        skipped.add(HotNewsSourceChannelSkippedView.builder()
            .name(candidate == null ? "" : StrUtil.trim(StrUtil.nullToEmpty(candidate.name())))
            .type(ChannelType.WEB.getValue())
            .baseUrl(candidate == null ? "" : StrUtil.nullToEmpty(candidate.baseUrl()))
            .reason(HotNewsSourceChannelSkipReasons.WEB_ALREADY_CREATED)
            .build());
        continue;
      }
      ChannelSuggestionProcessResult result = validator.processCandidate(themeId, candidate , existingIndex, acceptedKeys, searchQueries, githubQuery);
      if (result.created() != null) {
        created.add(result.created());
        acceptedKeys.addAll(result.acceptedKeys());
        remainingSlots--;
        if (ChannelType.WEB.getValue().equalsIgnoreCase(result.created().getType())) {
          webCreatedThisRound = true;
        }
      } else if (result.skipped() != null) {
        skipped.add(result.skipped());
      }
    }

    log.info(
        "热点主题来源渠道 AI 完成 themeId={} created={} skipped={}",
        themeId,
        created.size(),
        skipped.size());
    return HotNewsSourceChannelGenerateView.builder()
        .created(created)
        .skipped(skipped)
        .fromAi(true)
        .build();
  }


}
