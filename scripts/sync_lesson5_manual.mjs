import fs from "node:fs/promises";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const draftDir = path.join(
  root,
  "content",
  "learning-content",
  "topics",
  "ai-basics",
  "drafts",
  "\u6a21\u578b\u539f\u7406",
  "\u8bfe\u7a0b\u539f\u7a3f",
);
const draftNames = await fs.readdir(draftDir);
const draftName = draftNames.find((name) => name.includes("\u7b2c5\u8bfe"));
if (!draftName) throw new Error("Lesson 5 draft not found");

const content = await fs.readFile(path.join(draftDir, draftName), "utf8");
const courseId = "course-\u6a21\u578b\u539f\u7406-5";

for (const kind of ["course_draft", "wechat_article"]) {
  const articleId = `${courseId}:${kind}`;
  const response = await fetch(
    `http://127.0.0.1:8001/api/articles/${encodeURIComponent(articleId)}`,
    {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        content,
        source: "manual_edit",
        reason: "\u7b2c5\u8bfe\u6539\u4e3a Windows/macOS \u672c\u5730 Python \u547d\u4ee4\u884c\u4e0e VS Code \u5165\u95e8\u6559\u7a0b",
      }),
    },
  );
  if (!response.ok) throw new Error(`${articleId}: ${response.status} ${await response.text()}`);
  const result = await response.json();
  console.log(`${kind}: v${result.currentVersionNo}, ${result.wordCount} chars`);
}
