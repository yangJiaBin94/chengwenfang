import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  History,
  RefreshCcw,
} from "lucide-react";
import type {
  ArticleVersionData,
  ContentThemeData,
} from "../../course/api";
import type {
  JobData,
  JobEventData,
  WorkflowNodeData,
} from "../../../shared/api/jobs";
import { formatExecutionTime } from "../../../shared/format/time";
import {
  buildExecutionAttemptBatches as buildSharedExecutionAttemptBatches,
  type ExecutionAttemptBatch,
} from "../../../shared/execution/attemptBatches";

export type { ExecutionAttemptBatch };

export type Course = {
  id: string;
  series: string;
  lesson: string;
  title: string;
};

/** 生成流水线步骤：[key, 标题, 说明]；含新旧标签以兼容缓存事件。 */
export const generationPipeline = [
  [
    "context",
    "读取项目与课程上下文",
    "读取项目规则、学习路线、相邻课程和现有内容",
  ],
  ["skills", "加载生成 Skill", "加载系统级 Skill 和生成课程内容场景 Skill"],
  [
    "plan_course",
    "设计课程定位与边界",
    "明确本课目标、必须覆盖内容和禁止越界内容",
  ],
  [
    "review_course_plan",
    "审校课程计划",
    "检查课程计划、前后课程衔接和生成范围",
  ],
  ["draft", "生成课程原稿", "生成结构、案例、公式、插图提示词和课程预告"],
  ["draft_review", "审校课程原稿", "检查知识点、公式、边界、术语和写作规范"],
  [
    "manifest",
    "提取内容保留清单",
    "记录公众号必须保留的知识点、公式、案例和图片",
  ],
  ["wechat", "生成公众号文章", "以课程原稿为唯一事实来源进行等量转换"],
  [
    "wechat_review",
    "审校公众号文章",
    "逐项核对知识点、公式、案例、边界和图片引用",
  ],
  ["preview", "同步上一课预告", "更新上一课原稿和公众号版本的下节课预告"],
  ["image_plan", "规划课程配图", "明确封面、正文图片、用途、结论和目标尺寸"],
  ["images", "生成并检查图片", "按图片 AI 配置生成并检查所有封面和正文图片"],
  ["apply", "写入图片与文章版本", "课程原稿保留提示词，公众号替换为图片引用"],
  ["final", "最终完整性检查", "检查文章结构、文件、图片引用、失败项和版本关联"],
  ["context", "读取课程上下文", "读取完整路线、课程位置、前后课程和现有内容"],
  ["draft", "策划并生成课程原稿", "确定教学边界、结构、案例、公式和插图位置"],
  [
    "draft_review",
    "审校课程原稿",
    "检查知识准确性、零基础可读性、自然表达和格式",
  ],
  ["wechat", "生成公众号文章", "按照移动端结构进行知识等量转换"],
  ["wechat_review", "对照公众号内容", "核对知识点、公式、例题、边界和发布格式"],
  ["preview", "同步上一课预告", "更新上一课课程版和公众号版的下节课预告"],
  ["image_plan", "汇总配图需求", "合并课程版与公众号版提示词并按语义去重"],
  ["images", "生成并检查全部图片", "按图片 AI 配置生成封面和全部正文插图"],
  ["apply", "写入图片与文章版本", "课程版保留提示词，公众号版替换为图片"],
  ["final", "最终完稿检查", "检查文章结构、图片引用、提示词残留和失败项"],
] as const;

/** 按 key 去重后的展示用流水线。 */
export const uniqueGenerationPipeline = generationPipeline.filter(
  ([key], index, items) =>
    items.findIndex(([candidate]) => candidate === key) === index,
);

export function isExecutionFailureEvent(eventType: string | undefined) {
  return eventType === "error" || eventType === "failed" || eventType === "cancelled";
}

