import fs from "node:fs/promises";
import crypto from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";

/** Resolve all import inputs and generated outputs relative to the repository. */
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const sourceRoot = path.resolve(rootDir, "..", "knowledgeBase");
const sourceTopicDir = path.join(sourceRoot, "原始资料", "自创文章", "AI自学");
const downloadsDir = path.join(process.env.USERPROFILE ?? "C:\\Users\\嘉彬", "Downloads");
const topicDir = path.join(rootDir, "content", "learning-content", "topics", "ai-basics");
const publicDataDir = path.join(rootDir, "public", "data");
const publicContentDir = path.join(rootDir, "public", "content", "learning-content", "topics", "ai-basics");
let sourceAssetByName = new Map();

/** Content sources copied during the repeatable initial import. */
const copyPlan = [
  ["AI基础知识-学习路线.md", "route/AI基础知识-学习路线.md"],
  ["AI基础知识-学习进度.md", "progress/AI基础知识-学习进度.md"],
  ["公众号排版规范.md", "rules/公众号排版规范.md"],
  ["插图风格指南.md", "rules/插图风格指南.md"],
  ["认知启蒙", "drafts/认知启蒙"],
  ["数学基础", "drafts/数学基础"],
  ["系列3-AI模型原理-从神经元到训练全流程", "drafts/模型原理"],
  ["公众号文章", "wechat"],
  ["images", "images"]
];

/** Return whether a source file or directory exists. */
async function exists(target) {
  try {
    await fs.access(target);
    return true;
  } catch {
    return false;
  }
}

/** Create a directory and all missing parents. */
async function ensureDir(target) {
  await fs.mkdir(target, { recursive: true });
}

/** Copy a file after ensuring its destination directory exists. */
async function copyFileSafe(from, to) {
  if (!(await exists(from))) return false;
  await ensureDir(path.dirname(to));
  await fs.copyFile(from, to);
  return true;
}

/** Recursively copy a source directory into the workbench content tree. */
async function copyDirectory(from, to) {
  if (!(await exists(from))) return 0;
  await ensureDir(to);
  let count = 0;
  const entries = await fs.readdir(from, { withFileTypes: true });
  for (const entry of entries) {
    const sourcePath = path.join(from, entry.name);
    const targetPath = path.join(to, entry.name);
    if (entry.isDirectory()) {
      count += await copyDirectory(sourcePath, targetPath);
    } else if (entry.isFile()) {
      await fs.copyFile(sourcePath, targetPath);
      count += 1;
    }
  }
  return count;
}

/** Read UTF-8 text or return an empty string for missing files. */
async function readText(filePath) {
  try {
    return await fs.readFile(filePath, "utf8");
  } catch {
    return "";
  }
}

/** Collect image files available from the imported source locations. */
async function collectSourceAssets() {
  sourceAssetByName = new Map();

  for (const assetDir of [sourceRoot, downloadsDir]) {
    if (!(await exists(assetDir))) continue;
    const entries = await fs.readdir(assetDir, { withFileTypes: true });
    for (const entry of entries) {
      if (!entry.isFile()) continue;
      if (!/\.(png|jpe?g|webp|gif)$/i.test(entry.name)) continue;
      sourceAssetByName.set(entry.name, path.join(assetDir, entry.name));
    }
  }
}

