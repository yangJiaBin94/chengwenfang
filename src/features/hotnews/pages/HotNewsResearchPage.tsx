// @ts-nocheck
import { HotNewsResearchView } from "../components/HotNewsResearchView";
import { useHotNewsResearchPage } from "../hooks/useHotNewsResearchPage";

export function HotNewsResearchPage({
  topicId,
  themeSlug,
  showNotice,
  confirmAction,
  navigate,
}: {
  topicId: string;
  themeSlug: string;
  showNotice: (title: string, message: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  navigate: (path: string) => void;
}) {
  const viewProps = useHotNewsResearchPage({
    topicId,
    themeSlug,
    showNotice,
    confirmAction,
    navigate,
  });
  return <HotNewsResearchView {...viewProps} />;
}
