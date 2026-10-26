// @ts-nocheck
import { HotNewsPageViewHeader } from "./HotNewsPageViewHeader";
import { HotNewsPageViewMain } from "./HotNewsPageViewMain";
import { HotNewsPageViewSide } from "./HotNewsPageViewSide";
import { HotNewsPageViewDetail } from "./HotNewsPageViewDetail";

/** 热点雷达页视图壳。 */
export function HotNewsPageView(props: Record<string, any>) {
  return (
    <section className="hot-news-page">
      <HotNewsPageViewHeader {...props} />
      <div className="hot-news-layout">
        <HotNewsPageViewMain {...props} />
        <HotNewsPageViewSide {...props} />
      </div>
      <HotNewsPageViewDetail {...props} />
    </section>
  );
}
