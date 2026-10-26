package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.domain.entity.WorkEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.domain.entity.WorkOutputVersionEntity;
import com.chengwenfang.content.facade.WorkContentStore;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.constant.OutputFormatCodes;
import com.chengwenfang.content.facade.constant.OutputTrackKinds;
import com.chengwenfang.content.facade.dto.WorkOutputRef;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.infrastructure.mapper.WorkMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputVersionMapper;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作品产出正文：文件 + {@code work_output_versions}。
 */
@Service
@RequiredArgsConstructor
public class WorkContentStoreImpl implements WorkContentStore {
  private final WorkOutputMapper outputMapper;
  private final WorkOutputVersionMapper versionMapper;
  private final WorkMapper workMapper;
  private final FileFacade files;

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
