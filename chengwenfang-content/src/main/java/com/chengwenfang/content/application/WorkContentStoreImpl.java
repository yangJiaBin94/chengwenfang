package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.domain.entity.WorkEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.domain.entity.WorkOutputVersionEntity;
import com.chengwenfang.content.facade.WorkContentStore;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.constant.OutputFormatCodes;
import com.chengwenfang.content.facade.constant.OutputTrackKinds;
import com.chengwenfang.content.facade.dto.WorkOutputRef;
import com.chengwenfang.content.facade.dto.view.WorkOutputKnowledgeSnapshot;
import com.chengwenfang.content.facade.event.WorkOutputContentChangedEvent;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.infrastructure.mapper.ContentThemeMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputVersionMapper;
import java.util.regex.Pattern;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.identity.facade.OwnerScope;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作品产出正文：文件 + {@code work_output_versions}。
 */
@Service
@RequiredArgsConstructor
public class WorkContentStoreImpl implements WorkContentStore {

  /** 课程作品技术 ID：course-{themeCode}-{uuid…}。 */
  private static final Pattern COURSE_TECHNICAL_ID =
      Pattern.compile("(?i)^course-[a-z0-9_]+-[0-9a-f-]{8,}.*$");

  private final WorkOutputMapper outputMapper;
  private final WorkOutputVersionMapper versionMapper;
  private final WorkMapper workMapper;
  private final ContentThemeMapper contentThemeMapper;
  private final FileFacade files;
  private final ApplicationEventPublisher eventPublisher;
  /** 用户数据范围。 */
  private final OwnerScope ownerScope;

  @Override
  public String readCurrent(String outputId) {
    WorkOutputEntity output = requireOutput(outputId);
    int versionNo = output.getCurrentVersionNo() == null ? 0 : output.getCurrentVersionNo();
    if (versionNo <= 0) {
      return "";
    }
    return readVersion(outputId, versionNo);
  }

