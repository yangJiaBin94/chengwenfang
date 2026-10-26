package com.chengwenfang.app.golden.stub;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条 AI fixture 规则：按特征串匹配请求并返回固定助手内容。
 */
public final class AiFixtureRule {
  /** 场景名（与附录 A / ModelCallScenes 对齐）。 */
  private String scene;
  /** 端点：chat / responses / images。 */
  private String endpoint = "chat";
  /** 优先级，数值越小越先匹配（与 WireMock atPriority 一致）。 */
  private int priority = 100;
  /** 请求体需包含的任一特征串；images 可为空。 */
  private List<String> matchAny = new ArrayList<>();
  /** 可选：限制 URL 路径（如 {@code /v1/images/generations}）。 */
  private String matchPath;
  /** 助手文本内容（chat/responses）；与 {@link #contentJson} 二选一。 */
  private String content;
  /** 助手 JSON 内容对象序列化结果；优先于 content。 */
  private String contentJson;
  /** 图片接口的 b64_json。 */
  private String b64Json;
  /**
   * 解析校验键：keyword_plan / relevance / skeleton / … / text / image。
   * 供 {@code AiFixtureParseTest} 选用对应解析器。
   */
  private String parser = "text";
  /** 为 true 时助手正文强制为空（用于终态失败通知场景）。 */
  private boolean forceEmpty;

  /**
   * @return 场景名
   */
  public String scene() {
    return scene;
  }

  /**
   * @param scene 场景名
   */
  public void setScene(String scene) {
    this.scene = scene;
  }

  /**
   * @return 端点
   */
  public String endpoint() {
    return endpoint;
  }

  /**
   * @param endpoint 端点
   */
  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  /**
   * @return 优先级
   */
  public int priority() {
    return priority;
  }

  /**
   * @param priority 优先级
   */
  public void setPriority(int priority) {
    this.priority = priority;
  }

  /**
   * @return 特征串列表
   */
  public List<String> matchAny() {
    return matchAny;
  }

  /**
   * @param matchAny 特征串
   */
  public void setMatchAny(List<String> matchAny) {
    this.matchAny = matchAny == null ? new ArrayList<>() : new ArrayList<>(matchAny);
  }

  /**
   * @return 路径限制；可空
   */
  public String matchPath() {
    return matchPath;
  }

  /**
   * @param matchPath 路径
   */
  public void setMatchPath(String matchPath) {
    this.matchPath = matchPath;
  }

  /**
   * @return 文本内容
   */
  public String content() {
    return content;
  }

  /**
   * @param content 文本
   */
  public void setContent(String content) {
    this.content = content;
  }

  /**
   * @return JSON 内容串
   */
  public String contentJson() {
    return contentJson;
  }

  /**
   * @param contentJson JSON 串
   */
  public void setContentJson(String contentJson) {
    this.contentJson = contentJson;
  }

  /**
   * @return 图片 b64
   */
  public String b64Json() {
    return b64Json;
  }

  /**
   * @param b64Json 图片 b64
   */
  public void setB64Json(String b64Json) {
    this.b64Json = b64Json;
  }

  /**
   * @return 解析器键
   */
  public String parser() {
    return parser;
  }

  /**
   * @param parser 解析器键
   */
  public void setParser(String parser) {
    this.parser = parser;
  }

  /**
   * @return 是否强制空正文
   */
  public boolean forceEmpty() {
    return forceEmpty;
  }

  /**
   * @param forceEmpty 是否强制空正文
   */
  public void setForceEmpty(boolean forceEmpty) {
    this.forceEmpty = forceEmpty;
  }

  /**
   * 供 WireMock 返回的助手正文（chat/responses）。
   *
   * @return 正文；{@link #forceEmpty} 时为空串
   */
  public String assistantText() {
    if (forceEmpty) {
      return "";
    }
    if (contentJson != null && !contentJson.isBlank()) {
      return contentJson;
    }
    return content == null ? "" : content;
  }

  @Override
  public String toString() {
    return scene == null ? "fixture" : scene;
  }
}
