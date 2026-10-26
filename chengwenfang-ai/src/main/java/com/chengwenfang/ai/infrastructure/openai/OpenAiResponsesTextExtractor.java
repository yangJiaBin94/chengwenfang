package com.chengwenfang.ai.infrastructure.openai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Responses API 文本提取，对齐 LangChain {@code _get_output_text}：
 * 优先顶层 {@code output_text}；否则只拼接 {@code type=message} 项里的 {@code output_text}，
 * 跳过 {@code reasoning} 等非消息项。
 */
public final class OpenAiResponsesTextExtractor {
  private OpenAiResponsesTextExtractor() {}

  public static String extract(OpenAiResponsesResponse parsed) {
    if (parsed == null) {
      return "";
    }
    if (StrUtil.isNotBlank(parsed.getOutputText())) {
      return parsed.getOutputText().strip();
    }
    if (CollUtil.isEmpty(parsed.getOutput())) {
      return "";
    }
    List<String> texts = new ArrayList<>();
    for (OpenAiResponsesResponse.OutputItem item : parsed.getOutput()) {
      if (item == null) {
        continue;
      }
      // 跳过 reasoning / function_call 等；无 type 时保守地只收 output_text 片段
      if (StrUtil.isNotBlank(item.getType()) && !"message".equals(item.getType())) {
        continue;
      }
      if (CollUtil.isEmpty(item.getContent())) {
        continue;
      }
      for (OpenAiResponsesResponse.ContentPart part : item.getContent()) {
        if (part == null) {
          continue;
        }
        if ("output_text".equals(part.getType()) && StrUtil.isNotBlank(part.getText())) {
          texts.add(part.getText());
        }
      }
    }
    return String.join("", texts).strip();
  }
}
