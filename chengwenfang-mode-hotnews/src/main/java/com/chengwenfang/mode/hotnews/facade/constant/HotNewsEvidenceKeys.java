package com.chengwenfang.mode.hotnews.facade.constant;

import cn.hutool.core.util.StrUtil;

/**
 * 成文期佐证截屏相关常量（占位、上限、存储键前缀）。
 */
public final class HotNewsEvidenceKeys {
  private HotNewsEvidenceKeys() {}

  /** 单篇最多佐证截屏张数。 */
  public static final int MAX_SHOTS = 3;

  /** 佐证图 Markdown 占位前缀：{@code [[HOT_NEWS_EVIDENCE:{fileId}]]}。 */
  public static final String TOKEN_PREFIX = "[[HOT_NEWS_EVIDENCE:";

  /** 佐证图占位后缀。 */
  public static final String TOKEN_SUFFIX = "]]";

  /** 本地文件图路径前缀（发布侧可解析）。 */
  public static final String FILES_API_PREFIX = "/api/files/";

  /** 落盘 objectKey 前缀：{@code hotnews/evidence/{jobId}/…}。 */
  public static final String OBJECT_KEY_PREFIX = "hotnews/evidence/";

  /** 视频抽帧落盘前缀：{@code hotnews/video-frames/{itemId}/…}。 */
  public static final String VIDEO_FRAME_OBJECT_KEY_PREFIX = "hotnews/video-frames/";

  /** 单条视频最多保留抽帧张数（写入 stored_files）。 */
  public static final int MAX_PERSISTED_VIDEO_FRAMES = 8;

  /** 佐证来源标记：网页正文定位后截屏。 */
  public static final String EXTRACT_SOURCE_WEB = "web";

  /** 佐证来源标记：PDF 首页。 */
  public static final String EXTRACT_SOURCE_PDF = "pdf";

  /** 佐证来源标记：视频解析保留抽帧。 */
  public static final String EXTRACT_SOURCE_VIDEO_FRAMES = "video_frames";

  /**
   * 组装佐证占位 token。
   *
   * @param fileId 存储文件 id
   * @return 占位字符串
   */
  public static String evidenceToken(String fileId) {
    return TOKEN_PREFIX + fileId + TOKEN_SUFFIX;
  }

  /**
   * 组装正文可用的文件图 Markdown。
   *
   * @param fileId  存储文件 id
   * @param caption 可选图注；可空
   * @return Markdown 片段
   */
  public static String evidenceImageMarkdown(String fileId, String caption) {
    String alt = StrUtil.blankToDefault(StrUtil.trim(caption), "来源截图");
    return "![" + alt + "](" + FILES_API_PREFIX + fileId + ")";
  }
}
