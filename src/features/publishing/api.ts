/** 排版发布领域接口：从课程/设置 API 再导出，便于 feature 内统一引用。 */
export {
  listLayoutThemes,
  createLayoutTheme,
  updateLayoutTheme,
  cloneLayoutTheme,
  setLayoutThemeStatus,
  deleteLayoutTheme,
  renderPublishingMarkdown,
  loadArticleLayout,
  saveArticleLayout,
  createPublishingSnapshot,
  type LayoutThemeData,
  type PublishingRenderData,
  type ArticleLayoutData,
  type PublishingSnapshotData,
} from "../course/api";

export { pushWechatMpDraft, type WechatMpDraftPushResult } from "../settings/api";