export function generationStepIndex(step: string | null | undefined, message = "") {
  const safeStep = step ?? "";
  const safeMessage = message ?? "";
  if (
    safeStep === "queued" ||
    safeStep === "retry_queued" ||
    safeStep === "started" ||
    safeStep === "recovered" ||
    safeStep === "cancelled"
  ) {
    return -1;
  }
  if (
    safeStep === "snapshot_context" ||
    safeStep === "load_course_context" ||
    safeStep === "graph_execute"
  )
    return 0;
  if (safeStep === "load_skills" || safeStep === "load_skill_context") return 1;
  if (safeStep === "plan_course") return 2;
  if (safeStep === "review_course_plan") return 3;
  if (
    safeStep === "generate_draft" ||
    safeStep === "compress_draft" ||
    safeStep.startsWith("draft_")
  )
    return 4;
  if (safeStep === "review_draft" || safeStep.includes("course_draft_auto_review"))
    return 5;
  if (safeStep === "extract_content_manifest") return 6;
  if (
    safeStep === "review_wechat" ||
    safeStep === "wechat_review" ||
    safeStep.includes("wechat_article_auto_review")
  )
    return 8;
  if (
    safeStep === "generate_wechat" ||
    safeStep === "wechat_conversion" ||
    safeStep.startsWith("wechat_")
  )
    return 7;
  if (safeStep === "sync_previous_preview") return 9;
  if (
    safeStep === "collect_image_prompts" ||
    safeStep === "plan_images" ||
    safeStep === "resume_images"
  )
    return 10;
  if (safeStep === "generate_images" || safeStep === "image_cover" || safeStep === "image_inline")
    return 11;
  if (safeStep === "apply_images") return 12;
  // graph_finalize 只是管道收尾，不应映射为「最终完整性检查」，否则图中段失败会被标到第 14 步
  if (safeStep === "graph_finalize") return -1;
  if (
    safeStep === "final_validation" ||
    safeStep === "formula_style_validation" ||
    safeStep === "formula_expression_validation" ||
    safeStep === "finalize" ||
    safeStep === "commit_version" ||
    safeStep === "done" ||
    safeStep === "completed"
  )
    return 13;
  if (safeMessage.includes("审校") || safeMessage.includes("检查原稿")) return 2;
  if (
    safeMessage.includes("公众号") &&
    (safeMessage.includes("审校") || safeMessage.includes("检查"))
  )
    return 4;
  if (safeMessage.includes("公众号")) return 3;
  if (safeMessage.includes("原稿")) return 4;
  if (safeMessage.includes("上一课") || safeMessage.includes("预告")) return 5;
  if (safeMessage.includes("汇总") && safeMessage.includes("配图")) return 6;
  if (safeMessage.includes("生成") && safeMessage.includes("图片")) return 7;
  if (safeMessage.includes("写入") || safeMessage.includes("替换")) return 8;
  if (safeMessage.includes("最终") || safeMessage.includes("完稿")) return 9;
  return -1;
}

export function completedExecutionMessage(message: string | null | undefined) {
  const text = message ?? "";
  const replacements: Array<[string, string]> = [
    ["AI 修复任务开始执行", "AI 修复任务已启动"],
    ["正在执行最终", "已完成最终"],
    ["正在读取", "已读取"],
    ["正在加载", "已加载"],
    ["正在设计", "已设计"],
    ["正在提取", "已提取"],
    ["正在解析", "已解析"],
    ["正在定位", "已定位"],
    ["正在规划", "已规划"],
    ["正在准备", "已准备"],
    ["正在合并", "已合并"],
    ["正在校验", "已校验"],
    ["正在生成", "已生成"],
    ["正在保存", "已保存"],
    ["正在同步", "已同步"],
    ["正在汇总", "已汇总"],
    ["正在写入", "已写入"],
    ["正在替换", "已替换"],
    ["正在检查", "已检查"],
    ["正在调用", "已调用"],
    ["正在转换", "已转换"],
    ["正在执行", "已完成"],
  ];
  const replacement = replacements.find(([prefix]) =>
    text.startsWith(prefix),
  );
  return replacement
    ? text.replace(replacement[0], replacement[1])
    : text;
}

export function formatGenerationSnapshot(
  value: string | null | undefined,
  fallback: string,
) {
  const source = value?.trim() || fallback;
  const lines = source
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  const output: string[] = [];
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index].replace(/^[-*]\s*/, "");
    if (/[：:]$/.test(line)) {
      const items: string[] = [];
      while (index + 1 < lines.length && /^[-*]\s+/.test(lines[index + 1])) {
        items.push(
          lines[index + 1].replace(/^[-*]\s*/, "").replace(/[。；：]$/, ""),
        );
        index += 1;
      }
      output.push(`${line}${items.length ? items.join("、") : ""}。`);
      continue;
    }
    const normalized = line.replace(/,\s*/g, "，");
    output.push(
      /[。！？；]$/.test(normalized) ? normalized : `${normalized}。`,
    );
  }
  return output.join(" ");
}

