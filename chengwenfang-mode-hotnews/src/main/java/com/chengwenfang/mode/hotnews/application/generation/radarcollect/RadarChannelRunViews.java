package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelRunView;
import com.chengwenfang.sourcing.facade.enums.ChannelRunStatus;
import java.time.LocalDateTime;

/**
 * 雷达渠道抓取结果 → {@link HotNewsChannelRunView}，供 finishRun 落库与覆盖率统计。
 */
final class RadarChannelRunViews {
  private RadarChannelRunViews() {}

  /**
   * 将单渠道抓取结果转为子运行视图。
   *
   * @param channelId   渠道 ID
   * @param channelName 渠道名
   * @param channelType 渠道类型
   * @param ok          上游是否成功
   * @param errorCode   失败码；成功可空
   * @param rawCount    上游返回条数（过滤前）
   * @param accepted    进入候选条数
   * @param finishedAt  结束时间；null 则用当前时间
   * @return 子运行视图
   */
  static HotNewsChannelRunView fromFetchOutcome(
      String channelId,
      String channelName,
      String channelType,
      boolean ok,
      String errorCode,
      int rawCount,
      int accepted,
      LocalDateTime finishedAt) {
    LocalDateTime end = finishedAt == null ? LocalDateTime.now() : finishedAt;
    return HotNewsChannelRunView.builder()
        .channelId(channelId)
        .channelName(StrUtil.nullToEmpty(channelName))
        .channelType(StrUtil.nullToEmpty(channelType))
        .status(ok ? ChannelRunStatus.SUCCEEDED.getValue() : ChannelRunStatus.FAILED.getValue())
        .itemsCount(Math.max(0, rawCount))
        .newItems(Math.max(0, accepted))
        .errorCode(ok ? null : errorCode)
        .errorMessage(ok ? null : StrUtil.blankToDefault(errorCode, "channel_fetch_failed"))
        .startedAt(end)
        .finishedAt(end)
        .build();
  }
}