  @Override
  public String readVersion(String outputId, int versionNo) {
    if (StrUtil.isBlank(outputId) || versionNo <= 0) {
      return "";
    }
    WorkOutputVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<WorkOutputVersionEntity>()
        .eq(WorkOutputVersionEntity::getOutputId, outputId)
        .eq(WorkOutputVersionEntity::getVersionNo, versionNo)
        .last("LIMIT 1"));
    if (version != null && StrUtil.isNotBlank(version.getBodyFileId())) {
      return files.readText(version.getBodyFileId());
    }
    return "";
  }

  @Override
  @Transactional
  public int appendVersion(String outputId, String text) {
    return appendVersion(outputId, text, "edit", null, null);
  }

  /**
   * 追加版本并记录来源/原因。
   *
   * @param outputId      产出 id
   * @param text          正文；null 当空串
   * @param source        来源
   * @param reason        原因
   * @param workflowRunId 工作流运行；可空
   * @return 新版本号
   */
  @Transactional
  public int appendVersion(
      String outputId, String text, String source, String reason, String workflowRunId) {
    WorkOutputEntity output = requireOutput(outputId);
    String body = text == null ? "" : text;
    int nextNo = (output.getCurrentVersionNo() == null ? 0 : output.getCurrentVersionNo()) + 1;
    String objectKey = "works/" + output.getWorkId() + "/outputs/" + outputId + "/v" + nextNo + ".md";
    StoredFileView stored = files.writeText(objectKey, "v" + nextNo + ".md", body);
    String contentHash = ContentEntityUtil.sha256(body);
    int wordCount = body.replaceAll("\\s+", "").length();
    LocalDateTime now = LocalDateTime.now();
    versionMapper.insert(WorkOutputVersionEntity.builder()
        .id(IdsUtil.newId())
        .outputId(outputId)
        .versionNo(nextNo)
        .bodyFileId(stored.getId())
        .contentHash(contentHash)
        .source(source)
        .reason(reason)
        .workflowRunId(workflowRunId)
        .createdAt(now)
        .build());
    output.setCurrentVersionNo(nextNo);
    output.setContentHash(contentHash);
    output.setWordCount(wordCount);
    long token = output.getVersionToken() == null ? 0L : output.getVersionToken();
    output.setVersionToken(token + 1);
    output.setUpdatedAt(now);
    outputMapper.updateById(output);
    // 仅主轨原稿入知识库；公众号轨不入库（课程/热点同口径）
    if (ChannelCodes.MASTER.equals(output.getChannelCode())) {
      eventPublisher.publishEvent(WorkOutputContentChangedEvent.builder()
          .outputId(outputId)
          .workId(output.getWorkId())
          .channelCode(output.getChannelCode())
          .versionNo(nextNo)
          .contentHash(contentHash)
          .build());
    }
    return nextNo;
  }

  /**
   * 确保产出壳行存在（无则插入空草案）；格式默认 article。
   *
   * @param outputId    产出 id
   * @param workId      作品 id
   * @param channelCode 渠道
   * @param title       标题
   * @param status      状态
   * @return 产出实体
   */
  @Transactional
  public WorkOutputEntity ensureOutput(
      String outputId, String workId, String channelCode, String title, String status) {
    return ensureOutput(outputId, workId, channelCode, OutputFormatCodes.ARTICLE, title, status);
  }

  /**
   * 确保产出壳行存在（无则插入空草案）。
   *
   * @param outputId    产出 id
   * @param workId      作品 id
   * @param channelCode 渠道
   * @param formatCode  格式
   * @param title       标题
   * @param status      状态
   * @return 产出实体
   */
  @Transactional
  public WorkOutputEntity ensureOutput(
      String outputId,
      String workId,
      String channelCode,
      String formatCode,
      String title,
      String status) {
    WorkOutputEntity existing = outputMapper.selectById(outputId);
    if (existing != null) {
      return existing;
    }
    LocalDateTime now = LocalDateTime.now();
    WorkOutputEntity output = WorkOutputEntity.builder()
        .id(outputId)
        .workId(workId)
        .channelCode(channelCode)
        .formatCode(StrUtil.blankToDefault(formatCode, OutputFormatCodes.ARTICLE))
        .title(StrUtil.blankToDefault(title, ""))
        .status(StrUtil.blankToDefault(status, "draft"))
        .currentVersionNo(0)
        .wordCount(0)
        .versionToken(0L)
        .createdAt(now)
        .updatedAt(now)
        .build();
    outputMapper.insert(output);
    return output;
  }

  /**
   * 按作品与渠道查找产出 id。
   *
   * @param workId      作品 id
   * @param channelCode 渠道
   * @return 产出 id；缺失 null
   */
  public String findOutputId(String workId, String channelCode) {
    if (StrUtil.isBlank(workId) || StrUtil.isBlank(channelCode)) {
      return null;
    }
    WorkOutputEntity output = outputMapper.selectOne(new LambdaQueryWrapper<WorkOutputEntity>()
        .eq(WorkOutputEntity::getWorkId, workId)
        .eq(WorkOutputEntity::getChannelCode, channelCode)
        .eq(WorkOutputEntity::getFormatCode, OutputFormatCodes.ARTICLE)
        .last("LIMIT 1"));
    return output == null ? null : output.getId();
  }

  /**
   * 按产出 id 反查身份；缺失返回 null。
   *
   * @param outputId 产出 id
   * @return 身份投影；无则 null
   */
  public WorkOutputRef findOutputRef(String outputId) {
    if (StrUtil.isBlank(outputId)) {
      return null;
    }
    WorkOutputEntity output = outputMapper.selectById(outputId);
    if (output == null) {
      return null;
    }
    WorkEntity work = workMapper.selectById(output.getWorkId());
    if (work == null) {
      return null;
    }
    ownerScope.assertCanAccess(work.getOwnerUserId());
    boolean hotNews = ContentModeIds.isHotNews(work.getModeCode(), work.getModeCode());
    String kind = kindFromChannel(output.getChannelCode(), hotNews);
    return new WorkOutputRef(
        output.getId(),
        work.getId(),
        hotNews,
        kind,
        output.getChannelCode());
  }

  /**
   * 解析下游公众号轨产出 id：入参已是产出则原样返回；热点作品取 wechat_mp 轨；课程取入参或 wechat 轨。
   *
   * @param workOrOutputId 作品 id 或产出 id
   * @return 产出 id；无法解析时 null
   */
  /**
   * 加载主轨产出知识快照。
   *
   * @param outputId 产出 ID
   * @return 快照；不可用则 null
   */
  public WorkOutputKnowledgeSnapshot loadMasterKnowledgeSnapshot(String outputId) {
    if (StrUtil.isBlank(outputId)) {
      return null;
    }
    WorkOutputEntity output = outputMapper.selectById(outputId);
    if (output == null || !ChannelCodes.MASTER.equals(output.getChannelCode())) {
      return null;
    }
    WorkEntity work = workMapper.selectById(output.getWorkId());
    if (work == null) {
      return null;
    }
    ownerScope.assertCanAccess(work.getOwnerUserId());
    return buildKnowledgeSnapshot(output);
  }

  /**
   * 列出当前用户可见的、有正文的主轨产出快照。
   *
   * @return 快照列表
   */
  public List<WorkOutputKnowledgeSnapshot> listMasterKnowledgeSnapshots() {
    LambdaQueryWrapper<WorkOutputEntity> outputQuery = new LambdaQueryWrapper<WorkOutputEntity>()
        .eq(WorkOutputEntity::getChannelCode, ChannelCodes.MASTER)
        .gt(WorkOutputEntity::getCurrentVersionNo, 0);
    if (!ownerScope.currentUserIsAdmin()) {
      String ownerId = ownerScope.currentOwnerUserId().orElse(null);
      if (StrUtil.isBlank(ownerId)) {
        return List.of();
      }
      List<WorkEntity> ownedWorks = workMapper.selectList(
          new LambdaQueryWrapper<WorkEntity>()
              .eq(WorkEntity::getOwnerUserId, ownerId)
              .select(WorkEntity::getId));
      Set<String> workIds = ownedWorks.stream()
          .filter(work -> work != null && StrUtil.isNotBlank(work.getId()))
          .map(WorkEntity::getId)
          .collect(Collectors.toSet());
      if (workIds.isEmpty()) {
        return List.of();
      }
      outputQuery.in(WorkOutputEntity::getWorkId, workIds);
    }
    List<WorkOutputEntity> outputs = outputMapper.selectList(outputQuery);
    List<WorkOutputKnowledgeSnapshot> snapshots = new ArrayList<>();
    for (WorkOutputEntity output : outputs) {
      WorkOutputKnowledgeSnapshot snapshot = buildKnowledgeSnapshot(output);
      if (snapshot != null) {
        snapshots.add(snapshot);
      }
    }
    return snapshots;
  }

  /**
   * 由产出行组装知识快照（正文非空才返回）。
   *
   * @param output 产出实体
   * @return 快照；正文为空则 null
   */
  private WorkOutputKnowledgeSnapshot buildKnowledgeSnapshot(WorkOutputEntity output) {
    if (output == null) {
      return null;
    }
    int versionNo = output.getCurrentVersionNo() == null ? 0 : output.getCurrentVersionNo();
    if (versionNo <= 0) {
      return null;
    }
    String content = readCurrent(output.getId());
    if (StrUtil.isBlank(content)) {
      return null;
    }
    WorkEntity work = workMapper.selectById(output.getWorkId());
    String modeCode = work == null ? null : work.getModeCode();
    String themeId = work == null ? null : work.getThemeId();
    String rawTitle = StrUtil.firstNonBlank(output.getTitle(), work == null ? null : work.getTitle());
    String title = resolveKnowledgeTitle(
        rawTitle,
        work == null ? null : work.getId(),
        themeId,
        content);
    return WorkOutputKnowledgeSnapshot.builder()
        .outputId(output.getId())
        .workId(output.getWorkId())
        .modeCode(modeCode)
        .themeId(themeId)
        .title(title)
        .channelCode(output.getChannelCode())
        .currentVersionNo(versionNo)
        .contentHash(output.getContentHash())
        .content(content)
        .deepLink(buildKnowledgeDeepLink(output.getId(), output.getWorkId(), modeCode, themeId))
        .build();
  }

  /**
   * 知识库入库标题：技术 ID 换成正文首标题或主题名。
   *
   * @param rawTitle 产出/作品原标题
   * @param workId   作品 ID
   * @param themeId  主题 ID
   * @param content  正文
   * @return 可读标题
   */
  private String resolveKnowledgeTitle(
      String rawTitle, String workId, String themeId, String content) {
    String raw = StrUtil.nullToEmpty(rawTitle).trim();
    String suffix = "";
    String base = raw;
    if (raw.endsWith(" 原稿")) {
      suffix = "原稿";
      base = raw.substring(0, raw.length() - 3).trim();
    } else if (raw.endsWith(" 公众号")) {
      suffix = "公众号";
      base = raw.substring(0, raw.length() - 4).trim();
    }
    if (!isTechnicalWorkTitle(base, workId)) {
      return StrUtil.isBlank(suffix) ? base : base + " · " + suffix;
    }
    String fromHeading = firstMarkdownHeading(content);
    if (StrUtil.isNotBlank(fromHeading)) {
      return StrUtil.isBlank(suffix) ? fromHeading : fromHeading + " · " + suffix;
    }
    String themeName = resolveThemeName(themeId);
    if (StrUtil.isNotBlank(themeName)) {
      return StrUtil.isBlank(suffix) ? themeName : themeName + " · " + suffix;
    }
    return StrUtil.blankToDefault(suffix, "未命名文档");
  }

  /**
   * 判断作品标题是否为技术 ID。
   *
   * @param title  基名
   * @param workId 作品 ID
   * @return 技术标题则为 true
   */
  private static boolean isTechnicalWorkTitle(String title, String workId) {
    String text = StrUtil.nullToEmpty(title).trim();
    if (StrUtil.isBlank(text)) {
      return true;
    }
    if (StrUtil.isNotBlank(workId) && text.equals(workId.trim())) {
      return true;
    }
    return COURSE_TECHNICAL_ID.matcher(text).matches();
  }

  /**
   * 读取主题展示名。
   *
   * @param themeId 主题 ID
   * @return 名称
   */
  private String resolveThemeName(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    ContentThemeEntity theme = contentThemeMapper.selectById(themeId);
    return theme == null ? "" : StrUtil.nullToEmpty(theme.getName()).trim();
  }

  /**
   * 取 Markdown 首个标题行。
   *
   * @param markdown 正文
   * @return 标题
   */
  private static String firstMarkdownHeading(String markdown) {
    String text = StrUtil.nullToEmpty(markdown);
    if (StrUtil.isBlank(text)) {
      return "";
    }
    for (String line : text.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        String heading = trimmed.replaceFirst("^#+\\s*", "").trim();
        if (StrUtil.isNotBlank(heading)) {
          return heading;
        }
      }
    }
    return "";
  }

  /**
   * 按模式生成前端深链。
   *
   * @param outputId 产出 ID
   * @param workId   作品 ID
   * @param modeCode 模式 code
   * @param themeId  主题 ID
   * @return 路径
   */
  private String buildKnowledgeDeepLink(
      String outputId, String workId, String modeCode, String themeId) {
    if (ContentModeIds.isCourseSystem(modeCode, modeCode) && StrUtil.isNotBlank(workId)) {
      String themeCode = resolveThemeCode(themeId);
      if (StrUtil.isNotBlank(themeCode)) {
        return "/modes/course-system/themes/"
            + themeCode.replace('_', '-')
            + "/courses/"
            + workId;
      }
      return "/courses/" + workId;
    }
    if (ContentModeIds.isHotNews(modeCode, modeCode) && StrUtil.isNotBlank(workId)) {
      return "/modes/hot-news/articles/" + workId;
    }
    return "/knowledge?doc=" + outputId;
  }

  /**
   * 读取主题 code。
   *
   * @param themeId 主题 ID
   * @return code
   */
  private String resolveThemeCode(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    ContentThemeEntity theme = contentThemeMapper.selectById(themeId);
    return theme == null ? "" : StrUtil.nullToEmpty(theme.getCode()).trim();
  }

  public String resolvePublishingOutputId(String workOrOutputId) {
    if (StrUtil.isBlank(workOrOutputId)) {
      return null;
    }
    WorkOutputRef asOutput = findOutputRef(workOrOutputId);
    if (asOutput != null) {
      return asOutput.getOutputId();
    }
    WorkEntity work = workMapper.selectById(workOrOutputId);
    if (work == null) {
      return null;
    }
    ownerScope.assertCanAccess(work.getOwnerUserId());
    if (ContentModeIds.isHotNews(work.getModeCode(), work.getModeCode())) {
      return findOutputId(work.getId(), ChannelCodes.WECHAT_MP);
    }
    String wechat = findOutputId(work.getId(), ChannelCodes.WECHAT_MP);
    return StrUtil.isNotBlank(wechat) ? wechat : findOutputId(work.getId(), ChannelCodes.MASTER);
  }

  /**
   * 加载产出；缺失 404。
   *
   * @param outputId 产出 id
   * @return 实体
   */
  public WorkOutputEntity requireOutput(String outputId) {
    if (StrUtil.isBlank(outputId)) {
      throw new BusinessException("output_id_required", "产出 id 不能为空", 400);
    }
    WorkOutputEntity output = outputMapper.selectById(outputId);
    if (output == null) {
      throw new BusinessException("output_not_found", "产出不存在", 404);
    }
    return output;
  }

  /**
   * 由渠道映射内容轨 kind。
   *
   * @param channelCode 渠道
   * @param hotNews     是否热点
   * @return kind 字面量
   */
  private static String kindFromChannel(String channelCode, boolean hotNews) {
    boolean wechat = ChannelCodes.WECHAT_MP.equals(channelCode);
    if (hotNews) {
      return wechat ? OutputTrackKinds.HOT_WECHAT : OutputTrackKinds.HOT_DRAFT;
    }
    return wechat
        ? ArticleKind.WECHAT_ARTICLE.getValue()
        : ArticleKind.COURSE_DRAFT.getValue();
  }
}
