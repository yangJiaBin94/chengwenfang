package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.mode.hotnews.application.prompt.DraftOptimizeCritiquePrompts;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsGenerationSettingsKeys;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsOptimizeCritiqueRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsOptimizeCritiqueIssueView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsOptimizeCritiqueView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsOptimizeCritiqueDimension;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点原稿优化诊断：同步打分与可编辑建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsDraftOptimizeCritiqueService {
  /** 热点正文读写。 */
  private final HotNewsWorkContentService contents;
  /** 生成设置读写。 */
  private final HotNewsGenerationSettingsService generationSettings;
  /** 文章骨架存储。 */
  private final ArticleSkeletonStore skeletonStore;
  /** 选题资料查询。 */
  private final HotNewsResearchService research;
  /** JSON 编解码。 */
  private final HotNewsCodec codec;
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 对当前原稿做优化诊断并写入 generationSettings.lastOptimizeCritique。
   *
   * @param articleId 文章 id
   * @param body      请求；可空
   * @return 诊断视图
   */
  @Transactional
  public HotNewsOptimizeCritiqueView critique(String articleId, HotNewsOptimizeCritiqueRequest body) {
    log.info("开始原稿优化诊断 articleId={}", articleId);
    HotNewsArticleEntity article = contents.requireArticle(articleId);
    String draft = contents.resolveHotBody(
        article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    if (StrUtil.isBlank(draft)) {
      throw new BusinessException(HotNewsErrorCodes.DRAFT_EMPTY, "热点原稿为空，无法诊断", 400);
    }
    String notes = body == null ? "" : StrUtil.nullToEmpty(body.getNotes()).trim();
    HotNewsArticleSkeletonView skeleton = StrUtil.isBlank(article.getTopicId())
        ? null
        : skeletonStore.read(article.getTopicId());
    List<HotNewsResearchItemView> references = StrUtil.isBlank(article.getTopicId())
        ? List.of()
        : research.listArticleReferenceItems(article.getTopicId());
    String userPrompt = buildUserPrompt(draft, notes, skeleton, references);
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(DraftOptimizeCritiquePrompts.SYSTEM)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_DRAFT_OPTIMIZE_CRITIQUE)
          .timeoutSeconds(120)
          .build());
      HotNewsOptimizeCritiqueView view = parse(
          articleId, result == null ? null : result.getText());
      persistCritique(article, view);
      log.info(
          "原稿优化诊断完成 articleId={} overallScore={} issueCount={}",
          articleId,
          view.getOverallScore(),
          CollUtil.size(view.getIssues()));
      return view;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      log.warn("原稿优化诊断失败 articleId={} err={}", articleId, exception.getMessage());
      throw new BusinessException(
          HotNewsErrorCodes.OPTIMIZE_CRITIQUE_FAILED, "优化诊断失败：" + exception.getMessage(), 500);
    }
  }

  /**
   * 将诊断结果写入 generationSettings 并同步壳表。
   *
   * @param article 文章实体
   * @param view    诊断视图
   */
  private void persistCritique(HotNewsArticleEntity article, HotNewsOptimizeCritiqueView view) {
    Map<String, Object> settings = codec.readMap(generationSettings.preferGenerationSettingsJson(article));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("overallScore", view.getOverallScore());
    payload.put("dimensionScores", view.getDimensionScores());
    payload.put("issues", view.getIssues());
    payload.put("editableAdvice", view.getEditableAdvice());
    payload.put(HotNewsGenerationSettingsKeys.CRITIQUED_AT, LocalDateTime.now().toString());
    settings.put(HotNewsGenerationSettingsKeys.LAST_OPTIMIZE_CRITIQUE, payload);
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setUpdatedAt(LocalDateTime.now());
    contents.syncShellMetadata(article);
  }

  /**
   * 组装诊断用户提示（原稿 + 可选关注点、骨架与参考资料）。
   *
   * @param draft      原稿 Markdown
   * @param notes      用户补充；可空
   * @param skeleton   骨架；可空
   * @param references 参考资料；可空
   * @return 用户提示全文
   */
  static String buildUserPrompt(
      String draft,
      String notes,
      HotNewsArticleSkeletonView skeleton,
      List<HotNewsResearchItemView> references) {
    StringBuilder builder = new StringBuilder();
    builder.append("请诊断下列热点原稿并给出打分与可编辑优化建议。\n");
    if (StrUtil.isNotBlank(notes)) {
      builder.append("用户补充关注点：\n").append(notes.trim()).append("\n\n");
    }
    if (skeleton != null) {
      builder.append("骨架读者上下文（仅供参考）：\n");
      builder.append("targetAudience=").append(StrUtil.nullToEmpty(skeleton.getTargetAudience())).append('\n');
      builder.append("readerIntents=").append(CollUtil.emptyIfNull(skeleton.getReaderIntents())).append('\n');
      builder.append("readerGaps=").append(CollUtil.emptyIfNull(skeleton.getReaderGaps())).append('\n');
      builder.append("coreThesis=").append(StrUtil.nullToEmpty(skeleton.getCoreThesis())).append("\n\n");
    }
    builder.append(HotNewsReferencePromptFormatter.formatBlock(references)).append('\n');
    builder.append("原稿 Markdown：\n").append(draft);
    return builder.toString();
  }

  /**
   * 解析诊断 JSON。
   *
   * @param articleId 文章 id
   * @param raw       模型原文
   * @return 视图
   */
  static HotNewsOptimizeCritiqueView parse(String articleId, String raw) {
    JSONObject json = LlmJsonSupport.parseJsonObject(raw);
    if (json == null) {
      throw new BusinessException(HotNewsErrorCodes.OPTIMIZE_CRITIQUE_FAILED, "优化诊断结果无法解析", 500);
    }
    Map<String, Integer> dimensionScores = new LinkedHashMap<>();
    JSONObject dims = json.getJSONObject("dimensionScores");
    if (dims != null) {
      for (String key : dims.keySet()) {
        Integer score = dims.getInt(key);
        dimensionScores.put(HotNewsOptimizeCritiqueDimension.normalize(key), clampScore(score));
      }
    }
    List<HotNewsOptimizeCritiqueIssueView> issues = new ArrayList<>();
    JSONArray issueArr = json.getJSONArray("issues");
    if (issueArr != null) {
      for (Object row : issueArr) {
        if (!(row instanceof JSONObject obj)) {
          continue;
        }
        // 兼容旧模型误用 severity 字段
        String dimension = HotNewsOptimizeCritiqueDimension.normalize(StrUtil.firstNonBlank(
            StrUtil.trim(obj.getStr("dimension")),
            StrUtil.trim(obj.getStr("severity"))));
        issues.add(HotNewsOptimizeCritiqueIssueView.builder()
            .dimension(dimension)
            .evidence(StrUtil.trim(obj.getStr("evidence")))
            .suggestion(StrUtil.trim(obj.getStr("suggestion")))
            .build());
      }
    }
    String advice = StrUtil.trim(json.getStr("editableAdvice"));
    if (StrUtil.isBlank(advice)) {
      advice = "请根据分项问题优化叙述铺垫、语气、AI 味与证据出处，并补齐读者决策缺口。";
    }
    return HotNewsOptimizeCritiqueView.builder()
        .articleId(articleId)
        .overallScore(clampScore(json.getInt("overallScore")))
        .dimensionScores(dimensionScores)
        .issues(issues)
        .editableAdvice(advice)
        .build();
  }

  /**
   * 将分数夹到 0～100；空则 0。
   *
   * @param score 原始分
   * @return 合法分
   */
  private static int clampScore(Integer score) {
    if (score == null) {
      return 0;
    }
    return Math.max(0, Math.min(100, score));
  }
}
