// @ts-nocheck
import { CheckCircle2, ChevronDown } from "lucide-react";
import type { ContentThemeData } from "../api";

export type CourseThemeSwitcherProps = {
  contentThemes: any,
  dataThemeName: any,
  navigate: any,
  selectedThemeId: any,
  setSelectedThemeId: any,
  setThemePickerOpen: any,
  themePickerOpen: any,
  workspaceFallback: any
};

export function CourseThemeSwitcher({
  contentThemes,
  dataThemeName,
  navigate,
  selectedThemeId,
  setSelectedThemeId,
  setThemePickerOpen,
  themePickerOpen,
  workspaceFallback
}: CourseThemeSwitcherProps) {
  return (
    <div className="theme-switcher">
      <button
        className="theme-switcher-button"
        type="button"
        aria-expanded={themePickerOpen}
        onClick={() => setThemePickerOpen((open) => !open)}
      >
        <span>{dataThemeName}</span>
        <ChevronDown size={14} />
      </button>
      {themePickerOpen && (
        <div className="theme-switcher-menu">
          {(contentThemes.length
            ? contentThemes.filter(
                (theme) => theme.status !== "disabled",
              )
            : [
                {
                  id: workspaceFallback.themeId,
                  name: workspaceFallback.themeName,
                  code: workspaceFallback.themeCode,
                } as ContentThemeData,
              ]
          ).map((theme) => (
            <button
              className={
                theme.id === selectedThemeId ? "selected" : ""
              }
              key={theme.id}
              type="button"
              onClick={() => {
                setSelectedThemeId(theme.id);
                setThemePickerOpen(false);
                navigate(
                  `/modes/course-system/themes/${theme.code.replaceAll("_", "-")}/route`,
                );
              }}
            >
              <span>{theme.name}</span>
              {theme.id === selectedThemeId && (
                <CheckCircle2 size={15} />
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
