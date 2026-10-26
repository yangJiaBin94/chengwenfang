// @ts-nocheck
import { HotNewsResearchViewTop } from "./HotNewsResearchViewTop";
import { HotNewsResearchViewBottom } from "./HotNewsResearchViewBottom";

export type HotNewsResearchViewProps = {
  active: any,
  articleBusy: any,
  articleCount: any,
  brief: any,
  buildSkeleton: any,
  busy: any,
  conflicts: any,
  currentStep: any,
  enterWorkspace: any,
  friendlyParseError: any,
  indeterminate: any,
  itemCount: any,
  items: any,
  latest: any,
  latestStatus: any,
  listLoading: any,
  listPage: any,
  listPageCount: any,
  manualBusy: any,
  manualOpen: any,
  manualUrl: any,
  mediaFilter: any,
  navigate: any,
  pageSize: any,
  pageSizeOpen: any,
  pageSizeRef: any,
  pendingVideoCount: any,
  progress: any,
  query: any,
  refresh: any,
  research: any,
  resultItem: any,
  selectedPath: any,
  setListPage: any,
  setManualOpen: any,
  setManualUrl: any,
  setMediaFilter: any,
  setPageSize: any,
  setPageSizeOpen: any,
  setQuery: any,
  setResultItem: any,
  setSkeletonOpen: any,
  skeleton: any,
  skeletonBusy: any,
  skeletonOpen: any,
  startBatchVideoParse: any,
  startItemVideoParse: any,
  submitManualItem: any,
  total: any,
  videoCount: any,
  videoParseBusy: any,
  videoParseItemId: any,
  videoParseMessage: any,
  videoParseMeta: any,
  videoParseProgress: any,
  videoParseScope: any,
  videoParseStep: any
};

export function HotNewsResearchView(props: HotNewsResearchViewProps) {
  return (
    <section className="hot-research-page">
      <HotNewsResearchViewTop {...props} />
      <HotNewsResearchViewBottom {...props} />
    </section>
  );
}
