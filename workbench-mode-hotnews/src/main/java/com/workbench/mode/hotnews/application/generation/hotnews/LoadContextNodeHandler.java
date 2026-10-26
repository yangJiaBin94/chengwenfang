package com.workbench.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.mode.hotnews.application.hotnews.HotNewsThemeContextResolver;
import com.workbench.common.BusinessException;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.workbench.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.workbench.mode.hotnews.facade.enums.HotNewsWordCountOption;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 读取文章骨架、选题与资料线索。
 * <p>成文以骨架结构为主；资料仅作线索，不在本节点再规划大纲。
 */
@Component
@RequiredArgsConstructor
public class LoadContextNodeHandler implements HotNewsNodeHandler {
  /** 热点门面「*/
  private final HotNewsFacade hotNews;
  /** 主题成文上下文解析「*/
  private final HotNewsThemeContextResolver themeContextResolver;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.LOAD_CONTEXT;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String articleId = state.getArticleId();
    if (StrUtil.isBlank(articleId)) {
      throw new BusinessException(HotNewsErrorCodes.ARTICLE_ID_REQUIRED, "articleId 不能为空", 400);
    }
    // 读取文章与选题
    HotNewsArticleView article = hotNews.getHotArticle(articleId);
    String topicId = StrUtil.firstNonBlank(state.getTopicId(), article.getTopicId());
    HotNewsTopicView topic = StrUtil.isBlank(topicId) ? null : hotNews.getTopic(topicId);
    HotNewsArticleSkeletonView skeleton =
        StrUtil.isBlank(topicId) ? null : hotNews.getArticleSkeleton(topicId);
    if (skeleton == null || StrUtil.isBlank(skeleton.getCoreThesis())) {
      throw new BusinessException(
          HotNewsErrorCodes.ARTICLE_SKELETON_REQUIRED,
          "请先拟定文章骨架，再生成公众号文章",
          422);
    }
    // 骨架必须带结构（sectionPlan / architecture / modules 任一非空）
    if (!hasStructure(skeleton)) {
      throw new BusinessException(
          HotNewsErrorCodes.SKELETON_STRUCTURE_REQUIRED,
          "文章骨架缺少结构规划（sectionPlan/architecture），请先完善骨架",
          422);
    }
    // 参考资料仅作线索
    List<HotNewsResearchItemView> references =
        StrUtil.isBlank(topicId) ? List.of() : hotNews.listArticleReferenceItems(topicId);
    Map<String, Object> settings = article.getGenerationSettings() == null
        ? Map.of()
        : article.getGenerationSettings();
    String wordCountOption = HotNewsWordCountOption.normalize(
        StrUtil.firstNonBlank(
            state.getWordCountOption(),
            Convert.toStr(settings.get(HotNewsGraphStateKeys.WORD_COUNT_OPTION), null),
            skeleton.getRecommendedWordCountOption(),
            HotNewsImageSizes.DEFAULT_WORD_COUNT_OPTION));
    String imageCountOption = HotNewsImageCountOption.normalize(
        StrUtil.firstNonBlank(
            state.getImageCountOption(),
            Convert.toStr(settings.get(HotNewsGraphStateKeys.IMAGE_COUNT_OPTION), null),
            skeleton.getRecommendedImageCountOption(),
            HotNewsImageSizes.DEFAULT_IMAGE_COUNT_OPTION));

    List<HotNewsReferenceLine> referenceLines = new ArrayList<>();
    for (HotNewsResearchItemView item : references) {
      if (referenceLines.size() >= 12) {
        break;
      }
      String summary = StrUtil.nullToEmpty(item.getSummary());
      if (summary.length() > 400) {
        summary = summary.substring(0, 400);
      }
      referenceLines.add(HotNewsReferenceLine.builder()
          .title(item.getTitle())
          .summary(summary)
          .url(item.getUrl())
          .build());
    }

    state.setArticleId(articleId);
    state.setTopicId(topicId);
    state.setBriefId(article.getBriefId());
    state.setTitle(StrUtil.firstNonBlank(
        article.getTitle(),
        skeleton.getWorkingTitle(),
        topic == null ? null : topic.getTitle(),
        "热点文章"));
    state.setTopicSummary(topic == null ? "" : StrUtil.nullToEmpty(topic.getSummary()));
    // 注入主题读者与边界，供后续文本节点拼入 systemPrompt
    state.setThemeContext(themeContextResolver.resolve(topic == null ? null : topic.getThemeId()));
    state.setSkeleton(skeleton);
    state.setSkeletonJson(JSONUtil.toJsonStr(skeleton));
    state.setReferencesJson(JSONUtil.toJsonStr(referenceLines));
    state.setWordCountOption(wordCountOption);
    state.setImageCountOption(imageCountOption);
  }

  /**
   * 骨架是否已含可执行结构。
   *
   * @param skeleton 文章骨架
   * @return 有结构则个true
   */
  private static boolean hasStructure(HotNewsArticleSkeletonView skeleton) {
    if (CollUtil.isNotEmpty(skeleton.getSectionPlan())) {
      return true;
    }
    if (CollUtil.isNotEmpty(skeleton.getArchitecture())) {
      return true;
    }
    return CollUtil.isNotEmpty(skeleton.getModules());
  }
}
