package com.chengwenfang.app.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.dto.view.ProviderPublicView;

/**
 * 系统就绪单项的判定，与探测方式分开，便于单测。
 */
public final class SystemReadinessRules {
  private SystemReadinessRules() {}

  /**
   * 提示词包数量是否达到内置期望，且不存在仅属于提示词包的体检问题。
   *
   * @param count         库内数量
   * @param expected      期望数量
   * @param builtinReady  体检是否全部通过
   * @param workflowReady 工作流是否通过
   * @return 提示词包项是否通过
   */
  public static boolean promptPacksReady(
      int count, int expected, boolean builtinReady, boolean workflowReady) {
    boolean countOk = expected > 0 && count >= expected;
    // 工作流单独失败时，数量够则提示词包仍算通过
    return countOk && (builtinReady || !workflowReady);
  }

  /**
   * 组装一条检查项。
   *
   * @param key      稳定键
   * @param label    标题
   * @param section  分组
   * @param status   状态
   * @param detail   说明
   * @param required 是否计入默认档
   * @return 检查项
   */
  public static SystemReadinessItem item(
      String key,
      String label,
      SystemReadinessSection section,
      SystemReadinessStatus status,
      String detail,
      boolean required) {
    return SystemReadinessItem.builder()
        .key(key)
        .label(label)
        .section(section)
        .status(status)
        .detail(detail)
        .required(required)
        .build();
  }

  /**
   * Qdrant 必须真连上；内存实现或健康检查失败都不算就绪。
   *
   * @param enabled  配置是否启用
   * @param degraded 向量库是否降级（内存实现恒为 true）
   * @param host     主机
   * @param grpcPort gRPC 端口
   * @return 检查项
   */
  public static SystemReadinessItem qdrant(boolean enabled, boolean degraded, String host, int grpcPort) {
    String endpoint = StrUtil.blankToDefault(host, "127.0.0.1") + ":" + grpcPort;
    if (enabled && !degraded) {
      return item(
          SystemReadinessKeys.QDRANT,
          "Qdrant",
          SystemReadinessSection.DEFAULT_PROFILE,
          SystemReadinessStatus.PASS,
          "已连接 " + endpoint,
          true);
    }
    String detail = enabled
        ? "未连通 " + endpoint + "。内存向量库不算就绪，请确认 docker compose 已拉起 qdrant。"
        : "未启用，当前是内存向量库，不算就绪。";
    return item(
        SystemReadinessKeys.QDRANT,
        "Qdrant",
        SystemReadinessSection.DEFAULT_PROFILE,
        SystemReadinessStatus.FAIL,
        detail,
        true);
  }

  /**
   * API Key 是否已配置（不读取明文）。
   *
   * @param key           稳定键
   * @param label         标题
   * @param provider      公开配置；可空
   * @param missingDetail 未配置时的说明
   * @return 检查项；缺 Key 为失败
   */
  public static SystemReadinessItem apiKey(
      String key, String label, ProviderPublicView provider, String missingDetail) {
    boolean configured = provider != null && provider.isApiKeyConfigured();
    boolean enabled = provider == null || provider.getEnabled() == null || Boolean.TRUE.equals(provider.getEnabled());
    boolean modelPresent = provider != null && StrUtil.isNotBlank(provider.getModel());
    if (configured && enabled && modelPresent) {
      return item(
          key,
          label,
          SystemReadinessSection.DEFAULT_PROFILE,
          SystemReadinessStatus.PASS,
          "已配置（密钥不回显）",
          true);
    }
    String detail;
    if (!configured) {
      detail = missingDetail;
    } else if (!enabled) {
      detail = "已保存密钥但当前供应商被关闭。";
    } else {
      detail = "已保存密钥但未填写模型名，测试连接前不会当成可用。";
    }
    return item(
        key,
        label,
        SystemReadinessSection.DEFAULT_PROFILE,
        SystemReadinessStatus.FAIL,
        detail,
        true);
  }

  /**
   * 热点 CLI：没装好记为未加装，不把默认档判失败。
   *
   * @param key      稳定键
   * @param label    标题
   * @param present  解释器文件是否存在
   * @param importsOk 依赖模块是否可导入
   * @param python   展示用路径
   * @param purpose  缺它时会少什么
   * @return 检查项
   */
  public static SystemReadinessItem cli(
      String key,
      String label,
      boolean present,
      boolean importsOk,
      String python,
      String purpose) {
    if (present && importsOk) {
      return item(
          key,
          label,
          SystemReadinessSection.HOT_NEWS,
          SystemReadinessStatus.PASS,
          "已安装 " + python,
          false);
    }
    String detail = present
        ? "解释器在，但依赖未装齐（" + python + "）。" + purpose
        : "未加装（期望 " + python + "）。" + purpose;
    return item(
        key,
        label,
        SystemReadinessSection.HOT_NEWS,
        SystemReadinessStatus.NOT_INSTALLED,
        detail,
        false);
  }

  /**
   * 热点容器：连不上记为未加装。
   *
   * @param key       稳定键
   * @param label     标题
   * @param reachable 是否连通
   * @param endpoint  探测地址
   * @param purpose   缺它时会少什么
   * @return 检查项
   */
  public static SystemReadinessItem addonService(
      String key, String label, boolean reachable, String endpoint, String purpose) {
    if (reachable) {
      return item(
          key,
          label,
          SystemReadinessSection.HOT_NEWS,
          SystemReadinessStatus.PASS,
          "已连通 " + endpoint,
          false);
    }
    return item(
        key,
        label,
        SystemReadinessSection.HOT_NEWS,
        SystemReadinessStatus.NOT_INSTALLED,
        "未加装（" + endpoint + "）。" + purpose,
        false);
  }

  /**
   * Graph / Agent：默认关闭单独标出；打开后才看是否可用。
   *
   * @param key         稳定键
   * @param label       标题
   * @param enabled     应用开关
   * @param available   打开时是否可用
   * @param offDetail   关闭说明
   * @param onDetail    打开且可用的说明
   * @param downDetail  打开但不可用的说明
   * @return 检查项
   */
  public static SystemReadinessItem optionalSwitch(
      String key,
      String label,
      boolean enabled,
      boolean available,
      String offDetail,
      String onDetail,
      String downDetail) {
    if (!enabled) {
      return item(
          key,
          label,
          SystemReadinessSection.OPTIONAL,
          SystemReadinessStatus.OPTIONAL_OFF,
          offDetail,
          false);
    }
    if (available) {
      return item(
          key,
          label,
          SystemReadinessSection.OPTIONAL,
          SystemReadinessStatus.PASS,
          onDetail,
          false);
    }
    return item(
        key,
        label,
        SystemReadinessSection.OPTIONAL,
        SystemReadinessStatus.FAIL,
        downDetail,
        false);
  }
}
