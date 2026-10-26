package com.workbench.content.facade.dto;

/**
 * 产出身份投影：按 {@code work_outputs.id} 反查作品与模式，供统一文档路由分派。
 */
public final class WorkOutputRef {
  /** 产出主键（原 documentId）。 */
  private final String outputId;
  /** 作品主键。 */
  private final String workId;
  /** 是否热点模式。 */
  private final boolean hotNews;
  /** 内容轨 kind（热点 draft/wechat；课程 course_draft/wechat_article）。 */
  private final String kind;
  /** 渠道编码。 */
  private final String channelCode;

  /**
   * @param outputId    产出 id
   * @param workId      作品 id
   * @param hotNews     是否热点
   * @param kind        内容轨 kind
   * @param channelCode 渠道编码
   */
  public WorkOutputRef(
      String outputId,
      String workId,
      boolean hotNews,
      String kind,
      String channelCode) {
    this.outputId = outputId;
    this.workId = workId;
    this.hotNews = hotNews;
    this.kind = kind;
    this.channelCode = channelCode;
  }

  /**
   * 路由用的文章/作品 id：热点为 works.id，课程为产出 id。
   *
   * @return 文章 id
   */
  public String resolveArticleId() {
    return hotNews ? workId : outputId;
  }

  /** @return 产出 id */
  public String getOutputId() {
    return outputId;
  }

  /** @return 作品 id */
  public String getWorkId() {
    return workId;
  }

  /** @return 是否热点 */
  public boolean isHotNews() {
    return hotNews;
  }

  /** @return 是否课程 */
  public boolean isCourse() {
    return !hotNews;
  }

  /** @return kind */
  public String getKind() {
    return kind;
  }

  /** @return 渠道编码 */
  public String getChannelCode() {
    return channelCode;
  }
}
