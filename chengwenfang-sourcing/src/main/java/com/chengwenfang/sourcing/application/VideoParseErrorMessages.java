package com.chengwenfang.sourcing.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.sourcing.facade.enums.VideoParseStep;
import java.util.Locale;

/**
 * 将视频解析技术错误转成用户可读说明。
 */
public final class VideoParseErrorMessages {
  private VideoParseErrorMessages() {}

  /**
   * 生成用户可读的失败原因。
   *
   * @param rawError 原始错误（CLI / 模型 / 异常信息）
   * @param step     失败步骤；可为 null
   * @return 中文说明，不含堆栈与编解码细节
   */
  public static String humanize(String rawError, VideoParseStep step) {
    String raw = StrUtil.nullToEmpty(rawError).trim();
    String lower = raw.toLowerCase(Locale.ROOT);
    String stepHint = stepHint(step);

    if (StrUtil.isBlank(raw)) {
      return "解析失败" + stepHint + "，请稍后重试。";
    }
    if (containsAny(lower, "gbk", "codec can't encode", "codec can't decode", "unicodeencode",
        "unicodedecode", "illegal multibyte")) {
      return "语音识别结果含特殊字符，写出时编码失败" + stepHint
          + "。请再点一次「解析」重试（环境已改为 UTF-8）。";
    }
    if (containsAny(lower, "openai-whisper", "no module named 'whisper'", "no module named whisper")) {
      return "本机未安装语音识别组件（Whisper）" + stepHint + "。请按文档安装 scripts/video-transcript 依赖。";
    }
    if (containsAny(lower, "createprocess error=2", "系统找不到指定的文件", "cannot run program")) {
      return "找不到视频解析 Python 环境" + stepHint
          + "。请按 scripts/video-transcript/README 创建 .venv 后重试。";
    }
    if (containsAny(lower, "modulenotfound", "no module named")) {
      return "Python 环境缺少依赖" + stepHint + "。请确认已创建 venv 并安装 requirements.txt。";
    }
    if (containsAny(lower, "超时", "timeout", "timed out")) {
      return "解析超时" + stepHint + "。网络较慢或视频较长时请稍后重试。";
    }
    if (containsAny(lower, "未找到字幕", "字幕文本过短", "no subtitles")) {
      return "该视频没有可用字幕" + stepHint + "，将尝试下载音频做语音识别。";
    }
    if (containsAny(lower, "音频下载失败", "unable to download", "http error 4", "http error 5",
        "unsupported url", "video unavailable")) {
      return "视频音频下载失败" + stepHint + "。可能受平台限制或链接失效，可打开原文确认后重试。";
    }
    if (containsAny(lower, "ffmpeg", "找不到可转换", "conversion failed")) {
      return "音频格式转换失败" + stepHint + "。请确认本机已安装 ffmpeg 并加入 PATH。";
    }
    if (containsAny(lower, "缺少 audio.mp3", "缺少音频")) {
      return "缺少音频文件" + stepHint + "。请点「重新解析」从头再试。";
    }
    if (containsAny(lower, "语音识别结果过短", "识别结果过短")) {
      return "语音识别没有得到有效内容" + stepHint + "。将尝试画面解析。";
    }
    if (containsAny(lower, "视频下载失败", "download_video", "无法下载视频")) {
      return "视频下载失败" + stepHint + "。可能受平台限制或链接失效，可打开原文确认后重试。";
    }
    if (containsAny(lower, "未找到关键帧", "抽帧", "extract_frames", "无有效帧")) {
      return "关键帧提取失败" + stepHint + "。请确认本机已安装 ffmpeg 后重试。";
    }
    if (containsAny(lower, "画面理解", "多模态", "vision", "未配置多模态")) {
      return "画面理解失败" + stepHint + "。请检查设置中的「多模态 AI」配置后重试。";
    }
    if (containsAny(lower, "ai 总结", "模型返回空", "总结失败")) {
      return "要点总结失败" + stepHint + "。可从「总结内容」步骤重试，或检查 AI 配置。";
    }
    if (containsAny(lower, "cli 无 json", "json 解析失败", "cli json")) {
      return "解析程序未正常返回结果" + stepHint + "。请重试；若反复失败请查看本机 Python/依赖是否正常。";
    }
    if (containsAny(lower, "时长超过上限", "超过上限")) {
      return raw.endsWith("。") ? raw : raw + "。";
    }
    if (containsAny(lower, "视频解析未启用", "enabled=false")) {
      return "视频解析未启用。请在配置中打开 chengwenfang.video-transcript.enabled。";
    }
    if (containsAny(lower, "无法创建工作目录")) {
      return "无法创建解析临时目录，请检查磁盘权限后重试。";
    }
    if (looksTechnical(raw)) {
      return "解析失败" + stepHint + "。请重试；若仍失败可换一条资料或检查本机依赖。";
    }
    // 已是短中文说明则保留，并补步骤
    if (raw.length() <= 80 && !looksTechnical(raw)) {
      return raw.endsWith("。") ? raw : raw + "。";
    }
    return "解析失败" + stepHint + "。请重试。";
  }

  private static String stepHint(VideoParseStep step) {
    if (step == null) {
      return "";
    }
    return "（步骤：" + step.getLabel() + "）";
  }

  private static boolean containsAny(String haystack, String... needles) {
    for (String needle : needles) {
      if (haystack.contains(needle.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static boolean looksTechnical(String raw) {
    String lower = raw.toLowerCase(Locale.ROOT);
    return lower.contains("traceback")
        || lower.contains("exception")
        || lower.contains("codec")
        || lower.contains("errno")
        || lower.contains("file \"")
        || lower.contains(" at 0x")
        || raw.contains("\\u")
        || raw.contains("Error:")
        || raw.contains("error:");
  }
}