export function workflowNodeTitle(nodeKey: string | null | undefined) {
  if (!nodeKey) return "未命名节点";
  const titles: Record<string, string> = {
    snapshot_context: "读取并快照课程上下文",
    generate_draft: "生成课程原稿",
    "draft_section:opening": "生成原稿：开场与本课目标",
    "draft_section:core": "生成原稿：核心原理",
    "draft_section:example": "生成原稿：具体例子与推导",
    "draft_section:summary": "生成原稿：本课小结",
    "draft_section:preview": "生成原稿：下节课预告",
    generate_wechat: "转换公众号文章",
    "wechat_chunk:1": "转换公众号文章：第 1 段",
    "wechat_chunk:2": "转换公众号文章：第 2 段",
    "wechat_chunk:3": "转换公众号文章：第 3 段",
    "wechat_chunk:4": "转换公众号文章：第 4 段",
    "wechat_chunk:5": "转换公众号文章：第 5 段",
    generate_images: "逐张生成与检查配图",
    final_validation: "最终完整性检查",
  };
  return titles[nodeKey] || nodeKey.replaceAll("_", " ");
}

/** Converts backend event keys into user-facing Chinese execution-node names. */

export function eventStepTitle(step: string | null | undefined) {
  if (!step) return "内部处理步骤";
  const titles: Record<string, string> = {
    queued: "任务进入队列",
    starting: "启动生成任务",
    retrying: "重新执行失败步骤",
    graph_execute: "执行课程成文工作流",
    graph_finalize: "汇总工作流结果",
    snapshot_context: "读取并快照课程上下文",
    load_course_context: "读取课程上下文",
    load_skills: "加载生成 Skill",
    load_skill_context: "加载节点 Skill",
    plan_course: "设计课程计划",
    review_course_plan: "审校课程计划",
    draft_opening: "生成原稿：开场与目标",
    draft_core: "生成原稿：核心原理",
    draft_example: "生成原稿：案例与推导",
    draft_summary: "生成原稿：本课小结",
    draft_preview: "生成原稿：下节课预告",
    compress_draft: "压缩课程原稿",
    generate_draft: "生成课程原稿",
    review_draft: "校验课程原稿",
    extract_content_manifest: "提取内容保留清单",
    wechat_conversion: "生成公众号文章",
    generate_wechat: "生成公众号文章",
    review_wechat: "审校公众号文章",
    sync_previous_preview: "同步上一课预告",
    collect_image_prompts: "收集插图提示词",
    plan_images: "规划课程配图",
    resume_images: "继续生成配图",
    image_cover: "生成课程封面",
    image_inline: "生成正文插图",
    generate_images: "生成并检查配图",
    apply_images: "写入图片与文章",
    formula_style_validation: "校验公式块样式",
    formula_expression_validation: "校验公式表达式",
    final_validation: "执行最终完整性检查",
    finalize: "保存课程版本",
    commit_version: "保存课程版本",
    done: "完成生成任务",
    completed: "完成生成任务",
  };
  if (titles[step]) return titles[step];

  const sectionTitles: Record<string, string> = {
    opening: "开场与本课目标",
    core: "核心原理",
    example: "具体例子与推导",
    summary: "本课小结",
    preview: "下节课预告",
  };
  if (step === "draft_load_context") return "读取原稿生成上下文";
  if (step === "draft_call_text_model") return "调用文本 AI 生成原稿";
  if (step === "draft_validate_content") return "检查原稿内容";
  if (step === "draft_save_artifact") return "保存原稿中间产物";
  if (step.startsWith("draft_generate_section_")) {
    const section = step.replace("draft_generate_section_", "");
    return `生成原稿：${sectionTitles[section] || "课程章节"}`;
  }
  if (step.startsWith("draft_reuse_section_")) {
    const section = step.replace("draft_reuse_section_", "");
    return `复用原稿：${sectionTitles[section] || "课程章节"}`;
  }
  if (step === "wechat_load_context") return "读取公众号转换上下文";
  if (step === "wechat_call_text_model") return "调用文本 AI 转换公众号文章";
  if (step === "wechat_validate_content") return "检查公众号文章";
  if (step === "wechat_save_artifact") return "保存公众号中间产物";
  if (
    step.startsWith("wechat_generate_chunk_") ||
    step.startsWith("wechat_chunk_")
  ) {
    const chunk = step.match(/(\d+)$/)?.[1];
    return `转换公众号文章${chunk ? `：第 ${chunk} 段` : ""}`;
  }
  if (step === "wechat_reuse_wechat_chunk") return "复用公众号分段";
  if (step === "reject_reused_wechat_chunk") return "历史分段不再复用";
  if (step === "fallback_wechat_chunk") return "切换完整保留模式";
  if (step.includes("review") || step.includes("validate"))
    return "执行内容检查";
  if (step.includes("image")) return "处理课程配图";
  if (step.includes("save") || step.includes("persist")) return "保存生成结果";
  return "内部处理步骤";
}

