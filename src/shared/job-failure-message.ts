/**
 * 将任务失败的技术错误转成用户可读中文（通知列表/执行页）。
 * 与后端 JobFailureMessages 规则对齐，覆盖历史通知里已落库的原文。
 */
export function toUserFailureMessage(raw: string | null | undefined): string {
  const text = (raw ?? "").trim();
  if (!text) return "任务没有完成，请稍后重新执行。";

  const bodyTooLong = text.match(
    /COURSE_BODY_TOO_LONG:\s*(\S+)\s*正文\s*(\d+)\s*超过\s*(\d+)/i,
  );
  if (bodyTooLong) {
    const label = bodyTooLong[1] ?? "";
    const current = bodyTooLong[2];
    const limit = bodyTooLong[3];
    const kind = label.includes("公众号")
      ? "公众号文章"
      : label.includes("原稿")
        ? "课程原稿"
        : "正文";
    return `${kind}写得有点长了（约 ${current} 字，上限 ${limit} 字），请缩短后再生成。`;
  }

  const slotMatch = text.match(/第\s*(\d+)\s*\/\s*(\d+)\s*张/);
  const slotLabel = slotMatch
    ? `第 ${slotMatch[1]}/${slotMatch[2]} 张`
    : null;
  const lower = text.toLowerCase();
  const isTimeout =
    lower.includes("read timed out") ||
    lower.includes("timed out") ||
    lower.includes("timeout") ||
    text.includes("请求超时") ||
    text.includes("配图服务请求超时");
  if (isTimeout) {
    if (slotLabel || text.includes("生成配套图片") || text.includes("配图")) {
      return slotLabel
        ? `${slotLabel}未生成成功。原因：配图服务请求超时，请稍后重新执行失败步骤。`
        : "配图服务请求超时，请稍后重新执行失败步骤。";
    }
    return "请求超时了（常见于配图或模型调用），请稍后重新执行。";
  }
  if (
    lower.includes("connection refused") ||
    lower.includes("connect timed out") ||
    lower.includes("unknownhost")
  ) {
    if (slotLabel) {
      return `${slotLabel}未生成成功。原因：连不上配图服务，请检查网络后重新执行。`;
    }
    return "网络连不上服务，请检查网络后重新执行。";
  }
  if (text.includes("生成配套图片") && /[\u4e00-\u9fff]/.test(text)) {
    const trimmed = text
      .replace(/^第\s*\d+\s*步「生成配套图片」失败：\s*/, "")
      .trim();
    const body = trimmed || text;
    return body.length > 220 ? `${body.slice(0, 220).trim()}…` : body;
  }
  if (
    slotLabel &&
    (text.includes("配图") || text.includes("生成配套图片") || text.includes("插图"))
  ) {
    return `${slotLabel}未生成成功，请重新执行失败步骤。`;
  }
  if (
    (text.includes("COURSE_IMAGE") ||
      text.includes("IMAGE_GENERATION") ||
      text.includes("配图")) &&
    (lower.includes("fail") ||
      lower.includes("invalid") ||
      lower.includes("error") ||
      text.includes("失败"))
  ) {
    return "配图没有生成成功，请重新执行配图步骤。";
  }
  if (text.includes("WORD_COUNT") || text.includes("字数")) {
    return "字数不符合当前设置的范围，请调整字数档或缩短正文后重试。";
  }
  if (
    text.includes("WECHAT_CONTENT") ||
    text.includes("WECHAT_REVIEW") ||
    text.includes("公众号审校")
  ) {
    return "公众号文章检查没通过，请重新生成或审校后再试。";
  }
  if (
    text.includes("GENERATED_DRAFT") ||
    text.includes("COURSE_CONTENT_STRUCTURE") ||
    text.includes("原稿不完整")
  ) {
    return "课程原稿还不完整，请重新生成。";
  }
  if (text.includes("FINAL_VALIDATION") || text.includes("完整性")) {
    return "最终检查没通过，请查看执行轨迹里的失败步骤后重试。";
  }

  const withoutCode = text.replace(/^[A-Z][A-Z0-9_]+:\s*/, "").trim();
  if (withoutCode && /[\u4e00-\u9fff]/.test(withoutCode)) {
    return withoutCode;
  }
  return "任务没有完成，请打开执行轨迹查看失败步骤后重新执行。";
}

/**
 * 配图步骤失败时，若原文缺少「第几张」标识，用最近进度文案补全槽位。
 *
 * @param raw 原始/任务失败信息
 * @param progressHint 如「正在生成第 3/4 张」
 */
export function toImageStepFailureMessage(
  raw: string | null | undefined,
  progressHint?: string | null,
): string {
  const base = toUserFailureMessage(raw);
  if (/第\s*\d+\s*\/\s*\d+\s*张/.test(base)) {
    return base;
  }
  const slot = (progressHint ?? "").match(/第\s*(\d+)\s*\/\s*(\d+)\s*张/);
  const isTimeout =
    /超时/.test(base) ||
    /timed?\s*out|timeout/i.test(raw ?? "");
  if (slot && isTimeout) {
    return `第 ${slot[1]}/${slot[2]} 张未生成成功。原因：配图服务请求超时，请稍后重新执行失败步骤。`;
  }
  return base.replace(/^第\s*\d+\s*步「生成配套图片」失败：\s*/, "").trim() || base;
}
