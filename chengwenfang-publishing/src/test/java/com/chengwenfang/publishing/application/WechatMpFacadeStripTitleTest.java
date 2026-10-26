package com.chengwenfang.publishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.publishing.application.publish.WechatMpChannelPublisher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** 公众号草稿推送：去重标题、抽取摘要并移出正文。 */
class WechatMpFacadeStripTitleTest {

  @Test
  void stripLeadingTitleHeading_removesMatchingH1() {
    Document document =
        Jsoup.parseBodyFragment(
            """
            <section>
              <h1>RAG 到底怎么工作？把索引、检索、生成三步拆开看</h1>
              <blockquote><p>系列：AI RAG</p></blockquote>
              <p>正文段落</p>
            </section>
            """);
    WechatMpChannelPublisher.stripLeadingTitleHeading(
        document, "RAG 到底怎么工作？把索引、检索、生成三步拆开看");
    assertThat(document.select("h1")).isEmpty();
    assertThat(document.text()).contains("系列：AI RAG").contains("正文段落");
  }

  @Test
  void stripLeadingTitleHeading_keepsUnrelatedH1() {
    Document document =
        Jsoup.parseBodyFragment(
            """
            <section>
              <h1>完全不同的章节标题</h1>
              <p>正文</p>
            </section>
            """);
    WechatMpChannelPublisher.stripLeadingTitleHeading(document, "RAG 到底怎么工作");
    assertThat(document.selectFirst("h1").text()).isEqualTo("完全不同的章节标题");
  }

  @Test
  void inferDigest_readsSummaryLineFromMarkdown() {
    String markdown =
        """
        # 标题

        > 系列：AI RAG｜难度：零基础｜预计阅读：5 分钟
        >
        > 本课覆盖：拆解RAG主链路
        >
        > 摘要：本课围绕RAG三大阶段：索引、检索与生成，讲清拆解RAG主链路，理解离线索引、在线检索和生成集成的关系。。读完后可以继续学习向量与嵌入：从文本到语义相似度。

        正文开始
        """;
    String digest = WechatMpChannelPublisher.inferDigest(markdown, null);
    assertThat(digest)
        .isEqualTo(
            "本课围绕RAG三大阶段：索引、检索与生成，讲清拆解RAG主链路，理解离线索引、在线检索和生成集成的关系。。读完后可以继续学习向量与嵌入：从文本到语义相似度。");
    assertThat(digest).doesNotContain("本课覆盖").doesNotStartWith("摘要");
  }

  @Test
  void stripSummaryFromBody_removesDigestParagraphKeepsCoverage() {
    Document document =
        Jsoup.parseBodyFragment(
            """
            <section>
              <blockquote>
                <p>系列：AI RAG｜难度：零基础｜预计阅读：5 分钟</p>
                <p>本课覆盖：拆解RAG主链路</p>
                <p>摘要：本课围绕RAG三大阶段：索引、检索与生成，讲清拆解RAG主链路。</p>
              </blockquote>
              <p>正文段落</p>
            </section>
            """);
    WechatMpChannelPublisher.stripSummaryFromBody(document);
    assertThat(document.text()).contains("本课覆盖：拆解RAG主链路").contains("正文段落");
    assertThat(document.text()).doesNotContain("摘要：").doesNotContain("本课围绕RAG三大阶段");
  }
}
