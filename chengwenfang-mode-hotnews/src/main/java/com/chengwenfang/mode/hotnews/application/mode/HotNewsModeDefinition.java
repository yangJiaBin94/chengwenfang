package com.chengwenfang.mode.hotnews.application.mode;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.content.application.WorkContentStoreImpl;
import com.chengwenfang.content.domain.entity.WorkEntity;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.constant.OutputFormatCodes;
import com.chengwenfang.content.facade.dto.DefaultOutputSpec;
import com.chengwenfang.content.facade.dto.ModeWorkNavInfo;
import com.chengwenfang.content.facade.dto.WorkExtensionPayload;
import com.chengwenfang.content.facade.spi.ModeDefinition;
import com.chengwenfang.content.infrastructure.mapper.WorkMapper;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsWorkEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsWorkSourceEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsWorkMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsWorkSourceMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点资讯模式定义：默认双轨产出（原稿/公众号）与 hotnews_works 扩展。
 */
@Component
@RequiredArgsConstructor
public class HotNewsModeDefinition implements ModeDefinition {

  /** 默认产出组合（标题不加后缀）。 */
  private static final List<DefaultOutputSpec> DEFAULT_OUTPUTS = List.of(
      DefaultOutputSpec.builder()
          .channelCode(ChannelCodes.MASTER)
          .formatCode(OutputFormatCodes.ARTICLE)
          .titleSuffix("")
          .build(),
      DefaultOutputSpec.builder()
          .channelCode(ChannelCodes.WECHAT_MP)
          .formatCode(OutputFormatCodes.ARTICLE)
          .titleSuffix("")
          .build());

  /** 作品表。 */
  private final WorkMapper workMapper;
  /** 热点作品扩展。 */
  private final HotnewsWorkMapper hotnewsWorkMapper;
  /** 作品来源快照。 */
  private final HotnewsWorkSourceMapper sourceMapper;
  /** 产出指针。 */
  private final WorkContentStoreImpl workContents;

  /**
   * {@inheritDoc}
   */
  @Override
  public String modeCode() {
    return ContentModeIds.HOT_NEWS_CODE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<DefaultOutputSpec> defaultOutputs() {
    return DEFAULT_OUTPUTS;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void createExtension(WorkExtensionPayload payload) {
    if (payload == null || StrUtil.isBlank(payload.getWorkId())) {
      return;
    }
    WorkExtensionPayload.HotNewsWorkExtension hotNews = payload.getHotNews();
    if (hotNews == null) {
      return;
    }
    String status = StrUtil.blankToDefault(payload.getStatus(), HotNewsArticleStatuses.DRAFT);
    HotnewsWorkEntity existing = hotnewsWorkMapper.selectById(payload.getWorkId());
    if (existing == null) {
      hotnewsWorkMapper.insert(HotnewsWorkEntity.builder()
          .workId(payload.getWorkId())
          .topicId(hotNews.getTopicId())
          .briefId(hotNews.getBriefId())
          .status(status)
          .build());
      return;
    }
    existing.setTopicId(StrUtil.blankToDefault(hotNews.getTopicId(), existing.getTopicId()));
    if (hotNews.getBriefId() != null) {
      existing.setBriefId(hotNews.getBriefId());
    }
    existing.setStatus(status);
    hotnewsWorkMapper.updateById(existing);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteExtension(String workId) {
    if (StrUtil.isBlank(workId)) {
      return;
    }
    sourceMapper.delete(new LambdaQueryWrapper<HotnewsWorkSourceEntity>()
        .eq(HotnewsWorkSourceEntity::getWorkId, workId));
    hotnewsWorkMapper.deleteById(workId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ModeWorkNavInfo resolveNavInfo(String workId) {
    if (StrUtil.isBlank(workId)) {
      return null;
    }
    WorkEntity work = workMapper.selectById(workId);
    if (work == null) {
      return null;
    }
    HotnewsWorkEntity hot = hotnewsWorkMapper.selectById(workId);
    return ModeWorkNavInfo.builder()
        .workId(workId)
        .title(StrUtil.nullToEmpty(work.getTitle()))
        .themeId(work.getThemeId())
        .draftOutputId(workContents.findOutputId(workId, ChannelCodes.MASTER))
        .wechatOutputId(workContents.findOutputId(workId, ChannelCodes.WECHAT_MP))
        .topicId(hot == null ? null : hot.getTopicId())
        .build();
  }
}
