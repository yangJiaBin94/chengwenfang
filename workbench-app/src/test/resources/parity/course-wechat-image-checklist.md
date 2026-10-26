# 课程公众号 / 配图逐步对照清单

对照原版：`工作台python版本/backend/app/workflows/course_content_generation.py`、
`content_generation.py`。

Java 落点：`workbench-app/.../application/course/`。

状态：`齐` = 行为对齐；`偏` = 结构不同但效果等价；`缺` = 需补齐。

| 步骤 ID | Python | Java | 状态 | 备注 |
|---------|--------|------|------|------|
| W1 | `_wechat_source_with_image_tokens` | `CourseMarkdownUtil.prepareWechatSource` | 齐 | 封面提示删除；内文 → `[[COURSE_IMAGE_N]]` |
| W2 | `_generate_wechat_chunks` | `WechatChunkConverter.convert` | 齐 | 切块、reuse、≥75%、token 保留、确定性 fallback |
| W3a | `_normalize_wechat_terminal_sections` | `CourseMarkdownUtil.normalizeTerminalSections` | 齐 | 小结/预告去重 |
| W3b | `_normalize_standalone_code_lines` | `CourseMarkdownUtil.normalizeStandaloneCodeLines` | 齐 | |
| W3c | `_build_wechat_header` | `WechatChunkConverter.buildWechatHeader` | 齐 | 注入页眉 + `[[COURSE_COVER]]` |
| W4a | `check_knowledge_rules` | `CourseGenerationValidators.rejectKnowledgeGaps` | 齐 | |
| W4b | `_validate_manifest` | `CourseGenerationValidators.assertContentManifest` | 齐 | 图片引用数 + 公式子串 |
| I1 | `_remove_screenshot_prompts` | `CourseImagePipeline.requiresScreenshot` → placeholder | 偏 | 原版从文中删截图提示；Java 占位图计入张数（等价） |
| I2a | `_build_image_plan` | `CourseImagePipeline.buildPlan` | 齐 | |
| I2b | `_apply_image_result` | `CourseImagePipeline.applyImageResult` | 齐 | |
| I3 | apply 后 `### [[COURSE_IMAGE_N]]` + 残留 token 扫尾 | `CourseImagePipeline.replaceLeftoverImageTokens` | 齐 | compose 前调用 |
| I4a | `_compose_wechat_images` | `CourseImagePipeline.composeWechatImages` | 齐 | 与 I4b 合并实现 |
| I4b | `_normalize_wechat_images`（`### 插图`+引用块、重排） | `composeWechatImages` + `stripImageProductionResidue` | 齐 | 结构合并；含 `### 插图 N` 清理 |
| F1a | `_validate_final` 提示词/token 残留 | `validateFinal` | 齐 | 含 `### 插图` 提示标题拒绝 |
| F1b | 插图张数/间距/封面 | `validateFinal` / `validateCover` | 齐 | |
| F2 | `WECHAT_HEADER_LAYOUT_INVALID` | `validateFinal` 页眉布局正则 | 齐 | 系列行 / 空引用 / 覆盖 / 空引用 / 摘要 |

## 刻意不做

- 不把 compose/normalize 拆回两个 Java 方法（I4 标齐/结构合并）。
- 不恢复 `ensureWechatImageTokens` 插回（缺 token 抛错）。
- 截图路径保持占位图（I1 偏）。

## 代码互证

- `composeWechatImages` / `stripImageProductionResidue` → checklist **I4**
- `replaceLeftoverImageTokens` → checklist **I3**
- `validateFinal` → checklist **F1** / **F2**
