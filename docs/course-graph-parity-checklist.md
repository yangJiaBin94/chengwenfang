# 课程图切段零回退对照清单

正式路径已切到 14 节点规范图；业务逻辑在 `CourseArticleGraphSupport`。

## 节点与进度

| typeKey | 进度步名 | 对照点 |
|---------|----------|--------|
| snapshot_context | snapshot_context | 快照 + Context + 计划边界 |
| draft_opening…preview | 同 typeKey | 五段预算 / Skill / 复用 |
| review_draft | review_draft | compress_draft 子进度、插图守卫、最多 3 轮 repair |
| wechat_conversion | generate_wechat | wechat_chunk:*、等价比、知识缺口、最多 3 轮 repair |
| image_cover / image_inline | image_cover / image_inline | Skill 分节点；封面节点可一次跑完全量配图 |
| formula_* / final_validation | 同 typeKey | 真校验，非 LaTeX stub |
| commit_version | finalize | commit + 同步上一课预告 |

## Job 输出

- `draft` / `wechat` / `images` / `shell`（courseId、title、draftArticleId、wechatArticleId）
- `partial=false`

## 手工对照

1. 重启后端，确认课程工作流发布为 14 节点规范图。
2. 同课同档跑一次正式生成，核对进度条步名与终稿结构。
3. 抽查公众号等价比与配图占位替换。
