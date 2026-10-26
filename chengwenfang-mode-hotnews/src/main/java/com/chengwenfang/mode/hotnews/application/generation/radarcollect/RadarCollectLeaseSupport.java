package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 雷达采集图内租约续期：从轻量状态取出管道注入的 {@link Runnable}。
 */
@Slf4j
public final class RadarCollectLeaseSupport {
  private RadarCollectLeaseSupport() {}

  /**
   * 续期一次；回调缺失或失败时仅打日志。
   *
   * @param lightState 图轻量状态
   */
  public static void renew(Map<String, Object> lightState) {
    if (lightState == null) {
      return;
    }
    Object callback = lightState.get(RadarCollectGraphStateKeys.LEASE_RENEWAL);
    if (!(callback instanceof Runnable runnable)) {
      return;
    }
    try {
      runnable.run();
    } catch (Exception exception) {
      log.warn("雷达采集续租失败 err={}", exception.getMessage());
    }
  }
}
