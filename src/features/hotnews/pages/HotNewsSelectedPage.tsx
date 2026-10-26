import { useEffect, useMemo, useState } from "react";
import { CalendarDays, FileText, RefreshCcw, Search } from "lucide-react";
import {
  listHotNewsBriefs,
  listHotNewsConfirmedTopics,
  type HotNewsTopic,
} from "../api";
import { formatConfirmTime } from "../../../shared/format/time";

type MonthGroup = {
  key: string;
  label: string;
  topics: HotNewsTopic[];
};

function beijingParts(iso: string | null | undefined) {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const year = parts.find((p) => p.type === "year")?.value ?? "0000";
  const month = parts.find((p) => p.type === "month")?.value ?? "00";
  const day = parts.find((p) => p.type === "day")?.value ?? "00";
  return { year, month, day };
}

function beijingMonthKey(iso: string | null | undefined): string {
  const parts = beijingParts(iso);
  if (!parts) {
    return "unknown";
  }
  return `${parts.year}-${parts.month}`;
}

/** 北京时间日期键 yyyy-MM-dd，供与 date input 比较。 */
function beijingDayKey(iso: string | null | undefined): string | null {
  const parts = beijingParts(iso);
  if (!parts) {
    return null;
  }
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function monthLabel(key: string, count: number): string {
  if (key === "unknown") {
    return `未标注月份 · ${count} 个选题`;
  }
  const [year, month] = key.split("-");
  return `${year}年${Number(month)}月 · ${count} 个选题`;
}

/** 优先确认时间，其次更新/创建时间。 */
function topicStamp(topic: HotNewsTopic): string | null {
  return topic.confirmedAt || topic.updatedAt || topic.createdAt || null;
}

export function HotNewsSelectedPage({
  themeId,
  themeName,
  showNotice,
  navigate,
}: {
  themeId: string;
  themeName: string;
  showNotice: (title: string, message: string) => void;
  navigate: (path: string) => void;
}) {
  const [topics, setTopics] = useState<HotNewsTopic[]>([]);
  const [summaries, setSummaries] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const [titleQuery, setTitleQuery] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  async function refresh() {
    setBusy(true);
    try {
      const selected = await listHotNewsConfirmedTopics(themeId);
      setTopics(selected);
      const nextSummaries: Record<string, string> = {};
      await Promise.all(
        selected.slice(0, 40).map(async (topic) => {
          try {
            const briefs = await listHotNewsBriefs(topic.id);
            const brief = briefs[0];
            const overview =
              brief?.summary?.overview || brief?.angle || topic.summary || "";
            nextSummaries[topic.id] = overview;
          } catch {
            nextSummaries[topic.id] = topic.summary || "";
          }
        }),
      );
      setSummaries(nextSummaries);
    } catch (error) {
      showNotice(
        "已选题加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [themeId]);

  const filteredTopics = useMemo(() => {
    const normalized = titleQuery.trim().toLowerCase();
    const from = dateFrom.trim();
    const to = dateTo.trim();
    return topics.filter((topic) => {
      const stamp = topicStamp(topic);
      const dayKey = beijingDayKey(stamp);
      if (from || to) {
        if (!dayKey) {
          return false;
        }
        if (from && dayKey < from) {
          return false;
        }
        if (to && dayKey > to) {
          return false;
        }
      }
      if (!normalized) {
        return true;
      }
      return String(topic.title || "")
        .toLowerCase()
        .includes(normalized);
    });
  }, [topics, titleQuery, dateFrom, dateTo]);

  const groups = useMemo(() => {
    const map = new Map<string, HotNewsTopic[]>();
    for (const topic of filteredTopics) {
      const key = beijingMonthKey(topicStamp(topic));
      const list = map.get(key) ?? [];
      list.push(topic);
      map.set(key, list);
    }
    const rows: MonthGroup[] = Array.from(map.entries())
      .map(([key, items]) => ({
        key,
        label: monthLabel(key, items.length),
        topics: items.sort((a, b) =>
          String(topicStamp(b) || "").localeCompare(String(topicStamp(a) || "")),
        ),
      }))
      .sort((a, b) => b.key.localeCompare(a.key));
    return rows;
  }, [filteredTopics]);

  function openTopicResearch(topic: HotNewsTopic) {
    navigate(
      `/modes/hot-news/topics/${encodeURIComponent(topic.id)}/research`,
    );
  }

  return (
    <section className="hot-selected-page">
      <div className="context-line">热点资讯模式 / {themeName}</div>
      <header className="hot-news-hero">
        <div>
          <span className="section-kicker">SELECTED TOPICS</span>
          <h2>已选题</h2>
          <p>汇总全部已确认选题（不限当天），按确认月份查看，点击进入选题资料池。</p>
        </div>
        <div className="hot-news-actions">
          <button className="secondary-button" onClick={() => void refresh()} disabled={busy}>
            <RefreshCcw size={15} />
            {busy ? "刷新中..." : "刷新"}
          </button>
        </div>
      </header>

      <div className="hot-selected-filters" aria-label="已选题筛选">
        <label className="hot-selected-search">
          <Search size={14} />
          <input
            type="search"
            value={titleQuery}
            placeholder="按标题模糊搜索"
            onChange={(event) => setTitleQuery(event.target.value)}
          />
        </label>
        <div className="hot-selected-date-range">
          <span>确认日期</span>
          <input
            type="date"
            value={dateFrom}
            max={dateTo || undefined}
            aria-label="确认起始日期"
            onChange={(event) => setDateFrom(event.target.value)}
          />
          <span className="hot-selected-date-sep">至</span>
          <input
            type="date"
            value={dateTo}
            min={dateFrom || undefined}
            aria-label="确认结束日期"
            onChange={(event) => setDateTo(event.target.value)}
          />
        </div>
        {(titleQuery.trim() || dateFrom || dateTo) && (
          <button
            type="button"
            className="ghost-button"
            onClick={() => {
              setTitleQuery("");
              setDateFrom("");
              setDateTo("");
            }}
          >
            清空筛选
          </button>
        )}
      </div>

      {groups.length === 0 ? (
        <div className="hot-brief-empty">
          <strong>{topics.length === 0 ? "还没有已确认选题" : "没有匹配的选题"}</strong>
          <span>
            {topics.length === 0
              ? "在热点雷达确认选题后，会出现在这里。"
              : "试试调整标题关键词或确认日期范围。"}
          </span>
        </div>
      ) : (
        groups.map((group) => {
          const isCollapsed = collapsed[group.key] === true;
          return (
            <section className="course-group" key={group.key}>
              <button
                type="button"
                className="course-group-head"
                onClick={() =>
                  setCollapsed((prev) => ({
                    ...prev,
                    [group.key]: !isCollapsed,
                  }))
                }
              >
                <CalendarDays size={15} />
                <span>{group.label}</span>
              </button>
              {isCollapsed ? null : (
                <div className="course-group-body">
                  {group.topics.map((topic) => (
                    <button
                      key={topic.id}
                      type="button"
                      className="course-row"
                      onClick={() => openTopicResearch(topic)}
                    >
                      <FileText size={15} />
                      <div>
                        <strong>{topic.title}</strong>
                        <span className="hot-selected-confirm-time">
                          确认于 {formatConfirmTime(topicStamp(topic))}
                        </span>
                        <small>
                          {summaries[topic.id] || topic.summary || "暂无摘要"}
                        </small>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </section>
          );
        })
      )}
    </section>
  );
}
