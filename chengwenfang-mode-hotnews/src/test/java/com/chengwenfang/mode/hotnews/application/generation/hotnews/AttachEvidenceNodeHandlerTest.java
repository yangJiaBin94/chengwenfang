package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.PageScreenshotFacade;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.mode.hotnews.application.VideoParseRunService;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsVideoFrameEvidence;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link AttachEvidenceNodeHandler}：抽正文定位、成功落盘、失败降级、截屏关闭。 */
@ExtendWith(MockitoExtension.class)
class AttachEvidenceNodeHandlerTest {

  @Mock
  private HotNewsTextCompletionSupport textCompletion;
  @Mock
  private HotNewsArtifactReuseSupport artifactReuse;
  @Mock
  private ArticleContentFacade articleContent;
  @Mock
  private PageScreenshotFacade pageScreenshot;
  @Mock
  private VideoParseRunService videoParseRuns;
  @Mock
  private FileFacade files;

  @InjectMocks
  private AttachEvidenceNodeHandler handler;

  @TempDir
  Path tempDir;

  @Test
  void execute_emptyPlan_skipsScreenshotButStripsUrls() {
    HotNewsGraphState state = baseState("正文\n来源简称：https://news.example/a\n");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("{\"shots\":[]}");

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    verify(pageScreenshot, never()).captureEvidence(anyString(), any());
    verify(files, never()).saveLocalBytes(anyString(), anyString(), anyString(), any());
    assertThat(state.getDraft()).doesNotContain("https://");
    assertThat(state.getDraft()).contains("来源简称");
    verify(artifactReuse).persistTextArtifact(
        eq("job-1"),
        eq("hot_news_attach_evidence"),
        any(),
        eq(HotNewsArtifactTypes.EVIDENCE_DRAFT),
        anyString(),
        any());
  }

