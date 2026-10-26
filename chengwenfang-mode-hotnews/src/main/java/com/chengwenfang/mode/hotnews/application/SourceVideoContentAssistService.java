package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.mode.hotnews.application.prompt.SourceVideoPrompts;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.sourcing.facade.enums.SourceContentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 雷达视频解析后处理：事实速览总结 + 营销意图判定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceVideoContentAssistService {
  private final AiTextFacade ai;

  /**
   * 将口播或画面摘要整理为事实速览。
   *
   * @param title   标题
   * @param rawText 口播原文；可空
   * @param summary 已有摘要（如画面总结）；可空
   * @return 事实速览文本
   */
  public String buildFactBrief(String title, String rawText, String summary) {
    String body = StrUtil.blankToDefault(rawText, summary);
    if (StrUtil.isBlank(body)) {
      return "无可引用事实";
    }
    String clipped = body.length() > 10000 ? body.substring(0, 10000) : body;
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_SOURCE_VIDEO_FACT_BRIEF)
          .systemPrompt(SourceVideoPrompts.FACT_SYSTEM)
          .prompt("标题：" + StrUtil.nullToEmpty(title) + "\n\n材料：\n" + clipped)
          .timeoutSeconds(90)
          .build());
      String text = result == null ? null : result.getText();
      if (StrUtil.isBlank(text)) {
        return clipSummary(ChineseTextNormalizer.toSimplified(clipped));
      }
      return clipSummary(ChineseTextNormalizer.toSimplified(text.trim()));
    } catch (Exception exception) {
      log.warn("事实速览生成失败 title={} err={}", title, exception.getMessage());
      return clipSummary(ChineseTextNormalizer.toSimplified(clipped));
    }
  }

  /**
   * 判定内容意图。
   *
   * @param title     标题
   * @param factBrief 事实速览
   * @param rawText   口播原文；可空
   * @return 知识向或营销向
   */
  public SourceContentIntent judgeIntent(String title, String factBrief, String rawText) {
    String material = StrUtil.blankToDefault(rawText, factBrief);
    if (StrUtil.isBlank(material) && StrUtil.isBlank(title)) {
      return SourceContentIntent.KNOWLEDGE;
    }
    // 标题硬规则：明显口播带货直接判营销
    if (looksLikeHardSellTitle(title)) {
      return SourceContentIntent.MARKETING;
    }
    String clipped = material.length() > 8000 ? material.substring(0, 8000) : material;
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_SOURCE_VIDEO_PROMO_JUDGE)
          .systemPrompt(SourceVideoPrompts.PROMO_SYSTEM)
          .prompt("标题：" + StrUtil.nullToEmpty(title)
              + "\n事实速览：" + StrUtil.nullToEmpty(factBrief)
              + "\n原文摘录：\n" + clipped)
          .timeoutSeconds(60)
          .build());
      String text = result == null ? null : result.getText();
      JSONObject json = extractJson(text);
      if (json != null) {
        return SourceContentIntent.from(json.getStr("intent"));
      }
    } catch (Exception exception) {
      log.warn("营销判定失败 title={} err={}", title, exception.getMessage());
    }
    // 模型失败时：标题含软广词则偏营销
    return looksLikeHardSellTitle(title)
        ? SourceContentIntent.MARKETING
        : SourceContentIntent.KNOWLEDGE;
  }

  /**
   * 标题是否像口播带货（与 MarketingPageFilter 互补的本地兜底）。
   *
   * @param title 标题
   * @return true 表示疑似硬推销
   */
  public static boolean looksLikeHardSellTitle(String title) {
    return com.chengwenfang.sourcing.facade.SourceVideoTitleFilter.looksLikeHardSellTitle(title);
  }

  private static String clipSummary(String text) {
    String value = StrUtil.nullToEmpty(text).trim();
    if (value.length() > 4000) {
      return value.substring(0, 4000);
    }
    return value;
  }

  private static JSONObject extractJson(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    try {
      return JSONUtil.parseObj(raw.substring(start, end + 1));
    } catch (Exception ignored) {
      return null;
    }
  }
}
