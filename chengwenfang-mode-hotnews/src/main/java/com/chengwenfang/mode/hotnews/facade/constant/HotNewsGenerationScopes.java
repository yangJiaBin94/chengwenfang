package com.chengwenfang.mode.hotnews.facade.constant;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.prompt.facade.enums.HotNewsWorkflowNodeType;

/**
 * 热点文章生成运行范围（与 generation-runs 请求 scope 一致）。
 * <p>{@link #IMAGES} 仅配图；{@link #WECHAT} 仅公众号轨（基于已有原稿），均不重跑原稿生成。
 */
public final class HotNewsGenerationScopes {
  private HotNewsGenerationScopes() {}

  /** 完整成文。 */
  public static final String FULL = "full";
  /** 仅原稿轨。 */
  public static final String DRAFT = "draft";
  /** 仅公众号轨。 */
  public static final String WECHAT = "wechat";
  /** 仅配套图片（含封面）。 */
  public static final String IMAGES = "images";
  /** 仅审核。 */
  public static final String AUDIT = "audit";

  /**
   * 是否为仅配图范围。
   *
   * @param scope 运行范围；可空
   * @return 匹配 {@link #IMAGES} 则为 true
   */
  public static boolean isImages(String scope) {
    return IMAGES.equalsIgnoreCase(StrUtil.trim(scope));
  }

  /**
   * 是否为仅公众号范围。
   *
   * @param scope 运行范围；可空
   * @return 匹配 {@link #WECHAT} 则为 true
   */
  public static boolean isWechat(String scope) {
    return WECHAT.equalsIgnoreCase(StrUtil.trim(scope));
  }

  /**
   * 规范化 scope；空白回落 full。
   *
   * @param scope 原始范围
   * @return 规范字面量
   */
  public static String normalize(String scope) {
    String trimmed = StrUtil.trim(scope);
    if (StrUtil.isBlank(trimmed)) {
      return FULL;
    }
    if (isImages(trimmed)) {
      return IMAGES;
    }
    if (DRAFT.equalsIgnoreCase(trimmed)) {
      return DRAFT;
    }
    if (isWechat(trimmed)) {
      return WECHAT;
    }
    if (AUDIT.equalsIgnoreCase(trimmed)) {
      return AUDIT;
    }
    return FULL;
  }

  /**
   * 当前 scope 下是否应执行该热点节点。
   * <p>images：跳过成文/审校，保留配图链路。
   * <p>wechat：跳过事实包、原稿生成/审校与配图，仅跑公众号生成/审校与提交。
   *
   * @param scope    运行范围
   * @param nodeType 节点类型
   * @return 应执行则为 true
   */
  public static boolean shouldExecute(String scope, HotNewsWorkflowNodeType nodeType) {
    if (nodeType == null) {
      return false;
    }
    if (isImages(scope)) {
      return switch (nodeType) {
        case LOAD_CONTEXT, LOAD_PROMPT_PACKS, PLAN_IMAGES, GENERATE_IMAGES, VALIDATE_IMAGES,
            FINAL_VALIDATION, COMMIT_VERSION -> true;
        default -> false;
      };
    }
    if (isWechat(scope)) {
      return switch (nodeType) {
        case LOAD_CONTEXT, LOAD_PROMPT_PACKS, ATTACH_EVIDENCE, GENERATE_WECHAT, REVIEW_WECHAT,
            FINAL_VALIDATION, COMMIT_VERSION -> true;
        default -> false;
      };
    }
    return true;
  }
}
