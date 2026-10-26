package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.SiteKeyUtil;
import com.chengwenfang.crawler.facade.dto.view.FetchedItemView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.sourcing.facade.SearchQueryDiagnoseRules;
import com.chengwenfang.sourcing.facade.constant.SearchQueryDiagnoseKeys;
import com.chengwenfang.sourcing.facade.dto.request.QuerySiteBlockUpsertRequest;
import com.chengwenfang.sourcing.facade.dto.view.QuerySiteBlockView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 搜索词 × 网站诊断：按主题过滤偏题桶统计并 upsert 拉黑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryDiagnoseService {
  private final HotNewsFacade hotNews;

  /**
   * 加载主题下生效拉黑，构建查找键集合。
   *
   * @param themeId 主题 ID
   * @return 键集合：channelType|searchQuery|siteKey（小写）
   */
  public Set<String> loadActiveBlockKeys(String themeId) {
    Set<String> keys = new HashSet<>();
    if (StrUtil.isBlank(themeId)) {
      return keys;
    }
    for (QuerySiteBlockView block : hotNews.listActiveQuerySiteBlocks(themeId)) {
      if (block == null) {
        continue;
      }
      String key = blockKey(block.getChannelType(), block.getSearchQuery(), block.getSiteKey());
      if (StrUtil.isNotBlank(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * 是否命中生效拉黑。
   *
   * @param blockKeys   拉黑键集合
   * @param channelType 渠道类型
   * @param searchQuery 搜索词
   * @param url         结果 URL
   * @return true 表示应跳过
   */
  public boolean isBlocked(Set<String> blockKeys, String channelType, String searchQuery, String url) {
    if (blockKeys == null || blockKeys.isEmpty()) {
      return false;
    }
    String siteKey = SiteKeyUtil.fromUrl(url);
    String key = blockKey(channelType, searchQuery, siteKey);
    return StrUtil.isNotBlank(key) && blockKeys.contains(key);
  }

  /**
   * 根据本轮桶统计写入拉黑（最多 {@link SearchQueryDiagnoseKeys#MAX_BLOCKS_PER_FETCH} 组）。
   *
   * @param themeId 主题 ID
   * @param buckets 桶统计
   * @return 本轮新增/强化的拉黑视图
   */
  public List<QuerySiteBlockView> diagnoseAndUpsert(String themeId, List<BucketStat> buckets) {
    if (StrUtil.isBlank(themeId) || buckets == null || buckets.isEmpty()) {
      return List.of();
    }
    List<BucketStat> triggered = buckets.stream()
        .filter(bucket -> bucket != null && SearchQueryDiagnoseRules.shouldBlock(bucket.skipped(), bucket.total()))
        .sorted(Comparator
            .comparingInt(BucketStat::skipped)
            .reversed()
            .thenComparing(bucket -> -bucket.skipRatio()))
        .limit(SearchQueryDiagnoseKeys.MAX_BLOCKS_PER_FETCH)
        .toList();
    if (triggered.isEmpty()) {
      return List.of();
    }
    List<QuerySiteBlockUpsertRequest> requests = new ArrayList<>();
    for (BucketStat bucket : triggered) {
      requests.add(QuerySiteBlockUpsertRequest.builder()
          .themeId(themeId)
          .channelType(bucket.channelType())
          .searchQuery(bucket.searchQuery())
          .siteKey(bucket.siteKey())
          .hitDelta(bucket.skipped())
          .sampleTitles(bucket.sampleTitles())
          .reason(SearchQueryDiagnoseRules.reason(bucket.skipped(), bucket.total()))
          .build());
    }
    List<QuerySiteBlockView> written = hotNews.upsertQuerySiteBlocks(requests);
    log.info(
        "搜索词诊断写入拉黑 themeId={} count={} samples={}",
        themeId,
        written.size(),
        written.stream()
            .map(view -> view.getSearchQuery() + " × " + view.getSiteKey())
            .toList());
    return written;
  }

  /**
   * 构造进度提示文案。
   *
   * @param blocks 本轮拉黑
   * @return 文案；无拉黑时返回空串
   */
  public static String progressMessage(List<QuerySiteBlockView> blocks) {
    if (blocks == null || blocks.isEmpty()) {
      return "";
    }
    QuerySiteBlockView first = blocks.get(0);
    String sample = StrUtil.blankToDefault(first.getSearchQuery(), "?")
        + " × "
        + StrUtil.blankToDefault(first.getSiteKey(), "?");
    if (blocks.size() == 1) {
      return "搜索词诊断：拉黑 1 组（" + sample + "）";
    }
    return "搜索词诊断：拉黑 " + blocks.size() + " 组（含 " + sample + "）";
  }

  /**
   * 聚合桶键。
   *
   * @param channelType 渠道类型
   * @param searchQuery 搜索词
   * @param siteKey     站点键
   * @return 规范化键；缺字段时返回空串
   */
  public static String blockKey(String channelType, String searchQuery, String siteKey) {
    if (StrUtil.isBlank(channelType) || StrUtil.isBlank(searchQuery) || StrUtil.isBlank(siteKey)) {
      return "";
    }
    String normalizedQuery = StrUtil.subPre(
        searchQuery.trim(), SearchQueryDiagnoseKeys.MAX_SEARCH_QUERY_LEN);
    String normalizedSite = StrUtil.subPre(
        siteKey.trim().toLowerCase(Locale.ROOT), SearchQueryDiagnoseKeys.MAX_SITE_KEY_LEN);
    return channelType.trim().toLowerCase(Locale.ROOT)
        + "|"
        + normalizedQuery
        + "|"
        + normalizedSite;
  }

  /**
   * 从抓取条目累计桶统计（仅计入可归因条目）。
   *
   * @param channelType 渠道类型
   * @param item        抓取条目
   * @param themeSkipped 是否被主题过滤丢弃
   * @param buckets     桶聚合表
   */
  public static void accumulate(
      String channelType,
      FetchedItemView item,
      boolean themeSkipped,
      Map<String, MutableBucket> buckets) {
    if (item == null || buckets == null) {
      return;
    }
    String searchQuery = StrUtil.subPre(
        StrUtil.trim(item.getSearchQuery()), SearchQueryDiagnoseKeys.MAX_SEARCH_QUERY_LEN);
    String siteKey = StrUtil.subPre(
        SiteKeyUtil.fromUrl(item.getUrl()), SearchQueryDiagnoseKeys.MAX_SITE_KEY_LEN);
    String key = blockKey(channelType, searchQuery, siteKey);
    if (StrUtil.isBlank(key)) {
      return;
    }
    MutableBucket bucket = buckets.computeIfAbsent(
        key,
        ignored -> new MutableBucket(
            channelType.trim().toLowerCase(Locale.ROOT),
            searchQuery,
            siteKey));
    bucket.total++;
    if (themeSkipped) {
      bucket.skipped++;
      if (StrUtil.isNotBlank(item.getTitle())
          && bucket.sampleTitles.size() < SearchQueryDiagnoseKeys.MAX_SAMPLE_TITLES) {
        bucket.sampleTitles.add(item.getTitle().trim());
      }
    }
  }

  /**
   * 可变桶 → 不可变统计列表。
   *
   * @param buckets 可变桶
   * @return 统计列表
   */
  public static List<BucketStat> freeze(Map<String, MutableBucket> buckets) {
    if (buckets == null || buckets.isEmpty()) {
      return List.of();
    }
    List<BucketStat> out = new ArrayList<>(buckets.size());
    for (MutableBucket bucket : buckets.values()) {
      out.add(new BucketStat(
          bucket.channelType,
          bucket.searchQuery,
          bucket.siteKey,
          bucket.skipped,
          bucket.total,
          List.copyOf(bucket.sampleTitles)));
    }
    return out;
  }

  /**
   * 将渠道桶快照合并进全局聚合表。
   *
   * @param target  目标聚合表
   * @param buckets 渠道桶列表
   */
  public static void mergeBuckets(Map<String, MutableBucket> target, List<BucketStat> buckets) {
    if (target == null || buckets == null) {
      return;
    }
    for (BucketStat source : buckets) {
      if (source == null) {
        continue;
      }
      String key = blockKey(source.channelType(), source.searchQuery(), source.siteKey());
      if (StrUtil.isBlank(key)) {
        continue;
      }
      MutableBucket bucket = target.computeIfAbsent(
          key,
          ignored -> new MutableBucket(
              source.channelType(), source.searchQuery(), source.siteKey()));
      bucket.total += source.total();
      bucket.skipped += source.skipped();
      for (String title : source.sampleTitles()) {
        if (StrUtil.isBlank(title)) {
          continue;
        }
        if (bucket.sampleTitles.size() >= SearchQueryDiagnoseKeys.MAX_SAMPLE_TITLES) {
          break;
        }
        if (!bucket.sampleTitles.contains(title.trim())) {
          bucket.sampleTitles.add(title.trim());
        }
      }
    }
  }

  /**
   * 新建桶聚合表（保序）。
   *
   * @return 空表
   */
  public static Map<String, MutableBucket> newBucketMap() {
    return new LinkedHashMap<>();
  }

  /** 可变桶。 */
  public static final class MutableBucket {
    private final String channelType;
    private final String searchQuery;
    private final String siteKey;
    private int skipped;
    private int total;
    private final List<String> sampleTitles = new ArrayList<>();

    /**
     * @param channelType 渠道类型
     * @param searchQuery 搜索词
     * @param siteKey     站点键
     */
    public MutableBucket(String channelType, String searchQuery, String siteKey) {
      this.channelType = channelType;
      this.searchQuery = searchQuery;
      this.siteKey = siteKey;
    }
  }

  /**
   * 桶统计快照。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class BucketStat {
    /** 渠道类型。 */
    private final String channelType;
    /** 搜索词。 */
    private final String searchQuery;
    /** 站点键。 */
    private final String siteKey;
    /** 主题过滤丢弃数。 */
    private final int skipped;
    /** 原始条数。 */
    private final int total;
    /** 样例标题。 */
    private final List<String> sampleTitles;

    /**
     * @return 丢弃占比；total≤0 时为 0
     */
    public double skipRatio() {
      return total <= 0 ? 0d : ((double) skipped) / total;
    }
  }
}