/** Describe replayed retry events without implying that reused content was regenerated. */

export function technicalEventMessage(
  event: JobEventData,
  batchEvents: JobEventData[],
) {
  const reusedDraft = batchEvents.some((item) =>
    (item.step ?? "").startsWith("draft_reuse_section_"),
  );
  const reusedWechat = batchEvents.some(
    (item) => item.step === "wechat_reuse_wechat_chunk",
  );
  if (reusedDraft && event.step === "generate_draft")
    return "开始检查课程原稿历史产物";
  if (reusedDraft && event.step === "draft_call_text_model")
    return "检查原稿分段是否可以复用";
  if (reusedDraft && (event.step ?? "").startsWith("draft_generate_section_"))
    return "检查该原稿分段的历史产物";
  if (reusedWechat && event.step === "generate_wechat")
    return "开始检查公众号文章历史产物";
  if (reusedWechat && event.step === "wechat_call_text_model")
    return "检查公众号分段是否可以复用";
  return completedExecutionMessage(event.message);
}

/** Return the durable parent node for a section or chunk execution node. */

export function workflowNodeParentKey(nodeKey: string | null | undefined) {
  if (!nodeKey) return null;
  if (nodeKey.startsWith("draft_section:")) return "generate_draft";
  if (nodeKey.startsWith("wechat_chunk:")) return "generate_wechat";
  return null;
}

/** Split one durable job history into user-triggered execution attempts.
 *
 * `retry_queued` is the boundary for an explicit failed-step retry. Automatic
 * model corrections stay inside the same attempt so their diagnostic nodes
 * remain next to the execution that produced them.
 */
export function buildExecutionAttemptBatches(
  events: JobEventData[],
  nodes: WorkflowNodeData[],
): ExecutionAttemptBatch[] {
  return buildSharedExecutionAttemptBatches(events, nodes);
}

/** Render one technical workflow node with an optional nested-child treatment. */

export function WorkflowNodeRow({
  node,
  child = false,
}: {
  node: WorkflowNodeData;
  child?: boolean;
}) {
  const statusText =
    node.status === "succeeded"
      ? "已完成"
      : node.status === "failed"
        ? "执行失败"
        : node.status === "running"
          ? "执行中"
          : "等待执行";
  return (
    <article
      className={`workflow-node-row node-${node.status} ${child ? "workflow-node-child" : ""}`}
    >
      <span className="workflow-node-indicator">
        {node.status === "succeeded" ? (
          <CheckCircle2 size={16} />
        ) : node.status === "failed" ? (
          <AlertTriangle size={16} />
        ) : (
          <RefreshCcw size={16} />
        )}
      </span>
      <div>
        <div className="workflow-node-heading">
          <strong>{workflowNodeTitle(node.nodeKey)}</strong>
          <span>{statusText}</span>
          {node.attemptNo > 1 && <em>第 {node.attemptNo} 次尝试</em>}
        </div>
        <small>
          {formatExecutionTime(node.startedAt)} 至{" "}
          {formatExecutionTime(node.finishedAt)}
        </small>
        {node.outputSummary && (
          <p>结果：{JSON.stringify(node.outputSummary)}</p>
        )}
        {node.errorMessage && (
          <p className="workflow-node-error">{node.errorMessage}</p>
        )}
      </div>
    </article>
  );
}

export function displayJobStatus(job: JobData, events: JobEventData[]): JobData["status"] {
  const last = [...events].sort((left, right) => left.id - right.id).at(-1);
  if (!last) return job.status;
  if (job.status === "running" || job.status === "pending") {
    if (last.eventType === "failed" || last.eventType === "error") return "failed";
    if (last.eventType === "cancelled") return "cancelled";
    if (last.eventType === "succeeded" || last.eventType === "completed") {
      return "succeeded";
    }
  }
  return job.status;
}

export function jobStatusLabel(job: JobData) {
  if (job.status === "succeeded") return "已完成";
  if (job.status === "failed") return "失败";
  if (job.status === "cancelled") return "已取消";
  if (job.status === "pending") {
    return job.currentStep === "recovered" ? "已恢复排队" : "等待执行";
  }
  return "执行中";
}
