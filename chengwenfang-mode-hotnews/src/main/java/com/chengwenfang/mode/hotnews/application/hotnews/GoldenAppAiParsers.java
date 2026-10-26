package com.chengwenfang.mode.hotnews.application.hotnews;

import java.util.List;

/**
 * 黄金 fixture 解析桥接：暴露同包 package-private 解析方法。
 */
public final class GoldenAppAiParsers {
  private GoldenAppAiParsers() {}

  /**
   * @param text 助手文本
   * @return 渠道候选
   */
  public static List<ChannelSuggestionParser.AiChannelCandidate> parseChannels(String text) {
    return ChannelSuggestionParser.parseCandidates(text);
  }
}