  @Test
  void execute_captureSuccess_usesResolvedAnchorFromExtractedBody() throws Exception {
    Path shot = tempDir.resolve("shot-01.png");
    Files.write(shot, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
    HotNewsGraphState state = baseState("关键数字上升。\n来源：财联社\n");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("""
            {"shots":[{"url":"https://news.example/a","focusHint":"增速12%；另一句拼上","caption":"财联社","insertAfter":"来源：财联社"}]}
            """);
    when(pageScreenshot.isEnabled()).thenReturn(true);
    when(articleContent.resolve(any(ArticleContentRequest.class)))
        .thenReturn(ArticleContentResult.builder()
            .usable(true)
            .source(ArticleContentSource.TRAFILATURA)
            .text("公司披露营收增速12%，显著好于市场预期。")
            .build());
    when(pageScreenshot.captureEvidence(eq("https://news.example/a"), anyString()))
        .thenReturn(PageScreenshotResult.builder()
            .ok(true)
            .paths(List.of(shot.toAbsolutePath().toString()))
            .build());
    when(files.saveLocalBytes(anyString(), anyString(), anyString(), any()))
        .thenReturn(FileView.builder().id("33333333-3333-3333-3333-333333333333").build());

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    assertThat(state.getDraft()).contains("![来源截图](/api/files/33333333-3333-3333-3333-333333333333)");
    assertThat(state.getDraft()).doesNotContain("[[HOT_NEWS_EVIDENCE:");
    ArgumentCaptor<String> anchorCaptor = ArgumentCaptor.forClass(String.class);
    verify(pageScreenshot).captureEvidence(eq("https://news.example/a"), anchorCaptor.capture());
    assertThat(anchorCaptor.getValue()).containsAnyOf("12%", "增速12%");
    verify(pageScreenshot).cleanup(any());
  }

  @Test
  void execute_extractFailed_keepsTextOnlyAndRecordsError() {
    HotNewsGraphState state = baseState("正文 https://news.example/a\n来源：财联社\n");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("""
            {"shots":[{"url":"https://news.example/a","focusHint":"无此文","caption":"财联社"}]}
            """);
    when(pageScreenshot.isEnabled()).thenReturn(true);
    when(articleContent.resolve(any(ArticleContentRequest.class)))
        .thenReturn(ArticleContentResult.builder()
            .usable(false)
            .source(ArticleContentSource.NONE)
            .errorMessage("抽取失败")
            .build());

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    verify(pageScreenshot, never()).captureEvidence(anyString(), any());
    verify(files, never()).saveLocalBytes(anyString(), anyString(), anyString(), any());
    assertThat(state.getDraft()).doesNotContain("https://");
    assertThat(state.getDraft()).doesNotContain("/api/files/");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
    verify(artifactReuse).persistTextArtifact(
        eq("job-1"),
        eq("hot_news_attach_evidence"),
        any(),
        eq(HotNewsArtifactTypes.EVIDENCE_DRAFT),
        anyString(),
        summaryCaptor.capture());
    Map<String, Object> summary = summaryCaptor.getValue();
    assertThat(summary.get("failedShots")).isEqualTo(1);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> attempts = (List<Map<String, Object>>) summary.get("shotAttempts");
    assertThat(attempts.get(0).get("error").toString()).contains("源页正文抽取失败");
  }

  @Test
  void execute_screenshotDisabled_skipsCaptureStillStripsUrls() {
    HotNewsGraphState state = baseState("正文\n来源简称：https://news.example/a\n");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("""
            {"shots":[{"url":"https://news.example/a","caption":"媒体"}]}
            """);
    when(pageScreenshot.isEnabled()).thenReturn(false);

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    verify(pageScreenshot, never()).captureEvidence(anyString(), any());
    verify(articleContent, never()).resolve(any());
    assertThat(state.getDraft()).doesNotContain("https://");
  }

  @Test
  void execute_videoUrl_usesPersistedFramesWithoutPageScreenshot() {
    HotNewsGraphState state = baseState("短视频里写涨 288%。\n来源：短视频\n");
    state.setReferencesJson(
        "[{\"url\":\"http://www.bilibili.com/video/av117550234996844\",\"title\":\"短视频\"}]");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("""
            {"shots":[{"url":"http://www.bilibili.com/video/av117550234996844","focusHint":"288.69%","caption":"短视频","insertAfter":"来源：短视频"}]}
            """);
    // 无描述时退回中间帧，不再二次调模型
    when(videoParseRuns.findFrameEvidenceByUrl(anyString()))
        .thenReturn(List.of(
            HotNewsVideoFrameEvidence.builder().fileId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").build(),
            HotNewsVideoFrameEvidence.builder().fileId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb").build(),
            HotNewsVideoFrameEvidence.builder().fileId("cccccccc-cccc-cccc-cccc-cccccccccccc").build()));
    when(files.getBytes("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
        .thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    when(files.saveLocalBytes(anyString(), anyString(), anyString(), any()))
        .thenReturn(FileView.builder().id("dddddddd-dddd-dddd-dddd-dddddddddddd").build());

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    verify(pageScreenshot, never()).captureEvidence(anyString(), any());
    verify(pageScreenshot, never()).isEnabled();
    verify(articleContent, never()).resolve(any());
    assertThat(state.getDraft()).contains("![来源截图](/api/files/dddddddd-dddd-dddd-dddd-dddddddddddd)");
  }

  @Test
  void execute_videoUrl_picksFrameByCaptionWithAi() {
    HotNewsGraphState state = baseState("短视频里写涨 288%。\n来源：短视频\n");
    state.setReferencesJson(
        "[{\"url\":\"http://www.bilibili.com/video/av117550234996844\",\"title\":\"短视频\"}]");
    when(artifactReuse.tryReuseDraft(anyString(), anyString(), anyString(), any())).thenReturn(null);
    when(textCompletion.composePromptPackText(any())).thenReturn("");
    when(textCompletion.complete(anyString(), anyString(), anyString()))
        .thenReturn("""
            {"shots":[{"url":"http://www.bilibili.com/video/av117550234996844","focusHint":"288.69%","caption":"短视频","insertAfter":"来源：短视频"}]}
            """)
        .thenReturn("{\"index\":0}");
    when(videoParseRuns.findFrameEvidenceByUrl(anyString()))
        .thenReturn(List.of(
            HotNewsVideoFrameEvidence.builder()
                .fileId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                .caption("画面显示涨幅 288.69%")
                .build(),
            HotNewsVideoFrameEvidence.builder()
                .fileId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                .caption("主播面对镜头讲话")
                .build(),
            HotNewsVideoFrameEvidence.builder()
                .fileId("cccccccc-cccc-cccc-cccc-cccccccccccc")
                .caption("片尾二维码")
                .build()));
    when(files.getBytes("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        .thenReturn(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    when(files.saveLocalBytes(anyString(), anyString(), anyString(), any()))
        .thenReturn(FileView.builder().id("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee").build());

    handler.execute(state, List.of(), "hot_news_attach_evidence", "附加来源佐证截屏");

    verify(textCompletion).complete(
        anyString(), anyString(), eq(ModelCallScenes.HOT_NEWS_ATTACH_EVIDENCE_PICK_FRAME));
    verify(files).getBytes("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assertThat(state.getDraft()).contains("![来源截图](/api/files/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee)");
  }

  private static HotNewsGraphState baseState(String draft) {
    HotNewsGraphState state = new HotNewsGraphState();
    state.setJobId("job-1");
    state.setDraft(draft);
    state.setReferencesJson("[{\"url\":\"https://news.example/a\",\"title\":\"t\"}]");
    return state;
  }
}
