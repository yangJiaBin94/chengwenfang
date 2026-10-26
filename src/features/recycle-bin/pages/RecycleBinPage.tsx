import type { Dispatch, SetStateAction } from "react";
import { Trash2 } from "lucide-react";
import type { ContentThemeData } from "../../course/api";
import { restoreContentTheme } from "../../course/api";

export type RecycleBinPageProps = {
  deletedThemes: ContentThemeData[];
  themeDeletedNotice: string;
  setDeletedThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  setHotNewsThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  setContentThemes: Dispatch<SetStateAction<ContentThemeData[]>>;
  setThemeDeletedNotice: (notice: string) => void;
  showNotice: (title: string, message: string) => void;
};

export function RecycleBinPage({
  deletedThemes,
  themeDeletedNotice,
  setDeletedThemes,
  setHotNewsThemes,
  setContentThemes,
  setThemeDeletedNotice,
  showNotice,
}: RecycleBinPageProps) {
  return (
            <section className="mode-overview recycle-bin-page">
              <header>
                <span className="section-kicker">Recycle Bin</span>
                <h2>回收站</h2>
                <p>
                  已删除的主题在此列出（含课程体系与热点资讯）。恢复后默认为停用，需再到对应模式的主题列表中启用。
                </p>
              </header>
              {themeDeletedNotice && (
                <div className="theme-deleted-notice">{themeDeletedNotice}</div>
              )}
              {deletedThemes.length === 0 ? (
                <div className="recycle-bin-empty">
                  <Trash2 size={28} />
                  <strong>回收站为空</strong>
                  <p>删除主题后会出现在这里，关联数据会保留。</p>
                </div>
              ) : (
                <div className="theme-deleted-list">
                  {deletedThemes.map((theme) => {
                    const isHotNews = theme.modeId === "hot_news";
                    const modeLabel = isHotNews
                      ? "热点资讯模式"
                      : "课程体系教学模式";
                    const summary = isHotNews
                      ? `${theme.itemCount ?? 0} 来源条目 · ${theme.candidateCount ?? 0} 候选题`
                      : `${theme.seriesCount} 个系列 · ${theme.courseCount} 门课`;
                    return (
                      <div className="theme-deleted-row" key={theme.id}>
                        <div>
                          <div className="theme-deleted-title-row">
                            <strong>{theme.name}</strong>
                            <span
                              className={`theme-deleted-mode-badge${
                                isHotNews ? " is-hot-news" : ""
                              }`}
                            >
                              {modeLabel}
                            </span>
                          </div>
                          <small>
                            {summary}
                            {theme.description ? ` · ${theme.description}` : ""}
                          </small>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            void restoreContentTheme(theme.id)
                              .then((restored) => {
                                setDeletedThemes((current) =>
                                  current.filter((item) => item.id !== theme.id),
                                );
                                if (restored.modeId === "hot_news") {
                                  setHotNewsThemes((current) => [
                                    ...current.filter(
                                      (item) => item.id !== restored.id,
                                    ),
                                    restored,
                                  ]);
                                } else {
                                  setContentThemes((current) => [
                                    ...current.filter(
                                      (item) => item.id !== restored.id,
                                    ),
                                    restored,
                                  ]);
                                }
                                setThemeDeletedNotice("");
                                showNotice(
                                  "已恢复",
                                  `${restored.name}（已停用，请到${
                                    restored.modeId === "hot_news"
                                      ? "热点资讯"
                                      : "课程体系"
                                  }主题列表启用）`,
                                );
                              })
                              .catch((error: unknown) =>
                                showNotice(
                                  "恢复失败",
                                  error instanceof Error
                                    ? error.message
                                    : "无法恢复主题",
                                ),
                              );
                          }}
                        >
                          恢复
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
  );
}
