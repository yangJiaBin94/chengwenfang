package com.chengwenfang.ai.facade.constant;

/**
 * 联网搜索相关常量（DeepSeek 为首发实现）。
 */
public final class AiWebSearchConstants {
  private AiWebSearchConstants() {}

  /** DeepSeek 联网工具要求的模型名。 */
  public static final String DEEPSEEK_FLASH_MODEL = "deepseek-flash";

  /** DeepSeek Responses/Chat 联网搜索工具 type。 */
  public static final String DEEPSEEK_WEB_SEARCH_TOOL = "deepseek_web_search";

  /** DeepSeek 阅读链接工具 type。 */
  public static final String DEEPSEEK_READ_URL_TOOL = "deepseek_read_url";
}
