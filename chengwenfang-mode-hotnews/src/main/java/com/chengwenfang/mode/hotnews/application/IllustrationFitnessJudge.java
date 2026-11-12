package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.mode.hotnews.application.prompt.IllustrationFitnessPrompts;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil.PromptHit;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsIllustrationReuseReportView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsIllustrationSlotDecisionView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsIllustrationFitnessDecision;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 改写后插图适配裁定：合适复用，不合适去掉旧图待重生（含封面）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IllustrationFitnessJudge {
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 对照旧稿/新稿裁定插图，并产出合并后的新稿与报告。
   *
   * @param oldDraft 改写前原稿
   * @param newDraft 改写后原稿
   * @param advice   优化建议；可空
   * @return 合并结果
   */
  public MergeResult merge(String oldDraft, String newDraft, String advice) {
    List<SlotSnapshot> oldSlots = snapshotSlots(oldDraft);
    List<SlotSnapshot> newSlots = snapshotSlots(newDraft);
    Map<String, SlotSnapshot> oldByKey = indexByKey(oldSlots);
    Map<String, SlotSnapshot> oldByPrompt = indexByPrompt(oldSlots);

    List<HotNewsIllustrationSlotDecisionView> decisions = new ArrayList<>();
    List<SlotSnapshot> needAi = new ArrayList<>();
    Map<String, HotNewsIllustrationFitnessDecision> fastDecisions = new LinkedHashMap<>();
    Map<String, SlotSnapshot> matchedOldByNewKey = new LinkedHashMap<>();

    for (SlotSnapshot neu : newSlots) {
      // 优先按归一化提示词匹配旧槽，避免 inline-N 重排错挂图
      SlotSnapshot old = oldByPrompt.get(HotNewsMarkdownUtil.normalizePromptText(neu.prompt()));
      if (old == null) {
        SlotSnapshot byKey = oldByKey.get(neu.key());
        if (byKey != null
            && HotNewsMarkdownUtil.normalizePromptText(byKey.prompt())
                .equals(HotNewsMarkdownUtil.normalizePromptText(neu.prompt()))) {
          old = byKey;
        }
      }
      if (old != null) {
        matchedOldByNewKey.put(neu.key(), old);
        fastDecisions.put(neu.key(), HotNewsIllustrationFitnessDecision.REUSE);
      } else {
        needAi.add(neu);
      }
    }

    Map<String, String> aiDecisions = needAi.isEmpty()
        ? Map.of()
        : judgeWithAi(newDraft, advice, needAi, oldByKey);

    String merged = StrUtil.nullToEmpty(newDraft);
    int reused = 0;
    int regen = 0;
    // 从后往前改，避免下标漂移
    List<SlotSnapshot> working = snapshotSlots(merged);
    for (int index = working.size() - 1; index >= 0; index--) {
      SlotSnapshot neu = working.get(index);
      SlotSnapshot old = matchedOldByNewKey.get(neu.key());
      if (old == null) {
        old = oldByPrompt.get(HotNewsMarkdownUtil.normalizePromptText(neu.prompt()));
      }
      if (old == null) {
        old = oldByKey.get(neu.key());
      }
      HotNewsIllustrationFitnessDecision decision = fastDecisions.get(neu.key());
      if (decision == null) {
        decision = HotNewsIllustrationFitnessDecision.from(aiDecisions.get(neu.key()));
      }
      String reason;
      if (decision == HotNewsIllustrationFitnessDecision.REUSE) {
        reused++;
        reason = "提示词或画面仍贴合优化后正文，复用原图";
        if (old != null && StrUtil.isNotBlank(old.imageMarkdown())) {
          PromptHit hit = findHitByPrompt(merged, neu.prompt());
          if (hit != null) {
            // 先去掉错误/缺失的图，再挂回匹配到的旧图
            merged = HotNewsMarkdownUtil.stripImageAfterPrompt(merged, hit);
            merged = HotNewsMarkdownUtil.attachImageAfterPrompt(
                merged, neu.prompt(), old.imageMarkdown());
          }
        }
      } else {
        regen++;
        decision = HotNewsIllustrationFitnessDecision.REGENERATE;
        reason = StrUtil.blankToDefault(
            aiDecisions.get(neu.key() + ":reason"),
            "优化后内容或提示词已变化，需重新生成插图");
        PromptHit hit = findHitByPrompt(merged, neu.prompt());
        if (hit != null) {
          merged = HotNewsMarkdownUtil.stripImageAfterPrompt(merged, hit);
        }
      }
      decisions.add(0, HotNewsIllustrationSlotDecisionView.builder()
          .sequenceNo(neu.sequenceNo())
          .cover(neu.cover())
          .marker(neu.marker())
          .decision(decision.getValue())
          .reason(reason)
          .build());
    }

    HotNewsIllustrationReuseReportView report = HotNewsIllustrationReuseReportView.builder()
        .reusedCount(reused)
        .regenRequiredCount(regen)
        .slots(decisions)
        .build();
    log.info("插图适配裁定完成 reused={} regen={} slotCount={}", reused, regen, decisions.size());
    return new MergeResult(merged, report);
  }

  /**
   * 对无法快速匹配的槽位调用 AI 裁定。
   *
   * @param newDraft 新稿
   * @param advice   优化建议
   * @param slots    待裁定槽位
   * @param oldByKey 旧槽索引
   * @return key → decision / key:reason → 理由
   */
  private Map<String, String> judgeWithAi(
      String newDraft,
      String advice,
      List<SlotSnapshot> slots,
      Map<String, SlotSnapshot> oldByKey) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("advice", StrUtil.nullToEmpty(advice));
    payload.put("articleExcerpt", truncate(newDraft, 6000));
    List<Map<String, Object>> slotPayload = new ArrayList<>();
    for (SlotSnapshot slot : slots) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", slot.key());
      row.put("cover", slot.cover());
      row.put("marker", slot.marker());
      row.put("newPrompt", truncate(slot.prompt(), 500));
      SlotSnapshot old = oldByKey.get(slot.key());
      row.put("oldPrompt", old == null ? "" : truncate(old.prompt(), 500));
      row.put("hadImage", old != null && StrUtil.isNotBlank(old.imageMarkdown()));
      slotPayload.add(row);
    }
    payload.put("slots", slotPayload);
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(IllustrationFitnessPrompts.SYSTEM)
          .prompt(JSONUtil.toJsonStr(payload))
          .scene(ModelCallScenes.HOT_NEWS_ILLUSTRATION_FITNESS)
          .timeoutSeconds(90)
          .build());
      return parseAiDecisions(result == null ? null : result.getText());
    } catch (Exception exception) {
      log.warn("插图适配 AI 裁定失败，默认 regenerate err={}", exception.getMessage());
      Map<String, String> fallback = new LinkedHashMap<>();
      for (SlotSnapshot slot : slots) {
        fallback.put(slot.key(), HotNewsIllustrationFitnessDecision.REGENERATE.getValue());
      }
      return fallback;
    }
  }

  /**
   * 解析 AI 返回的 slots 决策 JSON。
   *
   * @param raw 模型原文
   * @return key → decision；另含 key:reason
   */
  private static Map<String, String> parseAiDecisions(String raw) {
    Map<String, String> out = new LinkedHashMap<>();
    JSONObject json = LlmJsonSupport.parseJsonObject(raw);
    if (json == null) {
      return out;
    }
    JSONArray arr = json.getJSONArray("slots");
    if (arr == null) {
      return out;
    }
    for (Object row : arr) {
      if (!(row instanceof JSONObject obj)) {
        continue;
      }
      String key = StrUtil.trim(obj.getStr("key"));
      if (StrUtil.isBlank(key)) {
        continue;
      }
      HotNewsIllustrationFitnessDecision decision =
          HotNewsIllustrationFitnessDecision.from(obj.getStr("decision"));
      out.put(key, decision.getValue());
      String reason = StrUtil.trim(obj.getStr("reason"));
      if (StrUtil.isNotBlank(reason)) {
        out.put(key + ":reason", reason);
      }
    }
    return out;
  }

  /**
   * 从原稿抽取插图槽位快照。
   *
   * @param draft 原稿 Markdown
   * @return 槽位列表
   */
  private static List<SlotSnapshot> snapshotSlots(String draft) {
    List<SlotSnapshot> out = new ArrayList<>();
    List<PromptHit> prompts = HotNewsMarkdownUtil.findPrompts(draft);
    int inlineSeq = 0;
    for (PromptHit hit : prompts) {
      boolean cover = HotNewsMarkdownUtil.isCoverPrompt(hit.marker(), hit.prompt());
      int sequenceNo = cover ? 0 : ++inlineSeq;
      String image = HotNewsMarkdownUtil.imageMarkdownAfter(draft, hit.end());
      out.add(new SlotSnapshot(
          slotKey(cover, sequenceNo),
          cover,
          sequenceNo,
          StrUtil.trim(hit.marker()),
          hit.prompt(),
          image));
    }
    return out;
  }

  /**
   * 按槽位 key 建索引。
   *
   * @param slots 槽位
   * @return key → 快照
   */
  private static Map<String, SlotSnapshot> indexByKey(List<SlotSnapshot> slots) {
    Map<String, SlotSnapshot> map = new LinkedHashMap<>();
    for (SlotSnapshot slot : slots) {
      map.put(slot.key(), slot);
    }
    return map;
  }

  /**
   * 按归一化提示词建索引（首次出现优先）。
   *
   * @param slots 槽位
   * @return 归一化提示词 → 快照
   */
  private static Map<String, SlotSnapshot> indexByPrompt(List<SlotSnapshot> slots) {
    Map<String, SlotSnapshot> map = new LinkedHashMap<>();
    for (SlotSnapshot slot : slots) {
      String normalized = HotNewsMarkdownUtil.normalizePromptText(slot.prompt());
      if (StrUtil.isNotBlank(normalized) && !map.containsKey(normalized)) {
        map.put(normalized, slot);
      }
    }
    return map;
  }

  /**
   * 生成槽位稳定键。
   *
   * @param cover      是否封面
   * @param sequenceNo 正文序号
   * @return cover 或 inline-N
   */
  private static String slotKey(boolean cover, int sequenceNo) {
    return cover ? "cover" : "inline-" + sequenceNo;
  }

  /**
   * 截断过长文本。
   *
   * @param text     原文
   * @param maxChars 最大字符数
   * @return 截断结果
   */
  private static String truncate(String text, int maxChars) {
    String value = StrUtil.nullToEmpty(text);
    if (value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars);
  }

  /**
   * 按提示词定位当前稿中的 PromptHit。
   *
   * @param draft  原稿
   * @param prompt 提示词
   * @return 命中；未找到为 null
   */
  private static PromptHit findHitByPrompt(String draft, String prompt) {
    String target = HotNewsMarkdownUtil.normalizePromptText(prompt);
    for (PromptHit hit : HotNewsMarkdownUtil.findPrompts(draft)) {
      if (HotNewsMarkdownUtil.normalizePromptText(hit.prompt()).equals(target)) {
        return hit;
      }
    }
    return null;
  }

  /**
   * 插图适配合并结果。
   */
  @Data
  @AllArgsConstructor
  public static class MergeResult {
    /** 合并后原稿。 */
    private String draft;
    /** 复用/重生报告。 */
    private HotNewsIllustrationReuseReportView report;
  }

  /**
   * 单槽位快照。
   *
   * @param key           稳定键
   * @param cover         是否封面
   * @param sequenceNo    序号
   * @param marker        位置标记
   * @param prompt        提示词
   * @param imageMarkdown 紧随图片 Markdown；可空
   */
  private record SlotSnapshot(
      String key,
      boolean cover,
      int sequenceNo,
      String marker,
      String prompt,
      String imageMarkdown) {}
}