/** Recursively list Markdown files in stable path order. */
async function listMarkdownFiles(dir) {
  if (!(await exists(dir))) return [];
  const entries = await fs.readdir(dir, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
    .map((entry) => path.join(dir, entry.name))
    .sort((a, b) => a.localeCompare(b, "zh-CN"));
}

/** Normalize line endings and trailing whitespace in imported Markdown. */
function normalizeMarkdown(content) {
  return content.replace(/\r\n/g, "\n").trim();
}

/** Count readable characters used by generated course metrics. */
function countReadableChars(content) {
  return normalizeMarkdown(content).replace(/\s/g, "").length;
}

/** Return a stable content hash for import change detection. */
function hashContent(content) {
  return crypto.createHash("sha1").update(content).digest("hex").slice(0, 12);
}

/** Produce a compact text excerpt for the course index. */
function excerpt(content, max = 520) {
  const lines = normalizeMarkdown(content)
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"));
  return lines.join("\n").slice(0, max);
}

/** Parse course and lesson metadata from a draft file path. */
function parseDraftFile(filePath) {
  const fileName = path.basename(filePath, ".md");
  const match = fileName.match(/^AI基础知识-(.+)-第(\d+)课-(.+)$/);
  if (!match) {
    return {
      series: "未分类",
      lessonNo: 0,
      title: fileName
    };
  }
  return {
    series: match[1],
    lessonNo: Number(match[2]),
    title: match[3]
  };
}

/** Parse lesson metadata from a WeChat article file path. */
function parseWechatFile(filePath) {
  const fileName = path.basename(filePath, ".md");
  const match = fileName.match(/^(?:(认知启蒙|数学基础|模型原理))?第(\d+)课-(.+)$/);
  if (!match) return { series: "", lessonNo: null, title: fileName, filePath };
  return {
    series: match[1] ?? "认知启蒙",
    lessonNo: Number(match[2]),
    title: match[3],
    filePath
  };
}

const routeSeriesNames = ["认知启蒙", "数学基础", "模型原理", "核心架构", "大语言模型", "实战工具", "前沿趋势"];

/** Parse every planned lesson from the canonical learning-route document. */
async function parseLearningRoute(filePath) {
  const content = await readText(filePath);
  const lessons = [];
  let seriesOrder = 0;
  let series = "";

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    const seriesMatch = line.match(/^##\s+系列[^：:]+[：:]\s*(.+)$/);
    if (seriesMatch) {
      seriesOrder += 1;
      series = routeSeriesNames[seriesOrder - 1] ?? `第 ${seriesOrder} 系列`;
      continue;
    }
    if (!series || !line.startsWith("- ")) continue;
    const lessonMatch = line.match(/^-\s*第\s*(\d+)\s*课[：:]\s*(.*)$/);
    if (!lessonMatch) continue;
    const lessonNo = Number(lessonMatch[1]);
    const [rawTitle = "", ...summaryParts] = lessonMatch[2].split(/\s+[—–-]\s+/);
    lessons.push({
      series,
      seriesOrder,
      lessonNo,
      title: rawTitle.trim(),
      summary: summaryParts.join(" - ").trim()
    });
  }
  return lessons;
}

/** Convert an absolute path to a repository-relative storage path. */
function relativeToRoot(filePath) {
  return path.relative(rootDir, filePath).replace(/\\/g, "/");
}

/** Return the generated public text-copy path for a content file. */
function publicTextPathFor(filePath) {
  const relative = path.relative(topicDir, filePath).replace(/\\/g, "/");
  return `/content/learning-content/topics/ai-basics/${relative}.txt`;
}

/** Return the generated public asset path for an image file. */
function publicAssetPathFor(filePath) {
  const relative = path.relative(topicDir, filePath).replace(/\\/g, "/");
  return `/content/learning-content/topics/ai-basics/${relative}`;
}

/** Extract a decoded image file name from a URL or local path. */
function imageNameFromPath(src) {
  const cleanSrc = src.split(/[?#]/)[0];
  const fileName = cleanSrc.split(/[\\/]/).filter(Boolean).pop() ?? cleanSrc;
  try {
    return decodeURIComponent(fileName);
  } catch {
    return fileName;
  }
}

/** Copy one embedded image into the public asset directory when available. */
async function copyEmbeddedAsset(fileName) {
  const sourcePath = sourceAssetByName.get(fileName);
  if (!sourcePath) return "";
  const safeName = fileName
    .replace(/[^\p{L}\p{N}._-]+/gu, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  const target = path.join(publicContentDir, "assets", safeName);
  await ensureDir(path.dirname(target));
  await fs.copyFile(sourcePath, target);
  return `/content/learning-content/topics/ai-basics/assets/${safeName}`;
}

/** Convert Obsidian embeds and local references to browser-served Markdown paths. */
async function normalizeMarkdownEmbeds(content) {
  const matches = [...content.matchAll(/!\[\[([^\]|]+)(?:\|[^\]]+)?\]\]/g)];
  let normalized = content;
  for (const match of matches) {
    const fileName = match[1].trim();
    const publicPath = await copyEmbeddedAsset(fileName);
    if (!publicPath) continue;
    normalized = normalized.replaceAll(match[0], `![${fileName}](${publicPath})`);
  }
  return normalized;
}

/** Extract image paths already referenced by normalized Markdown. */
function extractMarkdownImagePaths(content) {
  return [...content.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g)]
    .map((match) => match[1].trim())
    .filter((src) => src.startsWith("/content/learning-content/topics/ai-basics/"));
}

/** Extract image file names declared in article prompt sections. */
function extractPromptImageFileNames(content) {
  return new Set(
    [...content.matchAll(/!\[[^\]]*\]\((?:\.\.\/)?images\/([^)]+)\)/g)]
      .map((match) => imageNameFromPath(match[1].trim()))
      .filter(Boolean)
  );
}

/** Build structured image asset metadata for one course article. */
function createImageAssets(paths, course, articlePath) {
  return uniqueStrings(paths).map((imagePath) => ({
    name: imageNameFromPath(imagePath),
    path: imagePath,
    articleId: course.id,
    articleTitle: course.title,
    articlePath
  }));
}

