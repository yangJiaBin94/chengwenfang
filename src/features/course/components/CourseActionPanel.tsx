// @ts-nocheck
import {
  Bot,
  MonitorCheck,
  Palette,
  Play,
  WandSparkles,
} from "lucide-react";
import { ActionButton } from "./courseEditorUi";

export type CourseActionPanelProps = {
  checkedRules: any,
  loadState: any,
  openMdniceTask: any,
  runGeneration: any,
  runImageTask: any,
  runMockTask: any,
  taskMessage: any,
  taskRunning: any,
  toggleRule: any
};

export function CourseActionPanel({
  checkedRules,
  loadState,
  openMdniceTask,
  runGeneration,
  runImageTask,
  runMockTask,
  taskMessage,
  taskRunning,
  toggleRule
}: CourseActionPanelProps) {
  return (
    <aside className="action-panel">
      <div className="panel-header compact">
        <div>
          <h2>生产动作</h2>
          <p>
            {loadState === "ready" ? "已连接内容索引" : "正在连接内容索引"}
          </p>
        </div>
      </div>

      <div className="action-stack">
        <ActionButton
          icon={MonitorCheck}
          title="mdnice"
          text="打开外部编辑器进行排版"
          onClick={openMdniceTask}
        />
        <ActionButton
          icon={Bot}
          title="生成课程原稿"
          text="根据素材和路线生成教学稿"
          onClick={() => runGeneration("course_draft")}
        />
        <ActionButton
          icon={WandSparkles}
          title="生成公众号文章"
          text="做格式适配，不丢失含义"
          onClick={() => runGeneration("wechat_article")}
        />
        <ActionButton
          icon={Palette}
          title="生成配套图片"
          text="按名称与路径存储"
          onClick={runImageTask}
        />
        <ActionButton
          icon={MonitorCheck}
          title="打开 mdnice"
          text="自动粘贴、选主题、截图"
          onClick={runMockTask}
        />
      </div>

      <div className="task-card">
        <div className="task-head">
          <span>{taskRunning ? "任务运行中" : "当前空闲"}</span>
          <Play size={15} />
        </div>
        <div className="task-track">
          <span className={taskRunning ? "running" : ""} />
        </div>
        <p>{taskMessage}</p>
      </div>

      <div className="review-card">
        <div className="review-title">审核规则</div>
        {[
          ["summary", "摘要与封面信息完整"],
          ["symbols", "正文禁用符号已检查"],
          ["images", "配套图片名称和路径完整"],
        ].map(([key, label]) => (
          <label className="check-row" key={key}>
            <input
              type="checkbox"
              checked={checkedRules.includes(key)}
              onChange={() => toggleRule(key)}
            />
            <span>{label}</span>
          </label>
        ))}
      </div>
    </aside>
  );
}
