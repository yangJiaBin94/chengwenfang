import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** Resolve the source knowledge base and the public mirror destinations. */
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const sourceRoot = path.resolve(rootDir, "..", "knowledgeBase");
const downloadsDir = path.join(process.env.USERPROFILE ?? "C:\\Users\\嘉彬", "Downloads");
const sourceTopicDir = path.join(sourceRoot, "原始资料", "自创文章", "AI自学");
const topicDir = path.join(rootDir, "content", "learning-content", "topics", "ai-basics");
const publicTopicDir = path.join(rootDir, "public", "content", "learning-content", "topics", "ai-basics");

/** Minimal lesson-one import used by the focused content migration command. */
const files = [
  {
    source: path.join(sourceTopicDir, "认知启蒙", "AI基础知识-认知启蒙-第1课-AI的定义与边界.md"),
    target: path.join(topicDir, "drafts", "认知启蒙", "AI基础知识-认知启蒙-第1课-AI的定义与边界.md")
  },
  {
    source: path.join(sourceTopicDir, "公众号文章", "第1课-AI的定义与边界，别再拿科幻片当教材了.md"),
    target: path.join(topicDir, "wechat", "第1课-AI的定义与边界，别再拿科幻片当教材了.md")
  }
];

/** Create a directory and all missing parents. */
async function ensureDir(target) {
  await fs.mkdir(target, { recursive: true });
}

/** Return whether a source file or directory exists. */
async function exists(target) {
  try {
    await fs.access(target);
    return true;
  } catch {
    return false;
  }
}

/** Normalize asset names for stable public paths. */
function safeAssetName(fileName) {
  return fileName
    .replace(/[^\p{L}\p{N}._-]+/gu, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/** Collect lesson-one image assets from the configured source locations. */
async function collectAssets() {
  const assets = new Map();
  for (const dir of [sourceRoot, downloadsDir]) {
    if (!(await exists(dir))) continue;
    const entries = await fs.readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isFile() && /\.(png|jpe?g|webp|gif)$/i.test(entry.name)) {
        assets.set(entry.name, path.join(dir, entry.name));
      }
    }
  }
  return assets;
}

/** Write a browser-readable text copy with normalized asset references. */
async function copyPublicTextCopy(targetFile, assets) {
  let content = await fs.readFile(targetFile, "utf8");
  const matches = [...content.matchAll(/!\[\[([^\]|]+)(?:\|[^\]]+)?\]\]/g)];
  for (const match of matches) {
    const fileName = match[1].trim();
    const sourceAsset = assets.get(fileName);
    if (!sourceAsset) continue;
    const safeName = safeAssetName(fileName);
    const publicAsset = path.join(publicTopicDir, "assets", safeName);
    await ensureDir(path.dirname(publicAsset));
    await fs.copyFile(sourceAsset, publicAsset);
    content = content.replaceAll(
      match[0],
      `![${fileName}](/content/learning-content/topics/ai-basics/assets/${safeName})`
    );
  }

  const relative = path.relative(topicDir, targetFile);
  const publicText = path.join(publicTopicDir, `${relative}.txt`);
  await ensureDir(path.dirname(publicText));
  await fs.writeFile(publicText, content, "utf8");
}

/** Import lesson one content and its assets into the workbench copy. */
async function main() {
  const assets = await collectAssets();
  let copied = 0;

  for (const file of files) {
    if (!(await exists(file.source))) {
      throw new Error(`Source file not found: ${file.source}`);
    }
    await ensureDir(path.dirname(file.target));
    await fs.copyFile(file.source, file.target);
    await copyPublicTextCopy(file.target, assets);
    copied += 1;
  }

  console.log(`Reimported lesson 1: ${copied} markdown files.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