/** Deduplicate strings while preserving first-seen order. */
function uniqueStrings(items) {
  return [...new Set(items.filter(Boolean))];
}

/** Write a UTF-8 text copy under public/ for browser loading. */
async function writePublicTextCopy(filePath) {
  const content = await readText(filePath);
  if (!content) return "";
  const normalizedContent = await normalizeMarkdownEmbeds(content);
  const relative = path.relative(topicDir, filePath);
  const target = path.join(publicContentDir, `${relative}.txt`);
  await ensureDir(path.dirname(target));
  await fs.writeFile(target, normalizedContent, "utf8");
  return publicTextPathFor(filePath);
}

/** Copy one content asset into its public mirror path. */
async function writePublicAssetCopy(filePath) {
  if (!(await exists(filePath))) return "";
  const relative = path.relative(topicDir, filePath);
  const target = path.join(publicContentDir, relative);
  await ensureDir(path.dirname(target));
  await fs.copyFile(filePath, target);
  return publicAssetPathFor(filePath);
}

/** Derive the production status from available content and images. */
function deriveStatus(hasDraft, hasWechat, imageCount) {
  if (hasWechat && imageCount >= 3) return "图片已生成";
  if (hasWechat) return "公众号稿已生成";
  if (hasDraft) return "原稿已生成";
  return "素材已准备";
}

/** Map a production status to the dashboard progress percentage. */
function deriveProgress(status) {
  const map = {
    素材已准备: 24,
    原稿已生成: 46,
    公众号稿已生成: 64,
    图片已生成: 76,
    已排版: 82,
    "AI 已检查": 90,
    人工已审核: 100
  };
  return map[status] ?? 0;
}

