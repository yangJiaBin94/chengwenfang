package com.workbench.job.facade.dto.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.common.FlexiblePayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 任务载荷类型化：typed 对象与原 LinkedHashMap put 路径经 {@link JobPayloads#toJson} 逐字相等。
 */
class JobPayloadSerializationTest {

  @Test
  void okJobOutput_matches_map_of_ok_true() {
    Map<String, Object> original = new LinkedHashMap<>();
    original.put("ok", true);
    assertEquals(JSONUtil.toJsonStr(original), JobPayloads.toJson(JobPayloads.okTrue()));
  }

  @Test
  void flexiblePayload_is_flattened_not_nested_fields() {
    FlexiblePayload payload = FlexiblePayload.empty().with("themeId", "t1").with("hours", 24);
    Map<String, Object> original = new LinkedHashMap<>();
    original.put("themeId", "t1");
    original.put("hours", 24);
    assertEquals(JSONUtil.toJsonStr(original), JobPayloads.toJson(payload));
  }

  @Test
  void hotNewsTopicResearch_input_output() {
    Map<String, Object> originalIn = new LinkedHashMap<>();
    originalIn.put("topicId", "topic-1");
    originalIn.put("researchRunId", "run-1");
    HotNewsTopicResearchJobInput typedIn = HotNewsTopicResearchJobInput.builder()
        .topicId("topic-1")
        .researchRunId("run-1")
        .build();
    assertEquals(JSONUtil.toJsonStr(originalIn), JobPayloads.toJson(typedIn));

    Map<String, Object> originalOut = new LinkedHashMap<>();
    originalOut.put("researchRunId", "run-1");
    originalOut.put("topicId", "topic-1");
    originalOut.put("itemCount", 3);
    originalOut.put("conflictCount", 0);
    originalOut.put("status", "succeeded");
    originalOut.put("briefId", "brief-1");
    HotNewsTopicResearchJobOutput typedOut = HotNewsTopicResearchJobOutput.builder()
        .researchRunId("run-1")
        .topicId("topic-1")
        .itemCount(3)
        .conflictCount(0)
        .status("succeeded")
        .briefId("brief-1")
        .build();
    assertEquals(JSONUtil.toJsonStr(originalOut), JobPayloads.toJson(typedOut));
  }

  @Test
  void hotNewsArticleRewrite_input_and_map_subclass_output() {
    Map<String, Object> originalIn = new LinkedHashMap<>();
    originalIn.put("articleId", "a1");
    originalIn.put("instruction", "改写");
    assertEquals(
        JSONUtil.toJsonStr(originalIn),
        JobPayloads.toJson(HotNewsArticleRewriteJobInput.builder()
            .articleId("a1")
            .instruction("改写")
            .build()));

    Map<String, Object> viewMap = new LinkedHashMap<>();
    viewMap.put("id", "a1");
    viewMap.put("title", "标题");
    Map<String, Object> originalOut = new LinkedHashMap<>(viewMap);
    originalOut.put("instruction", "改写");
    originalOut.put("rewritten", true);
    HotNewsArticleRewriteJobOutput typedOut = new HotNewsArticleRewriteJobOutput();
    typedOut.putAll(viewMap);
    typedOut.put("instruction", "改写");
    typedOut.put("rewritten", true);
    assertEquals(JSONUtil.toJsonStr(originalOut), JobPayloads.toJson(typedOut));
  }

  @Test
  void hotNewsVideoParse_input_conditional_and_outputs() {
    Map<String, Object> originalIn = new LinkedHashMap<>();
    originalIn.put("topicId", "t1");
    originalIn.put("itemIds", List.of("i1", "i2"));
    originalIn.put("fromStep", "fetch_subtitle");
    assertEquals(
        JSONUtil.toJsonStr(originalIn),
        JobPayloads.toJson(HotNewsVideoParseJobInput.builder()
            .topicId("t1")
            .itemIds(List.of("i1", "i2"))
            .fromStep("fetch_subtitle")
            .build()));

    Map<String, Object> topicOnly = new LinkedHashMap<>();
    topicOnly.put("topicId", "t1");
    assertEquals(
        JSONUtil.toJsonStr(topicOnly),
        JobPayloads.toJson(HotNewsVideoParseJobInput.builder().topicId("t1").build()));

    Map<String, Object> emptyBatch = new LinkedHashMap<>();
    emptyBatch.put("ok", true);
    emptyBatch.put("parsed", 0);
    emptyBatch.put("failed", 0);
    assertEquals(
        JSONUtil.toJsonStr(emptyBatch),
        JobPayloads.toJson(HotNewsVideoParseJobOutput.builder()
            .ok(true).parsed(0).failed(0).build()));
    Map<String, Object> withTotal = new LinkedHashMap<>();
    withTotal.put("ok", true);
    withTotal.put("parsed", 2);
    withTotal.put("failed", 1);
    withTotal.put("total", 3);
    assertEquals(
        JSONUtil.toJsonStr(withTotal),
        JobPayloads.toJson(HotNewsVideoParseJobOutput.builder()
            .ok(true).parsed(2).failed(1).total(3).build()));
  }

  @Test
  void hotNewsSourceVideoParse_output_variants() {
    Map<String, Object> discardedPath = new LinkedHashMap<>();
    discardedPath.put("ok", true);
    discardedPath.put("parsed", 0);
    discardedPath.put("discarded", 2);
    assertEquals(
        JSONUtil.toJsonStr(discardedPath),
        JobPayloads.toJson(HotNewsSourceVideoParseJobOutput.builder()
            .ok(true).parsed(0).discarded(2).build()));
    Map<String, Object> readyPath = new LinkedHashMap<>();
    readyPath.put("ok", true);
    readyPath.put("ready", 1);
    readyPath.put("discarded", 2);
    readyPath.put("total", 3);
    assertEquals(
        JSONUtil.toJsonStr(readyPath),
        JobPayloads.toJson(HotNewsSourceVideoParseJobOutput.builder()
            .ok(true).ready(1).discarded(2).total(3).build()));
  }

  @Test
  void themeCurriculum_and_articleRepair_stub() {
    Map<String, Object> curriculumIn = new LinkedHashMap<>();
    curriculumIn.put("themeId", "th1");
    assertEquals(
        JSONUtil.toJsonStr(curriculumIn),
        JobPayloads.toJson(ThemeCurriculumGenerationJobInput.builder().themeId("th1").build()));

    Map<String, Object> curriculumOut = new LinkedHashMap<>();
    curriculumOut.put("ok", true);
    curriculumOut.put("themeId", "th1");
    curriculumOut.put("seriesCount", 4);
    assertEquals(
        JSONUtil.toJsonStr(curriculumOut),
        JobPayloads.toJson(ThemeCurriculumGenerationJobOutput.builder()
            .ok(true).themeId("th1").seriesCount(4).build()));

    Map<String, Object> stub = new LinkedHashMap<>();
    stub.put("ok", true);
    stub.put("stub", true);
    stub.put("articleId", "a1");
    stub.put("documentId", "d1");
    assertEquals(
        JSONUtil.toJsonStr(stub),
        JobPayloads.toJson(ArticleRepairStubJobOutput.builder()
            .ok(true).stub(true).articleId("a1").documentId("d1").build()));
  }

  @Test
  void hotArticleReview_input_output_and_repair() {
    Map<String, Object> originalIn = new LinkedHashMap<>();
    originalIn.put("articleId", "a1");
    originalIn.put("content", "正文");
    originalIn.put("contentHash", "h");
    originalIn.put("sources", List.of());
    originalIn.put("standardVersion", "hot-news-review-v1");
    HotArticleReviewJobInput typedIn = HotArticleReviewJobInput.from(originalIn);
    assertEquals(JSONUtil.toJsonStr(originalIn), JobPayloads.toJson(typedIn));

    Map<String, Object> originalOut = new LinkedHashMap<>();
    originalOut.put("status", "failed");
    originalOut.put("blockingFailed", true);
    originalOut.put("results", List.of(Map.of("code", "X")));
    assertEquals(
        JSONUtil.toJsonStr(originalOut),
        JobPayloads.toJson(HotArticleReviewJobOutput.of(
            "failed", true, List.of(Map.of("code", "X")))));

    Map<String, Object> repairOut = new LinkedHashMap<>();
    repairOut.put("candidateId", "c1");
    repairOut.put("status", "pending");
    repairOut.put("articleId", "a1");
    repairOut.put("reviewId", "r1");
    repairOut.put("content", "修复稿");
    assertEquals(
        JSONUtil.toJsonStr(repairOut),
        JobPayloads.toJson(HotArticleReviewRepairJobOutput.builder()
            .candidateId("c1")
            .status("pending")
            .articleId("a1")
            .reviewId("r1")
            .content("修复稿")
            .build()));
  }

  @Test
  void open_graph_outputs_and_fetch_input() {
    Map<String, Object> base = new LinkedHashMap<>();
    base.put("runId", "wf-1");
    base.put("status", "succeeded");
    Map<String, Object> original = new LinkedHashMap<>(base);
    original.put("themeId", "th1");
    original.put("fetchRunId", "fr1");
    HotNewsFetchJobOutput typed = HotNewsFetchJobOutput.fromGraph(base);
    typed.put("themeId", "th1");
    typed.put("fetchRunId", "fr1");
    assertEquals(JSONUtil.toJsonStr(original), JobPayloads.toJson(typed));

    Map<String, Object> fetchIn = new LinkedHashMap<>();
    fetchIn.put("themeId", "th1");
    fetchIn.put("channelIds", List.of("c1"));
    fetchIn.put("hours", 24);
    fetchIn.put("workflowId", "wf");
    fetchIn.put("fetchRunId", "fr1");
    assertEquals(JSONUtil.toJsonStr(fetchIn), JobPayloads.toJson(HotNewsFetchJobInput.from(fetchIn)));
  }

  @Test
  void courseDraft_wechat_image_article_outputs_via_map_subclass() {
    DummyArticle article = new DummyArticle();
    article.setId("art-1");
    article.setCourseId("c1");
    Map<String, Object> draftOriginal = new LinkedHashMap<>();
    draftOriginal.put("article", BeanUtil.beanToMap(article, new LinkedHashMap<>(), false, true));
    draftOriginal.put("courseId", "c1");
    assertEquals(
        JSONUtil.toJsonStr(draftOriginal),
        JobPayloads.toJson(CourseDraftGenerationJobOutput.of(article, "c1")));
    assertEquals(
        JSONUtil.toJsonStr(draftOriginal),
        JobPayloads.toJson(WechatArticleGenerationJobOutput.of(article, "c1")));

    Map<String, Object> imageBean = new LinkedHashMap<>();
    imageBean.put("id", "img-1");
    imageBean.put("kind", "inline");
    Map<String, Object> imageOriginal = new LinkedHashMap<>(imageBean);
    imageOriginal.put("articleId", "a1");
    imageOriginal.put("documentId", "d1");
    ImageGenerationJobOutput imageTyped = new ImageGenerationJobOutput();
    imageTyped.putAll(imageBean);
    imageTyped.put("articleId", "a1");
    imageTyped.put("documentId", "d1");
    assertEquals(JSONUtil.toJsonStr(imageOriginal), JobPayloads.toJson(imageTyped));

    Map<String, Object> genBase = new LinkedHashMap<>();
    genBase.put("runId", "r1");
    Map<String, Object> genOriginal = new LinkedHashMap<>(genBase);
    genOriginal.put("articleId", "a1");
    genOriginal.put("partial", false);
    HotNewsArticleGenerationJobOutput genOut = HotNewsArticleGenerationJobOutput.fromGraph(genBase);
    genOut.put("articleId", "a1");
    genOut.putIfAbsent("partial", false);
    assertEquals(JSONUtil.toJsonStr(genOriginal), JobPayloads.toJson(genOut));

    CourseContentGenerationJobOutput courseOut = CourseContentGenerationJobOutput.fromGraph(genBase);
    courseOut.putIfAbsent("courseId", "c1");
    Map<String, Object> courseOriginal = new LinkedHashMap<>(genBase);
    courseOriginal.put("courseId", "c1");
    assertEquals(JSONUtil.toJsonStr(courseOriginal), JobPayloads.toJson(courseOut));
  }

  @Test
  void articleRepair_input_omits_nulls() {
    Map<String, Object> withCourse = new LinkedHashMap<>();
    withCourse.put("articleId", "a1");
    withCourse.put("documentId", "d1");
    withCourse.put("courseId", "c1");
    withCourse.put("workId", "c1");
    assertEquals(
        JSONUtil.toJsonStr(withCourse),
        JobPayloads.toJson(ArticleRepairJobInput.builder()
            .articleId("a1")
            .documentId("d1")
            .courseId("c1")
            .workId("c1")
            .build()));

    Map<String, Object> articleOnly = new LinkedHashMap<>();
    articleOnly.put("articleId", "a1");
    assertEquals(
        JSONUtil.toJsonStr(articleOnly),
        JobPayloads.toJson(ArticleRepairJobInput.builder().articleId("a1").build()));
  }

  @Test
  void asMap_round_trip_preserves_bean_keys() {
    HotNewsTopicResearchJobInput input = HotNewsTopicResearchJobInput.builder()
        .topicId("t")
        .researchRunId("r")
        .build();
    Map<String, Object> map = JobPayloads.asMap(input);
    assertEquals("t", map.get("topicId"));
    assertEquals("r", map.get("researchRunId"));
  }

  /** 测试用迷你文章 bean，字段序与 put 一致。 */
  public static class DummyArticle {
    private String id;
    private String courseId;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getCourseId() {
      return courseId;
    }

    public void setCourseId(String courseId) {
      this.courseId = courseId;
    }
  }
}
