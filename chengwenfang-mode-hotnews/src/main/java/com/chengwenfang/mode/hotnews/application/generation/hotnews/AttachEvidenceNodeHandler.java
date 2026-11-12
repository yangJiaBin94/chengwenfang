package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.PageScreenshotFacade;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.crawler.facade.dto.view.PageScreenshotResult;
import com.chengwenfang.crawler.facade.enums.ArticleContentSource;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.mode.hotnews.application.VideoParseRunService;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsAttachEvidencePlan;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsEvidenceShotSpec;
import com.chengwenfang.mode.hotnews.application.generation.hotnews.model.HotNewsVideoFrameEvidence;
import com.chengwenfang.mode.hotnews.application.prompt.HotNewsWritingPrompts;
import com.chengwenfang.mode.hotnews.application.prompt.VideoVisionPrompts;
import com.chengwenfang.mode.hotnews.domain.HotNewsEvidenceAnchorSupport;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.domain.ResearchContentClassifier;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArtifactTypes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审校稿稳定后：由 AI 决定是否截来源佐证图；网页先抽正文再定位锚点后截屏，PDF 渲首页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachEvidenceNodeHandler implements HotNewsNodeHandler {
  /** 文本补全。 */
  private final HotNewsTextCompletionSupport textCompletion;
  /** 产物复用。 */
  private final HotNewsArtifactReuseSupport artifactReuse;
  /** 网页正文抽取。 */
  private final ArticleContentFacade articleContent;
  /** 网页/PDF 截屏。 */
  private final PageScreenshotFacade pageScreenshot;
  /** 视频解析抽帧查询。 */
  private final VideoParseRunService videoParseRuns;
  /** 文件落盘。 */
  private final FileFacade files;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.ATTACH_EVIDENCE;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<PromptPackPromptBindingView> promptPackBindings,
      String nodeKey,
      String nodeName) {
    String resolvedNodeKey = StrUtil.blankToDefault(nodeKey, supports().getValue());
    String draft = StrUtil.nullToEmpty(state.getDraft());
    if (StrUtil.isBlank(draft)) {
      log.info("佐证截屏跳过：原稿为空 jobId={}", state.getJobId());
      return;
    }

    Map<String, Object> inputSummary = new LinkedHashMap<>();
    inputSummary.put("draft", HotNewsArtifactReuseSupport.fingerprint(draft));
    inputSummary.put("references", HotNewsArtifactReuseSupport.fingerprint(state.getReferencesJson()));

    String reused = artifactReuse.tryReuseDraft(
        state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.EVIDENCE_DRAFT, inputSummary);
    if (StrUtil.isBlank(reused)) {
      reused = artifactReuse.tryReuseDraft(
          state.getJobId(), resolvedNodeKey, HotNewsArtifactTypes.EVIDENCE_DRAFT, null);
    }
    if (StrUtil.isNotBlank(reused)) {
      state.setDraft(reused);
      return;
    }

    List<String> allowedUrls = HotNewsEvidencePlanSupport.extractAllowedUrls(state.getReferencesJson());
    HotNewsAttachEvidencePlan plan = planEvidence(state, promptPackBindings, allowedUrls);
    String updated = draft;
    int attached = 0;
    List<Map<String, Object>> shotAttempts = new ArrayList<>();
    if (CollUtil.isNotEmpty(plan.getShots())) {
      Map<String, String> capturedByKey = new LinkedHashMap<>();
      int sequence = 0;
      for (HotNewsEvidenceShotSpec shot : plan.getShots()) {
        sequence++;
        boolean videoShot = ResearchContentClassifier.isVideo(shot.getUrl(), null);
        // 网页/PDF 依赖 page-screenshot；视频抽帧不依赖该开关
        if (!videoShot && !pageScreenshot.isEnabled()) {
          Map<String, Object> skipped = new LinkedHashMap<>();
          skipped.put("sequence", sequence);
          skipped.put("url", StrUtil.nullToEmpty(shot.getUrl()));
          skipped.put("focusHint", StrUtil.nullToEmpty(shot.getFocusHint()));
          skipped.put("ok", false);
          skipped.put("error", "page-screenshot 未启用");
          shotAttempts.add(skipped);
          continue;
        }
        String dedupeKey = StrUtil.nullToEmpty(shot.getUrl()).toLowerCase()
            + "\n" + StrUtil.nullToEmpty(shot.getFocusHint()).toLowerCase();
        String fileId = capturedByKey.get(dedupeKey);
        CaptureOutcome outcome = null;
        boolean reusedCapture = StrUtil.isNotBlank(fileId);
        if (StrUtil.isBlank(fileId)) {
          outcome = captureAndStore(state.getJobId(), sequence, shot);
          fileId = outcome.fileId();
          if (StrUtil.isNotBlank(fileId)) {
            capturedByKey.put(dedupeKey, fileId);
          }
        }
        boolean ok = StrUtil.isNotBlank(fileId);
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("sequence", sequence);
        attempt.put("url", StrUtil.nullToEmpty(shot.getUrl()));
        attempt.put("focusHint", StrUtil.nullToEmpty(shot.getFocusHint()));
        attempt.put("caption", StrUtil.nullToEmpty(shot.getCaption()));
        attempt.put("ok", ok);
        attempt.put("reusedCapture", reusedCapture);
        if (outcome != null) {
          if (StrUtil.isNotBlank(outcome.resolvedAnchor())) {
            attempt.put("resolvedAnchor", outcome.resolvedAnchor());
          }
          if (StrUtil.isNotBlank(outcome.extractSource())) {
            attempt.put("extractSource", outcome.extractSource());
          }
        }
        if (ok) {
          attempt.put("fileId", fileId);
        } else {
          attempt.put(
              "error",
              outcome == null
                  ? "截屏失败"
                  : StrUtil.blankToDefault(outcome.error(), "截屏失败"));
        }
        shotAttempts.add(attempt);
        if (!ok) {
          log.warn(
              "佐证截屏未挂入稿件 jobId={} seq={} url={} focusHint={} anchor={} err={}",
              state.getJobId(),
              sequence,
              shot.getUrl(),
              shot.getFocusHint(),
              outcome == null ? null : outcome.resolvedAnchor(),
              attempt.get("error"));
          continue;
        }
        log.info(
            "佐证截屏已挂入稿件 jobId={} seq={} url={} focusHint={} anchor={} fileId={} reused={}",
            state.getJobId(),
            sequence,
            shot.getUrl(),
            shot.getFocusHint(),
            outcome == null ? null : outcome.resolvedAnchor(),
            fileId,
            reusedCapture);
        updated = HotNewsMarkdownUtil.insertEvidenceShot(
            updated, shot.getInsertAfter(), shot.getCaption(), fileId);
        attached++;
      }
    }

    updated = HotNewsMarkdownUtil.finalizeEvidenceMarkdown(updated);
    state.setDraft(updated);
    Map<String, Object> outputSummary = new LinkedHashMap<>();
    outputSummary.put("chars", updated.length());
    outputSummary.put("plannedShots", plan.getShots() == null ? 0 : plan.getShots().size());
    outputSummary.put("attachedShots", attached);
    outputSummary.put("failedShots", Math.max(0, shotAttempts.size() - attached));
    outputSummary.put("shotAttempts", shotAttempts);
    log.info(
        "佐证截屏汇总 jobId={} planned={} attached={} failed={} attempts={}",
        state.getJobId(),
        outputSummary.get("plannedShots"),
        attached,
        outputSummary.get("failedShots"),
        shotAttempts);
    artifactReuse.persistTextArtifact(
        state.getJobId(),
        resolvedNodeKey,
        inputSummary,
        HotNewsArtifactTypes.EVIDENCE_DRAFT,
        updated,
        outputSummary);
  }

  /**
   * 调用模型产出佐证计划并校验。
   *
   * @param state              图状态
   * @param promptPackBindings 提示词包
   * @param allowedUrls        允许 URL
   * @return 计划
   */
  private HotNewsAttachEvidencePlan planEvidence(
      HotNewsGraphState state,
      List<PromptPackPromptBindingView> promptPackBindings,
      List<String> allowedUrls) {
    if (CollUtil.isEmpty(allowedUrls)) {
      return HotNewsAttachEvidencePlan.builder().shots(List.of()).build();
    }
    String promptPackText = textCompletion.composePromptPackText(promptPackBindings);
    String systemPrompt = HotNewsWritingPrompts.systemPrompt(promptPackText, state.getThemeContext());
    String userPrompt = """
        根据已审校原稿与参考资料，决定是否需要「来源网页/PDF 佐证截屏」（JSON）。
        要求：
        1. 仅当正文有可核验硬事实（数字、监管原文、财报表述等）且参考资料有对应 URL 时才截。
        2. shots 最多 %d 条；不需要截屏时输出 {"shots":[]}。
        3. 每条字段：url（必须来自下列参考资料）、focusHint（要佐证的硬事实线索：关键数字/专名即可，
           不要把多句用分号拼成长串；网页会先抽源页正文再定位；视频则复用解析阶段抽帧）、
           caption（图下出处简称：媒体名或「媒体《标题》」，禁止含 URL，禁止自带「来源：」前缀）、
           insertAfter（稿中已有子串，截图插在其后；可空。优先用含 focusHint 硬事实的那句完整句并带句末标点；
           禁止只用孤立数字或短词当锚点）。
        4. 禁止臆造 URL；叙述性软观点不必截；视频链仅在已解析并保留抽帧时有图。
        5. 只输出 JSON。
        参考资料 URL 白名单：
        %s
        参考资料 JSON：
        %s
        已审校原稿：
        %s
        """.formatted(
        HotNewsEvidenceKeys.MAX_SHOTS,
        String.join("\n", allowedUrls),
        StrUtil.nullToEmpty(state.getReferencesJson()),
        StrUtil.nullToEmpty(state.getDraft()));
    try {
      String raw = textCompletion.complete(
          systemPrompt, userPrompt, HotNewsWorkflowNodeType.ATTACH_EVIDENCE.getValue());
      return HotNewsEvidencePlanSupport.parseAndSanitize(raw, allowedUrls);
    } catch (Exception exception) {
      log.warn("佐证计划生成失败，跳过截屏 jobId={} err={}", state.getJobId(), exception.getMessage());
      return HotNewsAttachEvidencePlan.builder().shots(List.of()).build();
    }
  }

  /**
   * 解析定位锚点后截屏并写入 stored_files。
   *
   * @param jobId    任务 id
   * @param sequence 序号
   * @param shot     截屏规格
   * @return 截屏结果（含 fileId 或 error）
   */
  private CaptureOutcome captureAndStore(String jobId, int sequence, HotNewsEvidenceShotSpec shot) {
    String url = StrUtil.trim(shot.getUrl());
    // 仅按 URL 判定视频，避免 caption「短视频」误伤网页
    if (ResearchContentClassifier.isVideo(url, null)) {
      return captureFromPersistedVideoFrames(jobId, sequence, url, shot.getFocusHint());
    }
    String findText;
    String extractSource = null;
    if (HotNewsEvidenceAnchorSupport.looksLikePdfUrl(url)) {
      // PDF 只渲首页，不依赖正文定位
      findText = null;
      extractSource = HotNewsEvidenceKeys.EXTRACT_SOURCE_PDF;
    } else {
      AnchorResolve resolved = resolveWebAnchor(url, shot.getFocusHint(), shot.getCaption());
      if (StrUtil.isBlank(resolved.findText())) {
        return CaptureOutcome.failed(
            resolved.error(),
            null,
            resolved.extractSource());
      }
      findText = resolved.findText();
      extractSource = resolved.extractSource();
    }

    PageScreenshotResult result = pageScreenshot.captureEvidence(url, findText);
    if (result == null || !result.isOk() || CollUtil.isEmpty(result.getPaths())) {
      String error = result == null
          ? "截屏结果为空"
          : StrUtil.blankToDefault(result.getError(), "截屏失败且无错误信息");
      log.warn(
          "佐证截屏失败 jobId={} seq={} url={} focusHint={} anchor={} err={}",
          jobId,
          sequence,
          url,
          shot.getFocusHint(),
          findText,
          error);
      if (result != null) {
        pageScreenshot.cleanup(result.getPaths());
      }
      return CaptureOutcome.failed(error, findText, extractSource);
    }
    String pathText = result.getPaths().get(0);
    try {
      Path path = Path.of(pathText);
      byte[] bytes = Files.readAllBytes(path);
      String safeJob = StrUtil.blankToDefault(jobId, "unknown");
      String objectKey = HotNewsEvidenceKeys.OBJECT_KEY_PREFIX + safeJob + "/shot-"
          + sequence + ".png";
      FileView saved = files.saveLocalBytes(objectKey, "evidence-" + sequence + ".png", "image/png", bytes);
      if (saved == null || StrUtil.isBlank(saved.getId())) {
        return CaptureOutcome.failed("截屏文件落盘后未返回 fileId", findText, extractSource);
      }
      return CaptureOutcome.ok(saved.getId(), findText, extractSource);
    } catch (Exception exception) {
      log.warn(
          "佐证截屏落盘失败 jobId={} seq={} url={} focusHint={} err={}",
          jobId,
          sequence,
          url,
          shot.getFocusHint(),
          exception.getMessage());
      return CaptureOutcome.failed(
          "截屏落盘失败: " + exception.getMessage(), findText, extractSource);
    } finally {
      pageScreenshot.cleanup(result.getPaths());
      try {
        FileUtil.del(Path.of(pathText).getParent());
      } catch (Exception ignored) {
        // 临时目录清理失败不阻断
      }
    }
  }

  /**
   * 从视频解析保留的抽帧中，按画面描述让 AI 选型后挂入佐证。
   *
   * @param jobId     任务 id
   * @param sequence  序号
   * @param url       视频 URL
   * @param focusHint 要佐证的事实线索
   * @return 截屏结果
   */
  private CaptureOutcome captureFromPersistedVideoFrames(
      String jobId, int sequence, String url, String focusHint) {
    List<HotNewsVideoFrameEvidence> frames = videoParseRuns.findFrameEvidenceByUrl(url);
    if (CollUtil.isEmpty(frames)) {
      log.warn("视频佐证无保留抽帧 jobId={} seq={} url={}", jobId, sequence, url);
      return CaptureOutcome.failed(
          "视频素材尚无保留抽帧，请先完成视频解析",
          null,
          HotNewsEvidenceKeys.EXTRACT_SOURCE_VIDEO_FRAMES);
    }
    int frameIndex = pickVideoFrameIndex(focusHint, frames);
    HotNewsVideoFrameEvidence chosen = frames.get(frameIndex);
    String sourceFileId = chosen.getFileId();
    String anchorLabel = "video-frame#" + (frameIndex + 1)
        + (StrUtil.isBlank(chosen.getCaption()) ? "" : ":" + StrUtil.maxLength(chosen.getCaption(), 40));
    try {
      byte[] bytes = files.getBytes(sourceFileId);
      if (bytes == null || bytes.length == 0) {
        return CaptureOutcome.failed(
            "视频抽帧文件为空或不可读",
            anchorLabel,
            HotNewsEvidenceKeys.EXTRACT_SOURCE_VIDEO_FRAMES);
      }
      String objectKey = HotNewsEvidenceKeys.OBJECT_KEY_PREFIX
          + StrUtil.blankToDefault(jobId, "unknown")
          + "/video-frame-" + sequence + ".jpg";
      FileView saved = files.saveLocalBytes(
          objectKey, "evidence-video-" + sequence + ".jpg", "image/jpeg", bytes);
      if (saved == null || StrUtil.isBlank(saved.getId())) {
        return CaptureOutcome.failed(
            "视频抽帧复制到佐证目录失败",
            anchorLabel,
            HotNewsEvidenceKeys.EXTRACT_SOURCE_VIDEO_FRAMES);
      }
      log.info(
          "视频佐证按描述选型 jobId={} seq={} url={} index={} sourceFileId={} evidenceFileId={} caption={}",
          jobId,
          sequence,
          url,
          frameIndex,
          sourceFileId,
          saved.getId(),
          chosen.getCaption());
      return CaptureOutcome.ok(
          saved.getId(),
          anchorLabel,
          HotNewsEvidenceKeys.EXTRACT_SOURCE_VIDEO_FRAMES);
    } catch (Exception exception) {
      log.warn(
          "视频抽帧复制失败 jobId={} seq={} url={} err={}",
          jobId,
          sequence,
          url,
          exception.getMessage());
      return CaptureOutcome.failed(
          "视频抽帧复制失败: " + exception.getMessage(),
          anchorLabel,
          HotNewsEvidenceKeys.EXTRACT_SOURCE_VIDEO_FRAMES);
    }
  }

  /**
   * 根据事实线索与逐帧描述，让文本模型选出最合适的帧下标。
   *
   * @param focusHint 事实线索
   * @param frames    抽帧列表
   * @return 合法下标；失败时退回中间帧
   */
  private int pickVideoFrameIndex(String focusHint, List<HotNewsVideoFrameEvidence> frames) {
    int fallback = frames.size() / 2;
    boolean anyCaption = frames.stream().anyMatch(frame -> StrUtil.isNotBlank(frame.getCaption()));
    if (!anyCaption || frames.size() == 1) {
      return fallback;
    }
    StringBuilder catalog = new StringBuilder();
    for (int index = 0; index < frames.size(); index++) {
      catalog.append(index)
          .append(". ")
          .append(StrUtil.blankToDefault(frames.get(index).getCaption(), "（无描述）"))
          .append('\n');
    }
    String userPrompt = """
        要佐证的事实：
        %s

        可选截图描述：
        %s
        """.formatted(StrUtil.blankToDefault(focusHint, "（未提供）"), catalog);
    try {
      String raw = textCompletion.complete(
          VideoVisionPrompts.PICK_FRAME_SYSTEM,
          userPrompt,
          ModelCallScenes.HOT_NEWS_ATTACH_EVIDENCE_PICK_FRAME);
      JSONObject json = LlmJsonSupport.parseJsonObject(raw);
      if (json == null || !json.containsKey("index")) {
        return fallback;
      }
      int index = json.getInt("index", fallback);
      if (index < 0 || index >= frames.size()) {
        return fallback;
      }
      return index;
    } catch (Exception exception) {
      log.warn("视频帧 AI 选型失败，改用中间帧 err={}", exception.getMessage());
      return fallback;
    }
  }

  /**
   * 抽取网页正文并为线索选出定位锚点。
   *
   * @param url       网页地址
   * @param focusHint 佐证线索
   * @param caption   出处简称（辅助匹配）
   * @return 锚点解析结果
   */
  private AnchorResolve resolveWebAnchor(String url, String focusHint, String caption) {
    ArticleContentResult extracted = articleContent.resolve(ArticleContentRequest.builder()
        .url(url)
        .title(caption)
        .fallbackSummary(null)
        .build());
    if (extracted == null || !extracted.isUsable() || StrUtil.isBlank(extracted.getText())) {
      String reason = extracted == null
          ? "正文抽取结果为空"
          : StrUtil.blankToDefault(extracted.getErrorMessage(), "未能抽取可用正文");
      return AnchorResolve.failed("源页正文抽取失败: " + reason, sourceValue(extracted));
    }
    ArticleContentSource source = extracted.getSource();
    if (source == null
        || source == ArticleContentSource.NONE
        || source == ArticleContentSource.SUMMARY) {
      return AnchorResolve.failed(
          "源页正文不可用（仅有摘要或无正文），无法可靠定位",
          sourceValue(extracted));
    }
    String hint = StrUtil.blankToDefault(focusHint, caption);
    String anchor = HotNewsEvidenceAnchorSupport.resolveFindText(extracted.getText(), hint);
    if (StrUtil.isBlank(anchor)) {
      return AnchorResolve.failed(
          "已抽取正文但未匹配到可定位锚点: " + StrUtil.maxLength(StrUtil.nullToEmpty(hint), 80),
          sourceValue(extracted));
    }
    log.info(
        "佐证锚点已解析 url={} source={} focusHint={} anchor={}",
        url,
        source.getValue(),
        focusHint,
        anchor);
    return AnchorResolve.ok(anchor, source.getValue());
  }

  /**
   * @param extracted 抽取结果
   * @return 来源字面量
   */
  private static String sourceValue(ArticleContentResult extracted) {
    if (extracted == null || extracted.getSource() == null) {
      return null;
    }
    return extracted.getSource().getValue();
  }

  /**
   * 网页锚点解析结果。
   *
   * @param findText      定位文本；失败为 null
   * @param error         失败原因；成功为 null
   * @param extractSource 正文来源
   */
  private record AnchorResolve(String findText, String error, String extractSource) {
    /**
     * @param findText      锚点
     * @param extractSource 来源
     * @return 成功
     */
    private static AnchorResolve ok(String findText, String extractSource) {
      return new AnchorResolve(findText, null, extractSource);
    }

    /**
     * @param error         失败原因
     * @param extractSource 来源
     * @return 失败
     */
    private static AnchorResolve failed(String error, String extractSource) {
      return new AnchorResolve(null, error, extractSource);
    }
  }

  /**
   * 单次截屏落盘结果。
   *
   * @param fileId         成功时的文件 id；失败为 null
   * @param error          失败原因；成功为 null
   * @param resolvedAnchor 实际用于定位的锚点
   * @param extractSource  正文抽取来源
   */
  private record CaptureOutcome(
      String fileId, String error, String resolvedAnchor, String extractSource) {
    /**
     * @param fileId         文件 id
     * @param resolvedAnchor 锚点
     * @param extractSource  来源
     * @return 成功结果
     */
    private static CaptureOutcome ok(String fileId, String resolvedAnchor, String extractSource) {
      return new CaptureOutcome(fileId, null, resolvedAnchor, extractSource);
    }

    /**
     * @param error          失败原因
     * @param resolvedAnchor 锚点（可空）
     * @param extractSource  来源（可空）
     * @return 失败结果
     */
    private static CaptureOutcome failed(
        String error, String resolvedAnchor, String extractSource) {
      return new CaptureOutcome(null, error, resolvedAnchor, extractSource);
    }
  }
}
