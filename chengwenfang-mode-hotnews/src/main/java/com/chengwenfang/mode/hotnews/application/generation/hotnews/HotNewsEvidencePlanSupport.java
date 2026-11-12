package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsAttachEvidencePlan;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsEvidenceShotSpec;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * 解析并校验 AI 佐证截屏计划：URL 必须在参考列表内，张数截断至上限。
 */
@UtilityClass
public class HotNewsEvidencePlanSupport {

  /**
   * 从模型原文解析计划；失败返回空 shots。
   *
   * @param raw               模型输出
   * @param allowedReferenceUrls 参考资料允许的 URL
   * @return 校验后的计划（永不为 null）
   */
  public static HotNewsAttachEvidencePlan parseAndSanitize(
      String raw, List<String> allowedReferenceUrls) {
    Set<String> allowed = normalizeAllowedUrls(allowedReferenceUrls);
    HotNewsAttachEvidencePlan parsed = LlmJsonSupport.parseObject(raw, HotNewsAttachEvidencePlan.class);
    if (parsed == null) {
      parsed = parseShotsArrayFallback(raw);
    }
    List<HotNewsEvidenceShotSpec> sanitized = new ArrayList<>();
    if (parsed != null && CollUtil.isNotEmpty(parsed.getShots())) {
      Set<String> seen = new LinkedHashSet<>();
      for (HotNewsEvidenceShotSpec shot : parsed.getShots()) {
        if (shot == null || StrUtil.isBlank(shot.getUrl())) {
          continue;
        }
        String url = shot.getUrl().trim();
        if (!isAllowedUrl(url, allowed)) {
          continue;
        }
        String dedupeKey = url.toLowerCase(Locale.ROOT) + "\n"
            + StrUtil.nullToEmpty(shot.getFocusHint()).trim().toLowerCase(Locale.ROOT);
        if (!seen.add(dedupeKey)) {
          continue;
        }
        sanitized.add(HotNewsEvidenceShotSpec.builder()
            .url(url)
            .focusHint(StrUtil.trim(shot.getFocusHint()))
            .caption(HotNewsMarkdownUtil.normalizeEvidenceCaption(shot.getCaption()))
            .insertAfter(StrUtil.trim(shot.getInsertAfter()))
            .build());
        if (sanitized.size() >= HotNewsEvidenceKeys.MAX_SHOTS) {
          break;
        }
      }
    }
    return HotNewsAttachEvidencePlan.builder().shots(sanitized).build();
  }

  /**
   * 从参考资料 JSON 抽取 URL 列表。
   *
   * @param referencesJson LoadContext 写入的 referencesJson
   * @return URL 列表
   */
  public static List<String> extractAllowedUrls(String referencesJson) {
    List<String> urls = new ArrayList<>();
    if (StrUtil.isBlank(referencesJson) || !JSONUtil.isTypeJSON(referencesJson)) {
      return urls;
    }
    Object root = JSONUtil.parse(referencesJson);
    if (root instanceof JSONArray array) {
      for (Object item : array) {
        collectUrl(item, urls);
      }
      return urls;
    }
    if (root instanceof JSONObject object) {
      Object nested = object.get("references");
      if (nested instanceof JSONArray array) {
        for (Object item : array) {
          collectUrl(item, urls);
        }
      }
    }
    return urls;
  }

  private static void collectUrl(Object item, List<String> urls) {
    if (item instanceof JSONObject object) {
      String url = StrUtil.trim(object.getStr("url"));
      if (StrUtil.isNotBlank(url)) {
        urls.add(url);
      }
    }
  }

  private static HotNewsAttachEvidencePlan parseShotsArrayFallback(String raw) {
    JSONObject json = LlmJsonSupport.parseJsonObject(raw);
    if (json == null) {
      return HotNewsAttachEvidencePlan.builder().shots(List.of()).build();
    }
    JSONArray shots = json.getJSONArray("shots");
    if (shots == null) {
      return HotNewsAttachEvidencePlan.builder().shots(List.of()).build();
    }
    List<HotNewsEvidenceShotSpec> list = new ArrayList<>();
    for (Object item : shots) {
      if (item instanceof JSONObject object) {
        list.add(HotNewsEvidenceShotSpec.builder()
            .url(object.getStr("url"))
            .focusHint(object.getStr("focusHint"))
            .caption(object.getStr("caption"))
            .insertAfter(object.getStr("insertAfter"))
            .build());
      }
    }
    return HotNewsAttachEvidencePlan.builder().shots(list).build();
  }

  private static Set<String> normalizeAllowedUrls(List<String> allowedReferenceUrls) {
    Set<String> allowed = new LinkedHashSet<>();
    if (CollUtil.isEmpty(allowedReferenceUrls)) {
      return allowed;
    }
    for (String url : allowedReferenceUrls) {
      if (StrUtil.isNotBlank(url)) {
        allowed.add(normalizeUrl(url));
      }
    }
    return allowed;
  }

  private static boolean isAllowedUrl(String url, Set<String> allowed) {
    if (allowed.isEmpty()) {
      return false;
    }
    String normalized = normalizeUrl(url);
    if (allowed.contains(normalized)) {
      return true;
    }
    // 允许仅差尾部斜杠
    String alt = normalized.endsWith("/")
        ? normalized.substring(0, normalized.length() - 1)
        : normalized + "/";
    return allowed.contains(alt);
  }

  private static String normalizeUrl(String url) {
    return StrUtil.trim(url).toLowerCase(Locale.ROOT);
  }
}
