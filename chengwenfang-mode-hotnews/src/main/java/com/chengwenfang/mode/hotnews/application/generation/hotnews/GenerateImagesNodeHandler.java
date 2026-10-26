package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.ai.facade.dto.request.ImageGenerateRequest;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.mode.hotnews.application.HotNewsWorkStore;
import com.chengwenfang.content.facade.dto.request.PersistGeneratedImageRequest;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.StorageService;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按原稿插图提示词串行生成封面与正文插图，并回写公众号占位。
 * <p>每张图有固定槽位标识（第 N/M 张）；重试时跳过已成功槽位，进度只报当前正在生成的槽位。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateImagesNodeHandler implements HotNewsNodeHandler {
  /** 文本/Skill 组合「*/
  private final HotNewsTextCompletionSupport textCompletion;
  /** 图像 AI「*/
  private final AiImageFacade images;
  /** 内容落库「*/
  private final ContentFacade content;
  /** 热点作品产出。 */
  private final HotNewsWorkStore workStore;
  /** 本地存储「*/
  private final StorageService storage;
  /** 任务进度与续租「*/
  private final JobFacade jobs;
  /** Worker 租约等配置「*/
  private final ChengwenfangProperties properties;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.GENERATE_IMAGES;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    // 封面必出；正文插图按档位；槽位标识固定为第 1…N 张
    String imageOption = HotNewsImageCountOption.normalize(state.getImageCountOption());
    state.setImageCountOption(imageOption);
    int inlineCount = HotNewsImageCountOption.resolveInlineCount(imageOption);
    List<HotNewsImageSlot> slots = HotNewsImageSlot.plan(inlineCount);
    state.setImagesSkipped(false);
    HotNewsArticleSkeletonView skeleton = state.getSkeleton();
    String illustrationStyle = skeleton == null || StrUtil.isBlank(skeleton.getIllustrationStyle())
        ? HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE
        : skeleton.getIllustrationStyle();
    String coverBrief = skeleton == null || StrUtil.isBlank(skeleton.getCoverBrief())
        ? StrUtil.nullToEmpty(state.getTitle())
        : skeleton.getCoverBrief();
    String skillPrompt = textCompletion.composeSkillPrompt(skillBindings);
    List<HotNewsGeneratedImageSummary> generated = new ArrayList<>();
    String wechat = StrUtil.nullToEmpty(state.getWechat());
    String draft = StrUtil.nullToEmpty(state.getDraft());
    // 仅加载当前 Job 可复用的已成功配图（失败重试）；跨 Job 的历史图不复用
    String wechatDocumentId = workStore.wechatOutputId(state.getArticleId());
    List<GeneratedImageView> existingImages =
        content.listImages(state.getArticleId(), wechatDocumentId);
    String coverFromDraft = HotNewsMarkdownUtil.coverPrompt(draft);
    List<HotNewsMarkdownUtil.PromptHit> inlinePrompts = HotNewsMarkdownUtil.findInlinePrompts(draft);

    // 严格串行：完成槽位 N 再进入 N+1；已成功槽位静默复用，进度只报当前生成槽位
    for (HotNewsImageSlot slot : slots) {
      boolean cover = slot.kind() == HotNewsImageKind.COVER;
      String basePrompt = cover
          ? StrUtil.blankToDefault(coverFromDraft, """
              热点公众号封面。风格：%s。围绕标题「%s」的核心内容做画面主信息，须一眼可读并吸引点击；构图要点：%s。
              """.formatted(
              illustrationStyle,
              StrUtil.nullToEmpty(state.getTitle()),
              coverBrief))
          : resolveInlineBasePrompt(slot, inlinePrompts, illustrationStyle, state.getTitle());
      String size = cover ? HotNewsImageSizes.COVER_SIZE : HotNewsImageSizes.INLINE_SIZE;
      int width = cover ? HotNewsImageSizes.COVER_WIDTH : HotNewsImageSizes.INLINE_WIDTH;
      int height = cover ? HotNewsImageSizes.COVER_HEIGHT : HotNewsImageSizes.INLINE_HEIGHT;
      // 封面额外注入标题/论点/封面约束，避免原稿提示词偏空装饰
      String prompt = cover
          ? enrichCoverPrompt(
              buildImagePrompt(skillPrompt, basePrompt, size),
              state.getTitle(),
              skeleton == null ? null : skeleton.getCoreThesis(),
              coverBrief)
          : buildImagePrompt(skillPrompt, basePrompt, size);
      GeneratedImageView image = resolveOrGenerate(
          existingImages,
          state.getArticleId(),
          state.getJobId(),
          slot,
          prompt,
          size,
          width,
          height);
      generated.add(toSummary(image));
      String markdown = "![](/api/images/" + image.getId() + ")";
      if (slot.kind() == HotNewsImageKind.COVER) {
        draft = HotNewsMarkdownUtil.attachImageAfterPrompt(draft, coverFromDraft, markdown);
        wechat = wechat.replace(HotNewsImageSizes.COVER_TOKEN, markdown);
      } else {
        String fromDraft = slot.sequenceNo() <= inlinePrompts.size()
            ? inlinePrompts.get(slot.sequenceNo() - 1).prompt()
            : "";
        String token = HotNewsImageSizes.inlineToken(slot.sequenceNo());
        draft = HotNewsMarkdownUtil.attachImageAfterPrompt(draft, fromDraft, markdown);
        if (wechat.contains(token)) {
          wechat = wechat.replace(token, markdown);
        } else {
          wechat = wechat.strip() + "\n\n" + markdown + "\n";
        }
      }
    }

    // 完稿约束：清正文禁用符号；公众号清提示词/提示标题残留
    draft = HotNewsMarkdownUtil.sanitizeProseSymbols(draft);
    wechat = HotNewsMarkdownUtil.stripWechatProductionResidue(
        HotNewsMarkdownUtil.sanitizeProseSymbols(wechat));
    state.setDraft(draft);
    state.setWechat(wechat);
    state.setGeneratedImages(generated);
    state.setImagesSkipped(false);
  }

  /**
   * 解析正文插图槽位的原稿提示词（无则兜底）。
   *
   * @param slot               槽位
   * @param inlinePrompts      原稿内文提示词
   * @param illustrationStyle  风格
   * @param title              标题
   * @return 基础提示词
   */
  private static String resolveInlineBasePrompt(
      HotNewsImageSlot slot,
      List<HotNewsMarkdownUtil.PromptHit> inlinePrompts,
      String illustrationStyle,
      String title) {
    String fromDraft = slot.sequenceNo() <= inlinePrompts.size()
        ? inlinePrompts.get(slot.sequenceNo() - 1).prompt()
        : "";
    return StrUtil.blankToDefault(fromDraft, """
        热点正文插图第 %d 张。风格：%s。围绕标题「%s」画出可识别的具体判断或对比，让读者看出这段在讲什么。
        """.formatted(slot.sequenceNo(), illustrationStyle, StrUtil.nullToEmpty(title)));
  }

  /**
   * 优先复用「本 Job」已落库且文件仍在的槽位；否则续租后串行调用模型。
   * <p>新开生成任务是新 Job，不会复用历史配图；仅失败重试（同 Job）才跳过已成功槽位。
   * <p>复用时不上报进度，避免重试时进度文案跳回更小的序号。
   *
   * @param existingImages 文章已有配图
   * @param articleId      文章 ID
   * @param jobId          任务 ID
   * @param slot           固定槽位标识
   * @param prompt         生图提示词
   * @param size           尺寸字符个
   * @param width          宽
   * @param height         高
   * @return 可写入正文的配图视图
   */
  private GeneratedImageView resolveOrGenerate(
      List<GeneratedImageView> existingImages,
      String articleId,
      String jobId,
      HotNewsImageSlot slot,
      String prompt,
      String size,
      int width,
      int height) {
    GeneratedImageView candidate = HotNewsGeneratedImageReuse.pickCandidate(
        existingImages, jobId, slot.kind(), slot.sequenceNo());
    if (candidate != null && imageFileExists(candidate.getId())) {
      log.info(
          "热点配图复用 articleId={} jobId={} slot={}/{} kind={} sequenceNo={} imageId={}",
          articleId,
          jobId,
          slot.slotIndex(),
          slot.totalSlots(),
          slot.kind().getValue(),
          slot.sequenceNo(),
          candidate.getId());
      return candidate;
    }
    // 仅对真正开生的槽位报进度：展示「当前是第几张。
    renewLease(jobId);
    reportCurrentSlotProgress(jobId, slot);
    try {
      GeneratedImageView created = generateAndPersist(
          articleId, jobId, slot.kind(), slot.sequenceNo(), prompt, size, width, height);
      renewLease(jobId);
      return created;
    } catch (Exception exception) {
      // 失败信息写清步骤、槽位与可读原因，避免把英文超时原文抛到执行页
      throw new IllegalStateException(formatSlotFailure(slot, exception), exception);
    }
  }

  /**
   * 组装配图槽位失败的中文说明。
   *
   * @param slot      当前槽位
   * @param exception 原始异常
   * @return 用户可读中文
   */
  static String formatSlotFailure(HotNewsImageSlot slot, Exception exception) {
    String reason = summarizeImageFailureReason(exception);
    // 步骤标题已在执行轨迹上，此处只写第几张与原因
    return "第 "
        + slot.slotIndex()
        + "/"
        + slot.totalSlots()
        + " 张未生成成功。"
        + reason;
  }

  /**
   * 将生图异常压成简短中文原因。
   *
   * @param exception 原始异常
   * @return 原因文案
   */
  static String summarizeImageFailureReason(Throwable exception) {
    String raw = "";
    Throwable cursor = exception;
    while (cursor != null) {
      if (StrUtil.isNotBlank(cursor.getMessage())) {
        raw = cursor.getMessage();
        break;
      }
      cursor = cursor.getCause();
    }
    String lower = StrUtil.nullToEmpty(raw).toLowerCase();
    if (lower.contains("read timed out")
        || lower.contains("timed out")
        || lower.contains("timeout")
        || lower.contains("sockettimeout")) {
      return "原因：配图服务请求超时，请稍后重新执行失败步骤。";
    }
    if (lower.contains("connection refused")
        || lower.contains("connect timed out")
        || lower.contains("unknownhost")
        || lower.contains("network is unreachable")) {
      return "原因：连不上配图服务，请检查网络后重新执行。";
    }
    if (StrUtil.isNotBlank(raw) && looksMostlyChinese(raw) && raw.length() < 160) {
      return "原因：" + raw.strip();
    }
    return "原因：配图生成失败，请重新执行失败步骤。";
  }

  /**
   * 文本是否以汉字为主。
   *
   * @param text 文本
   * @return 汉字不少于字母则个true
   */
  private static boolean looksMostlyChinese(String text) {
    int chinese = 0;
    int letters = 0;
    for (int index = 0; index < text.length(); index++) {
      char ch = text.charAt(index);
      if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
        chinese++;
      } else if (Character.isLetter(ch)) {
        letters++;
      }
    }
    return chinese > 0 && chinese >= letters;
  }

  /**
   * 配图文件是否仍可读取。
   *
   * @param imageId 配图 ID
   * @return 文件存在则为 true
   */
  private boolean imageFileExists(String imageId) {
    if (StrUtil.isBlank(imageId)) {
      return false;
    }
    try {
      Path path = content.getImageFile(imageId);
      return path != null && Files.isRegularFile(path);
    } catch (Exception ignored) {
      // 再试运行目录下的直落盘路径
      try {
        return Files.isRegularFile(storage.generatedImagesDir().resolve(imageId + ".png"));
      } catch (Exception ignoredAgain) {
        return false;
      }
    }
  }

  /**
   * 续租，避免串行生图超时被二次认领。
   *
   * @param jobId 任务 ID
   */
  private void renewLease(String jobId) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    jobs.renewLease(jobId, properties.getWorkerLeaseSeconds());
  }

  /**
   * 上报当前正在生成的槽位标识（第 N/M 张）。
   *
   * @param jobId 任务 ID
   * @param slot  当前槽位
   */
  private void reportCurrentSlotProgress(String jobId, HotNewsImageSlot slot) {
    if (StrUtil.isBlank(jobId) || slot == null || slot.totalSlots() <= 0) {
      return;
    }
    int progress = 70 + Math.round(slot.slotIndex() * 20f / slot.totalSlots());
    jobs.updateProgress(
        jobId,
        HotNewsWorkflowNodeType.GENERATE_IMAGES.getValue(),
        Math.min(90, progress),
        "正在生成配图 " + slot.slotIndex() + "/" + slot.totalSlots() + " 张");
  }

  /**
   * 组合 Skill 与原稿提示词为最终生图提示（画面要素已写在提示词内，不再硬拼约束）。
   *
   * @param skillPrompt Skill 片段
   * @param basePrompt  原稿或兜底提示词
   * @param size        尺寸
   * @return 完整提示词
   */
  private static String buildImagePrompt(String skillPrompt, String basePrompt, String size) {
    return """
        %s
        %s
        尺寸 %s。不要出现「第 N 课」字样。
        """.formatted(
        StrUtil.nullToEmpty(skillPrompt),
        StrUtil.nullToEmpty(basePrompt).strip(),
        size)
        .strip();
  }

  /**
   * 为封面提示词补强主题可读与点击吸引力。
   *
   * @param prompt     已组合的生图提示词
   * @param title      文章标题
   * @param coreThesis 骨架核心论点
   * @param coverBrief 封面构图要点
   * @return 补强后的封面提示词
   */
  private static String enrichCoverPrompt(
      String prompt, String title, String coreThesis, String coverBrief) {
    StringBuilder enriched = new StringBuilder(StrUtil.nullToEmpty(prompt).strip());
    enriched.append('\n').append(HotNewsMarkdownUtil.COVER_THEME_CONSTRAINT);
    if (StrUtil.isNotBlank(title)) {
      enriched.append(" 文章标题：「").append(title.strip()).append("」。");
    }
    if (StrUtil.isNotBlank(coreThesis)) {
      enriched.append(" 核心论点（须用画面直观表达）：").append(coreThesis.strip()).append('。');
    }
    if (StrUtil.isNotBlank(coverBrief) && !StrUtil.contains(prompt, coverBrief)) {
      enriched.append(" 封面构图：").append(coverBrief.strip()).append('。');
    }
    return enriched.toString();
  }

  /**
   * 调用图像模型、落盘并持久化元数据。
   *
   * @param articleId  文章 ID
   * @param jobId      任务 ID
   * @param kind       配图种类
   * @param sequenceNo 序号（封面为 0）
   * @param prompt     生图提示词
   * @param size       尺寸字符串
   * @param width      宽
   * @param height     高
   * @return 落库后的图片视图
   */
  private GeneratedImageView generateAndPersist(
      String articleId,
      String jobId,
      HotNewsImageKind kind,
      int sequenceNo,
      String prompt,
      String size,
      int width,
      int height) {
    var aiResult = images.generate(new ImageGenerateRequest(prompt, size, false));
    String base64Payload = aiResult.getB64Json();
    if (StrUtil.isBlank(base64Payload)) {
      throw new IllegalStateException("配图生成返回空 b64，kind=" + kind.getValue());
    }
    byte[] pngBytes = Base64.getDecoder().decode(base64Payload);
    String imageId = IdsUtil.newId();
    try {
      Path file = storage.generatedImagesDir().resolve(imageId + ".png");
      Files.write(file, pngBytes);
    } catch (Exception exception) {
      throw new IllegalStateException("配图落盘失败：" + exception.getMessage(), exception);
    }
    // 热点配图绑定公众号轨产出 id
    String documentId = workStore.wechatOutputId(articleId);
    return content.persistGeneratedImage(PersistGeneratedImageRequest.builder()
        .id(imageId)
        .articleId(articleId)
        .documentId(documentId)
        .kind(kind.getValue())
        .sequenceNo(sequenceNo)
        .prompt(prompt)
        .model(aiResult.getModel())
        .size(size)
        .outputFormat("png")
        .filePath("generated-images/" + imageId + ".png")
        .contentHash(sha256(pngBytes))
        .width(width)
        .height(height)
        .jobId(jobId)
        .build());
  }

  /**
   * 转为图状态用的配图摘要。
   *
   * @param view 落库视图
   * @return 摘要
   */
  private static HotNewsGeneratedImageSummary toSummary(GeneratedImageView view) {
    return HotNewsGeneratedImageSummary.builder()
        .id(view.getId())
        .kind(view.getKind())
        .sequenceNo(view.getSequenceNo())
        .build();
  }

  /**
   * 计算内容 SHA-256 十六进制摘要。
   *
   * @param bytes 文件字节
   * @return hex；算法不可用时返回空个
   */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ignored) {
      return "";
    }
  }
}