/** Scan imported content and generate the frontend workbench index. */
async function createIndex() {
  const draftDirs = [
    path.join(topicDir, "drafts", "认知启蒙"),
    path.join(topicDir, "drafts", "数学基础"),
    path.join(topicDir, "drafts", "模型原理")
  ];
  const draftFiles = [];
  for (const dir of draftDirs) {
    draftFiles.push(...(await listMarkdownFiles(dir)));
  }

  const draftsByCourse = new Map();
  for (const draftPath of draftFiles) {
    const parsed = parseDraftFile(draftPath);
    draftsByCourse.set(`${parsed.series}:${parsed.lessonNo}`, { ...parsed, draftPath });
  }

  const routePath = path.join(topicDir, "route", "AI基础知识-学习路线.md");
  const routeCourses = await parseLearningRoute(routePath);

  const wechatFiles = await listMarkdownFiles(path.join(topicDir, "wechat"));
  const wechatByCourse = new Map();
  for (const filePath of wechatFiles) {
    const parsed = parseWechatFile(filePath);
    if (parsed.lessonNo !== null) {
      wechatByCourse.set(`${parsed.series}:${parsed.lessonNo}`, parsed);
      continue;
    }
    const matchingCourse = routeCourses.find((course) => {
      const topic = course.title.split(/[：:]/)[0].trim();
      return topic.length >= 2 && parsed.title.includes(topic);
    });
    if (matchingCourse) {
      wechatByCourse.set(`${matchingCourse.series}:${matchingCourse.lessonNo}`, parsed);
    }
  }

  const imageDir = path.join(topicDir, "images");
  const imageFiles = (await exists(imageDir))
    ? (await fs.readdir(imageDir)).filter((name) => /\.(png|jpe?g|webp)$/i.test(name)).sort()
    : [];

  const courses = [];
  for (const routeCourse of routeCourses) {
    const draftRecord = draftsByCourse.get(`${routeCourse.series}:${routeCourse.lessonNo}`);
    const draftPath = draftRecord?.draftPath ?? "";
    const parsed = { ...routeCourse, title: routeCourse.title || draftRecord?.title || "" };
    const draftContent = await readText(draftPath);
    const wechat = wechatByCourse.get(`${parsed.series}:${parsed.lessonNo}`);
    const wechatContent = wechat ? await readText(wechat.filePath) : "";
    const normalizedDraftContent = await normalizeMarkdownEmbeds(draftContent);
    const normalizedWechatContent = await normalizeMarkdownEmbeds(wechatContent);
    const draftPublicPath = draftPath ? await writePublicTextCopy(draftPath) : "";
    const wechatPublicPath = wechat ? await writePublicTextCopy(wechat.filePath) : "";
    const courseId = `course-${parsed.series}-${parsed.lessonNo}`.replace(/\s+/g, "-");
    const articlePath = wechat ? relativeToRoot(wechat.filePath) : "";
    const promptImageNames = new Set([
      ...extractPromptImageFileNames(normalizedDraftContent),
      ...extractPromptImageFileNames(normalizedWechatContent)
    ]);
    const matchedImages = imageFiles.filter(
      (name) => promptImageNames.has(name) || name.startsWith(`${parsed.seriesOrder}.${parsed.lessonNo}-`)
    );
    const imagePaths = matchedImages.map((name) => path.join(imageDir, name));
    const copiedImagePublicPaths = [];
    for (const imagePath of imagePaths) {
      const publicPath = await writePublicAssetCopy(imagePath);
      if (publicPath) copiedImagePublicPaths.push(publicPath);
    }
    const imagePublicPaths = uniqueStrings([
      ...copiedImagePublicPaths,
      ...extractMarkdownImagePaths(normalizedDraftContent),
      ...extractMarkdownImagePaths(normalizedWechatContent)
    ]);
    const imageAssets = createImageAssets(
      imagePublicPaths,
      {
        id: courseId,
        title: wechat?.title ?? parsed.title
      },
      articlePath
    );
    const status = deriveStatus(Boolean(draftContent), Boolean(wechat), imagePublicPaths.length);

    courses.push({
      id: courseId,
      topicSlug: "ai-basics",
      series: parsed.series,
      lesson: `第 ${parsed.lessonNo} 课`,
      lessonNo: parsed.lessonNo,
      title: parsed.title,
      status,
      progress: deriveProgress(status),
      draftWords: countReadableChars(draftContent),
      articleWords: countReadableChars(wechatContent),
      images: imagePublicPaths.length,
      reviewScore: wechat ? Math.min(98, 78 + imagePublicPaths.length * 3) : 0,
      updatedAt: "初始导入",
      summary: parsed.summary || excerpt(draftContent, 120),
      draftPath: draftPath ? relativeToRoot(draftPath) : "",
      wechatPath: wechat ? relativeToRoot(wechat.filePath) : "",
      draftPublicPath,
      wechatPublicPath,
      imagePaths: imagePaths.map((imagePath) => relativeToRoot(imagePath)),
      imagePublicPaths,
      imageAssets,
      contentHash: hashContent(`${draftContent}\n---wechat---\n${wechatContent}`),
      draftExcerpt: excerpt(draftContent),
      articleExcerpt: excerpt(wechatContent)
    });
  }

  return {
    workspace: {
      name: "工作台",
      moduleName: "自学课程及自媒体内容生成",
      topicName: "0 基础 AI 自学",
      topicSlug: "ai-basics"
    },
    generatedAt: new Date().toISOString(),
    courses,
    metrics: {
      courseCount: courses.length,
      reviewedCount: courses.filter((course) => course.status === "人工已审核").length,
      pendingCount: courses.filter((course) => course.status !== "人工已审核").length,
      imageCount: imageFiles.length + sourceAssetByName.size
    },
    rules: {
      agentRulesPath: "content/learning-content/topics/ai-basics/rules/AGENTS.md",
      layoutRulesPath: "content/learning-content/topics/ai-basics/rules/公众号排版规范.md"
    }
  };
}

/** Copy source content, mirror public files, and regenerate the course index. */
async function main() {
  if (!(await exists(sourceTopicDir))) {
    throw new Error(`Source topic directory not found: ${sourceTopicDir}`);
  }

  await ensureDir(topicDir);
  await collectSourceAssets();

  let copied = 0;
  for (const [fromRelative, toRelative] of copyPlan) {
    const from = path.join(sourceTopicDir, fromRelative);
    const to = path.join(topicDir, toRelative);
    const statExists = await exists(from);
    if (!statExists) continue;
    const stat = await fs.stat(from);
    copied += stat.isDirectory() ? await copyDirectory(from, to) : (await copyFileSafe(from, to) ? 1 : 0);
  }

  await copyFileSafe(path.join(sourceRoot, "AGENTS.md"), path.join(topicDir, "rules", "AGENTS.md"));

  const data = await createIndex();
  await ensureDir(publicDataDir);
  await fs.writeFile(
    path.join(publicDataDir, "workbench-data.json"),
    `${JSON.stringify(data, null, 2)}\n`,
    "utf8"
  );

  await ensureDir(path.join(rootDir, ".workbench"));
  await fs.writeFile(
    path.join(rootDir, ".workbench", "import-log.json"),
    `${JSON.stringify(
      {
        importedAt: new Date().toISOString(),
        copiedFiles: copied,
        note: "Source paths are recorded only in this import log. Runtime data reads workbench copies."
      },
      null,
      2
    )}\n`,
    "utf8"
  );

  console.log(`Imported ${copied} files into ${topicDir}`);
  console.log(`Generated ${data.courses.length} course records.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
  // Pair drafts, articles, and image metadata by stable lesson identity.
