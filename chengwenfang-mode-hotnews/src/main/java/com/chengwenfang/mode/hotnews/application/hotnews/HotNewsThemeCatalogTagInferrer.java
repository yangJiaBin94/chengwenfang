package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.dto.view.ThemeCoreIntentView;
import com.chengwenfang.sourcing.facade.constant.SourceChannelCatalogTags;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从热点主题问卷推断沉淀池场景标签（关键词启发式，无额外 LLM）。
 */
public final class HotNewsThemeCatalogTagInferrer {
  private HotNewsThemeCatalogTagInferrer() {}

  /**
   * AI 场景关键词（英文按词边界匹配，避免 daily/method 等误伤；中文仍用包含）。
   */
  private static final String[] AI_KEYWORDS = {
      "ai", "aigc", "llm", "gpt", "chatgpt", "gpt-4", "gpt4", "gpt-5", "gpt5",
      "claude", "gemini", "openai", "anthropic", "agent",
      "人工智能", "大模型", "生成式", "智能体", "机器学习", "深度学习"
  };

  /** Web3 场景关键词。 */
  private static final String[] WEB3_KEYWORDS = {
      "web3", "defi", "nft", "dao", "ethereum", "bitcoin", "btc", "eth", "crypto",
      "区块链", "加密货币", "链上", "比特币", "以太坊"
  };

  /**
   * 推断主题场景标签。
   *
   * @param briefing 问卷 map；可空
   * @return 去重保序标签；无命中则空列表
   */
  public static List<String> inferTags(Map<String, Object> briefing) {
    String blob = buildBlob(briefing).toLowerCase(Locale.ROOT);
    Set<String> tags = new LinkedHashSet<>();
    if (containsAny(blob, AI_KEYWORDS)) {
      tags.add(SourceChannelCatalogTags.AI);
    }
    if (containsAny(blob, WEB3_KEYWORDS)) {
      tags.add(SourceChannelCatalogTags.WEB3);
    }
    return new ArrayList<>(tags);
  }

  /**
   * 拼接合命名、领域与意图文本。
   *
   * @param briefing 问卷
   * @return 小写前原文
   */
  private static String buildBlob(Map<String, Object> briefing) {
    if (briefing == null || briefing.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    builder.append(HotNewsThemeBriefingSupport.themeName(briefing)).append(' ');
    Map<String, Object> naming = nested(briefing, "naming");
    append(builder, naming.get("description"));
    append(builder, naming.get("domain"));
    append(builder, naming.get("domain_custom"));
    ThemeCoreIntentView intent = HotNewsThemeBriefingSupport.coreIntent(briefing);
    append(builder, intent.getSummary());
    append(builder, intent.getSuccessLooksLike());
    if (CollUtil.isNotEmpty(intent.getMaterialWants())) {
      for (String want : intent.getMaterialWants()) {
        append(builder, want);
      }
    }
    return builder.toString();
  }

  /**
   * 读取嵌套 map。
   *
   * @param root 根
   * @param key  键
   * @return 子 map；无则空
   */
  private static Map<String, Object> nested(Map<String, Object> root, String key) {
    Object value = root.get(key);
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return typed;
    }
    return Map.of();
  }

  /**
   * 追加非空文本到拼装缓冲。
   *
   * @param builder 缓冲
   * @param value   值
   */
  private static void append(StringBuilder builder, Object value) {
    String text = StrUtil.trim(StrUtil.nullToEmpty(value == null ? null : String.valueOf(value)));
    if (StrUtil.isNotBlank(text)) {
      builder.append(text).append(' ');
    }
  }

  /**
   * 是否命中任一关键词：ASCII 词用词边界；含非 ASCII 的用包含匹配。
   *
   * @param blob     已小写文本
   * @param keywords 关键词
   * @return 命中则为 true
   */
  private static boolean containsAny(String blob, String[] keywords) {
    for (String keyword : keywords) {
      if (StrUtil.isBlank(keyword)) {
        continue;
      }
      String needle = keyword.toLowerCase(Locale.ROOT);
      if (isAsciiWord(needle)) {
        Pattern pattern = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])");
        if (pattern.matcher(blob).find()) {
          return true;
        }
      } else if (blob.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 是否为纯 ASCII 字母数字词（可用词边界）。
   *
   * @param text 文本
   * @return 纯 ASCII 词则为 true
   */
  private static boolean isAsciiWord(String text) {
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character > 127) {
        return false;
      }
    }
    return true;
  }
}
