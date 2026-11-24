package com.chengwenfang.app.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.HotNewsThemeBriefingSupport;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeCommitOrchestrator;
import com.chengwenfang.publishing.facade.constant.BuiltinLayoutThemeKeys;
import com.chengwenfang.prompt.facade.PromptPackBindingIds;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.WorkflowFacade;
import com.chengwenfang.prompt.facade.constant.CoursePromptPackNames;
import com.chengwenfang.prompt.facade.dto.request.PromptPackSaveRequest;
import com.chengwenfang.prompt.facade.dto.request.WorkflowDraftSaveRequest;
import com.chengwenfang.prompt.facade.dto.request.WorkflowPublishRequest;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import com.chengwenfang.prompt.facade.dto.view.WorkflowSummaryView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 把仓库种子导入数据库。
 * <p>已有同名同场景提示词包、已发布工作流默认跳过，避免覆盖库内修改。
 * 空库自动导入与 {@code bootstrap-seed} 补种脚本共用本服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltinCatalogSeedService {
  /** 课程演示主题固定 id（与 default-themes.json 一致）。 */
  static final String COURSE_SEED_THEME_ID = "theme-ai-self-study-zero";
  /** 热点演示主题固定 id（与 default-themes.json 一致）。 */
  static final String HOT_NEWS_SEED_THEME_ID = "theme-ai-frontier";

  /** 提示词包门面。 */
  private final PromptPackFacade promptPacks;
  /** 工作流门面。 */
  private final WorkflowFacade workflows;
  /** 内容门面（默认主题课壳）。 */
  private final ContentFacade content;
  /** 热点主题确认编排（与手动 wizard 同一路径：启用 + 初始渠道）。 */
  private final HotNewsThemeCommitOrchestrator hotNewsThemeCommit;
  /** 排版主题直写 JDBC（bootstrap 一次性导入）。 */
  private final JdbcTemplate jdbc;
  /** JSON 解析。 */
  private final ObjectMapper objectMapper;

  /** 种子文件路径，相对路径会向上查找仓库根。 */
  @Value("${chengwenfang.bootstrap.catalog:scripts/bootstrap/builtin-catalog.json}")
  private String catalogPath;

  /** 默认课程/热点主题种子（问卷 + 课表结构，无正文）。 */
  @Value("${chengwenfang.bootstrap.default-themes:scripts/bootstrap/default-themes.json}")
  private String defaultThemesPath;

  /** 信源沉淀池种子（跨主题 RSS 等，按 code 幂等导入）。 */
  @Value("${chengwenfang.bootstrap.source-channel-catalog:scripts/bootstrap/source-channel-catalog.json}")
  private String sourceChannelCatalogPath;

  /** 为 true 时用种子覆盖已有内置提示词包 与工作流发布版本。 */
  @Value("${chengwenfang.bootstrap.force:false}")
  private boolean force;

  /**
   * 按当前配置的种子路径导入。
   *
   * @param forceOverwrite 为 true 时覆盖已有内置行
   * @throws Exception 解析或写入失败
   */
  public void importConfigured(boolean forceOverwrite) throws Exception {
    boolean previous = this.force;
    this.force = forceOverwrite;
    try {
      importCatalog(resolveCatalog(catalogPath));
    } finally {
      this.force = previous;
    }
  }

  /**
   * 配置里的强制覆盖开关（补种脚本使用）。
   *
   * @return 是否强制覆盖
   */
  public boolean isForce() {
    return force;
  }

  /**
   * 没有未删除提示词包、也没有工作流定义时视为空库。
   *
   * @return 是否空库
   */
  public boolean isEmptyLibrary() {
    return libraryEmpty(countRows(
        "SELECT COUNT(1) FROM prompt_packs WHERE deleted_at IS NULL"),
        countRows("SELECT COUNT(1) FROM workflow_definitions"));
  }

  /**
   * 是否需要自动补种：空库，或演示主题/已发布工作流基线未齐。
   * <p>避免「提示词包已写入、后续主题失败」后永远跳过。
   *
   * @return 需要自动导入则为 true
   */
  public boolean needsAutoSeed() {
    if (isEmptyLibrary()) {
      return true;
    }
    return !seedBaselineComplete(
        countPublishedWorkflows(),
        countThemeCourses(COURSE_SEED_THEME_ID),
        countHotNewsChannels(HOT_NEWS_SEED_THEME_ID));
  }

  /**
   * 自动种子后的基线是否已齐（三条已发布工作流 + 两演示主题有内容）。
   *
   * @return 齐则为 true
   */
  public boolean isSeedBaselineComplete() {
    return seedBaselineComplete(
        countPublishedWorkflows(),
        countThemeCourses(COURSE_SEED_THEME_ID),
        countHotNewsChannels(HOT_NEWS_SEED_THEME_ID));
  }

  /**
   * 提示词包与工作流都还没有时视为空库。
   *
   * @param promptPacks 未删除提示词包数
   * @param workflows   工作流定义数
   * @return 是否空库
   */
  static boolean libraryEmpty(int promptPacks, int workflows) {
    return promptPacks == 0 && workflows == 0;
  }

  /**
   * 种子基线是否齐备。
   *
   * @param publishedWorkflows 已发布工作流定义数
   * @param courseLessons      课程演示主题课壳数
   * @param hotNewsChannels    热点演示主题渠道数
   * @return 齐则为 true
   */
  static boolean seedBaselineComplete(int publishedWorkflows, int courseLessons, int hotNewsChannels) {
    return publishedWorkflows >= 3 && courseLessons > 0 && hotNewsChannels > 0;
  }

  /**
   * 读取计数；空结果按 0。
   *
   * @param sql 计数语句
   * @return 行数
   */
  private int countRows(String sql) {
    Integer count = jdbc.queryForObject(sql, Integer.class);
    return count == null ? 0 : count;
  }

  /**
   * 已发布工作流定义数。
   *
   * @return 行数
   */
  private int countPublishedWorkflows() {
    return countRows(
        "SELECT COUNT(1) FROM workflow_definitions WHERE published_version_id IS NOT NULL"
            + " AND TRIM(published_version_id) <> ''");
  }

  /**
   * 读取目录并按条导入。
   *
   * @param catalogFile 种子文件
   * @throws Exception 解析或写入失败
   */
  void importCatalog(Path catalogFile) throws Exception {
    JsonNode root = objectMapper.readTree(catalogFile.toFile());
    // 先迁旧 提示词包 名，避免后续按新名 upsert 再插一行
    if (promptPacks.migrateLegacyRulesPromptPackName()) {
      log.info(
          "提示词包名称已同步：{} → {}",
          CoursePromptPackNames.LEGACY_RULES,
          CoursePromptPackNames.CHENGWENFANG_RULES);
    }
    int promptPackCreated = 0;
    int promptPackSkipped = 0;
    JsonNode promptPackNodes = root.path("promptPacks");
    if (promptPackNodes.isArray()) {
      for (JsonNode promptPackNode : promptPackNodes) {
        if (upsertPromptPack(promptPackNode)) {
          promptPackCreated++;
        } else {
          promptPackSkipped++;
        }
      }
    }
    int workflowPublished = 0;
    int workflowSkipped = 0;
    JsonNode workflowNodes = root.path("workflows");
    if (workflowNodes.isArray()) {
      for (JsonNode workflowNode : workflowNodes) {
        if (publishWorkflow(workflowNode)) {
          workflowPublished++;
        } else {
          workflowSkipped++;
        }
      }
    }
    int themeWritten = 0;
    int themeSkipped = 0;
    // 新键 layoutThemes；兼容旧种子 publishingThemes
    JsonNode themeNodes = root.path("layoutThemes");
    if (!themeNodes.isArray()) {
      themeNodes = root.path("publishingThemes");
    }
    if (themeNodes.isArray()) {
      for (JsonNode themeNode : themeNodes) {
        if (upsertLayoutTheme(themeNode)) {
          themeWritten++;
        } else {
          themeSkipped++;
        }
      }
    }
    // 沉淀池与演示主题互不依赖；主题确认失败不能挡住渠道种子
    int[] channelCatalogStats = importSourceChannelCatalog(resolveCatalog(sourceChannelCatalogPath));
    importDefaultThemes(resolveCatalog(defaultThemesPath));
    log.info(
        "基础配置导入完成 promptPacksWritten={} promptPacksSkipped={} workflowsPublished={} workflowsSkipped={} themesWritten={} themesSkipped={} sourceChannelsWritten={} sourceChannelsSkipped={} force={}",
        promptPackCreated,
        promptPackSkipped,
        workflowPublished,
        workflowSkipped,
        themeWritten,
        themeSkipped,
        channelCatalogStats[0],
        channelCatalogStats[1],
        force);
  }

  /**
   * 导入信源沉淀池：按 {@code code} 缺则插；{@code force} 时覆盖元数据。
   *
   * @param catalogFile source-channel-catalog.json
   * @return {@code [written, skipped]}
   * @throws Exception 解析或落库失败
   */
  private int[] importSourceChannelCatalog(Path catalogFile) throws Exception {
    JsonNode root = objectMapper.readTree(catalogFile.toFile());
    JsonNode channels = root.path("channels");
    int written = 0;
    int skipped = 0;
    if (!channels.isArray()) {
      log.warn("信源沉淀池种子无 channels 数组，跳过 file={}", catalogFile);
      return new int[] {0, 0};
    }
    for (JsonNode channelNode : channels) {
      if (upsertSourceChannelCatalogEntry(channelNode)) {
        written++;
      } else {
        skipped++;
      }
    }
    return new int[] {written, skipped};
  }

  /**
   * 按业务 code 写入一条沉淀池渠道。
   *
   * @param channelNode 渠道节点
   * @return true 表示本次写入或覆盖
   * @throws Exception JSON 序列化失败
   */
  private boolean upsertSourceChannelCatalogEntry(JsonNode channelNode) throws Exception {
    String code = text(channelNode, "code").trim();
    if (StrUtil.isBlank(code)) {
      log.warn("跳过无 code 的沉淀池条目");
      return false;
    }
    String name = text(channelNode, "name").trim();
    String channelType = StrUtil.blankToDefault(text(channelNode, "channelType"), "rss").trim();
    String baseUrl = text(channelNode, "baseUrl").trim();
    if (StrUtil.isBlank(name) || StrUtil.isBlank(baseUrl)) {
      log.warn("跳过沉淀池条目 code={}：name/baseUrl 为空", code);
      return false;
    }
    JsonNode configNode = channelNode.get("config");
    String configJson = null;
    if (configNode != null && !configNode.isNull()) {
      configJson = objectMapper.writeValueAsString(configNode);
    }
    JsonNode tagsNode = channelNode.path("tags");
    List<String> tags = tagsNode.isArray()
        ? objectMapper.convertValue(tagsNode, new TypeReference<List<String>>() {})
        : List.of();
    String tagsJson = objectMapper.writeValueAsString(tags);
    boolean enabled = !channelNode.path("enabled").isBoolean() || channelNode.path("enabled").asBoolean(true);
    int sortOrder = channelNode.path("sortOrder").asInt(100);
    List<Map<String, Object>> existing = jdbc.queryForList(
        "SELECT id FROM source_channel_catalog WHERE code=? LIMIT 1", code);
    if (!existing.isEmpty() && !force) {
      log.info("跳过已有沉淀池渠道 code={}", code);
      return false;
    }
    if (existing.isEmpty()) {
      String id = StrUtil.blankToDefault(text(channelNode, "id").trim(), IdsUtil.newId());
      jdbc.update(
          "INSERT INTO source_channel_catalog "
              + "(id, code, name, channel_type, base_url, config_json, tags_json, enabled, sort_order, created_at, updated_at) "
              + "VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
          id, code, name, channelType, baseUrl, configJson, tagsJson, enabled, sortOrder);
      log.info("创建沉淀池渠道 code={} name={} tags={}", code, name, tagsJson);
      return true;
    }
    String id = String.valueOf(existing.get(0).get("id"));
    jdbc.update(
        "UPDATE source_channel_catalog SET name=?, channel_type=?, base_url=?, config_json=?, tags_json=?, "
            + "enabled=?, sort_order=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        name, channelType, baseUrl, configJson, tagsJson, enabled, sortOrder, id);
    log.info("覆盖沉淀池渠道 code={} name={} tags={}", code, name, tagsJson);
    return true;
  }

  /**
   * 导入默认课程/热点主题：问卷 + 课表结构（课壳），不生成正文。
   *
   * @param themesFile default-themes.json
   * @throws Exception 解析或落库失败
   */
  private void importDefaultThemes(Path themesFile) throws Exception {
    JsonNode root = objectMapper.readTree(themesFile.toFile());
    // 热点演示主题在未确认主要意图时会拒绝启用；不能因此丢掉课程主题或已经写入的沉淀池
    seedThemeIsolated("课程演示主题", () -> seedCourseTheme(root.path("courseTheme")));
    seedThemeIsolated("热点演示主题", () -> seedHotNewsTheme(root.path("hotNewsTheme")));
  }

  /**
   * 单个演示主题失败只记日志，让同一次导入的其余种子继续。
   *
   * @param label 主题说明
   * @param seed  导入动作
   */
  private void seedThemeIsolated(String label, Runnable seed) {
    try {
      seed.run();
    } catch (RuntimeException exception) {
      log.warn(
          "{}未完成，其余种子继续。可稍后执行补种脚本或检查主题问卷是否齐全。原因：{}",
          label,
          exception.getMessage());
    }
  }

  /**
   * 种子课程体系默认主题：briefing → curriculum draft → commit（系列+课壳）。
   *
   * @param node courseTheme 节点
   */
  private void seedCourseTheme(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    String themeId = text(node, "id");
    if (StrUtil.isBlank(themeId)) {
      themeId = "theme-ai-self-study-zero";
    }
    ContentThemeView theme = content.ensureSeedTheme(
        themeId,
        ContentModeIds.COURSE_SYSTEM_CODE,
        text(node, "code"),
        text(node, "name"),
        text(node, "description"),
        1);
    int seriesRows = countThemeSeries(theme.getId());
    int courseRows = countThemeCourses(theme.getId());
    // 已确认且已有课表：跳过；已确认但 0 课：回退为 draft 再补全课表
    if ((ContentThemeStatus.ENABLED.matches(theme.getStatus())
            || ContentThemeStatus.DISABLED.matches(theme.getStatus()))
        && (seriesRows > 0 || courseRows > 0)) {
      log.info(
          "跳过已确认课程种子主题 id={} status={} series={} courses={}",
          theme.getId(),
          theme.getStatus(),
          seriesRows,
          courseRows);
      return;
    }
    if (ContentThemeStatus.ENABLED.matches(theme.getStatus())
        || ContentThemeStatus.DISABLED.matches(theme.getStatus())) {
      reopenEmptySeedTheme(theme.getId());
      log.info("空课表种子主题已回退为 draft 以补全课壳 id={}", theme.getId());
    }
    // 非 force：用户已推进的问卷保留；ensureSeedTheme 的 step=1 stub 仍覆盖为完整种子
    if (force || !shouldPreserveCourseBriefing(objectMapper, theme.getBriefingJson())) {
      Map<String, Object> briefing = objectMapper.convertValue(
          node.path("briefing"), new TypeReference<Map<String, Object>>() {});
      content.patchThemeBriefing(
          theme.getId(),
          ThemeBriefingPatchRequest.builder()
              .name(text(node, "name"))
              .description(text(node, "description"))
              .code(text(node, "code"))
              .briefing(FlexiblePayload.of(briefing))
              .build());
    } else {
      log.info("保留已有课程主题问卷，不覆盖种子 briefing id={}", theme.getId());
    }
    CurriculumDraftView draft = objectMapper.convertValue(
        node.path("curriculumDraft"), CurriculumDraftView.class);
    content.saveCurriculumDraft(
        theme.getId(), CurriculumDraftSaveRequest.builder().draft(draft).build());
    content.commitCurriculum(theme.getId());
    log.info("已确认课程种子主题 id={} courses={}", theme.getId(), draft.getSeries() == null ? 0
        : draft.getSeries().stream().mapToInt(series -> series.getLessons() == null ? 0 : series.getLessons().size()).sum());
  }

  /**
   * 统计主题下系列行数。
   *
   * @param themeId 主题 id
   * @return 行数
   */
  private int countThemeSeries(String themeId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(1) FROM course_series WHERE theme_id=?", Integer.class, themeId);
    return count == null ? 0 : count;
  }

  /**
   * 统计主题下课壳行数（course_lessons 经 series 归属主题）。
   *
   * @param themeId 主题 id
   * @return 行数
   */
  private int countThemeCourses(String themeId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(1) FROM course_lessons cl "
            + "INNER JOIN course_series cs ON cs.id=cl.series_id WHERE cs.theme_id=?",
        Integer.class,
        themeId);
    return count == null ? 0 : count;
  }

  /**
   * 将无课表的已确认种子主题改回 draft，以便走正常 commit 路径。
   *
   * @param themeId 主题 id
   */
  private void reopenEmptySeedTheme(String themeId) {
    jdbc.update(
        "UPDATE themes SET status=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        ContentThemeStatus.DRAFT.getValue(),
        themeId);
  }

  /**
   * 库内热点问卷是否已满足启用校验（可跳过种子覆盖）。
   *
   * @param briefingJson 主题 briefing_json
   * @return 可 commit 则为 true
   */
  private boolean hotNewsBriefingCommitReady(String briefingJson) {
    if (StrUtil.isBlank(briefingJson)) {
      return false;
    }
    try {
      Map<String, Object> briefing = objectMapper.readValue(
          briefingJson, new TypeReference<Map<String, Object>>() {});
      HotNewsThemeBriefingSupport.assertReadyToCommit(briefing);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * 是否保留库内课程问卷（避免冲掉用户修改）。
   * <p>{@code ensureSeedTheme} 写入的 step=1 + 仅 naming stub 不算「已有问卷」。
   *
   * @param briefingJson 主题 briefing_json
   * @return 应保留则为 true
   */
  static boolean shouldPreserveCourseBriefing(String briefingJson) {
    return shouldPreserveCourseBriefing(new ObjectMapper(), briefingJson);
  }

  /**
   * @param mapper       JSON 解析器
   * @param briefingJson 主题 briefing_json
   * @return 应保留则为 true
   */
  static boolean shouldPreserveCourseBriefing(ObjectMapper mapper, String briefingJson) {
    if (mapper == null || StrUtil.isBlank(briefingJson)) {
      return false;
    }
    try {
      Map<String, Object> briefing = mapper.readValue(
          briefingJson, new TypeReference<Map<String, Object>>() {});
      if (briefing == null || briefing.isEmpty()) {
        return false;
      }
      int step = 0;
      Object rawStep = briefing.get("step");
      if (rawStep instanceof Number number) {
        step = number.intValue();
      } else if (rawStep != null) {
        try {
          step = Integer.parseInt(String.valueOf(rawStep).trim());
        } catch (NumberFormatException ignored) {
          step = 0;
        }
      }
      Object goals = briefing.get("goals");
      boolean hasGoals = goals instanceof Map<?, ?> goalsMap && !goalsMap.isEmpty();
      // stub：仅命名起步，仍应用完整种子
      return step > 1 || hasGoals;
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * 统计主题下热点渠道行数。
   *
   * @param themeId 主题 id
   * @return 行数
   */
  private int countHotNewsChannels(String themeId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(1) FROM source_channels WHERE theme_id=? AND deleted_at IS NULL",
        Integer.class,
        themeId);
    return count == null ? 0 : count;
  }

  /**
   * 种子热点默认主题：briefing → 与手动确认相同的编排（启用 + defaults + 初始渠道）。
   *
   * @param node hotNewsTheme 节点
   */
  private void seedHotNewsTheme(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    String themeId = text(node, "id");
    if (StrUtil.isBlank(themeId)) {
      themeId = "theme-ai-frontier";
    }
    ContentThemeView theme = content.ensureSeedTheme(
        themeId,
        ContentModeIds.HOT_NEWS_CODE,
        text(node, "code"),
        text(node, "name"),
        text(node, "description"),
        1);
    int channelRows = countHotNewsChannels(theme.getId());
    // 已确认且已有渠道：跳过；已确认但 0 渠道：回退为 draft 再走完整确认路径
    if ((ContentThemeStatus.ENABLED.matches(theme.getStatus())
            || ContentThemeStatus.DISABLED.matches(theme.getStatus()))
        && channelRows > 0) {
      log.info(
          "跳过已确认热点种子主题 id={} status={} channels={}",
          theme.getId(),
          theme.getStatus(),
          channelRows);
      return;
    }
    if (ContentThemeStatus.ENABLED.matches(theme.getStatus())
        || ContentThemeStatus.DISABLED.matches(theme.getStatus())) {
      reopenEmptySeedTheme(theme.getId());
      log.info("空渠道种子主题已回退为 draft 以补全初始渠道 id={}", theme.getId());
    }
    // 非 force：库内问卷已可 commit 则保留；否则写入种子（含已确认主要意图）
    if (force || !hotNewsBriefingCommitReady(theme.getBriefingJson())) {
      Map<String, Object> briefing = objectMapper.convertValue(
          node.path("briefing"), new TypeReference<Map<String, Object>>() {});
      content.patchThemeBriefing(
          theme.getId(),
          ThemeBriefingPatchRequest.builder()
              .name(text(node, "name"))
              .description(text(node, "description"))
              .code(text(node, "code"))
              .briefing(FlexiblePayload.of(briefing))
              .build());
    } else {
      log.info("保留已有热点主题问卷，不覆盖种子 briefing id={}", theme.getId());
    }
    // 与 ThemeController hot-news:commit 同一编排，确保渠道/查询词与手动创建一致
    HotNewsThemeCommitView committed = hotNewsThemeCommit.commit(theme.getId());
    log.info(
        "已确认热点种子主题 id={} channels={} types={}",
        theme.getId(),
        committed.getChannelCount(),
        committed.getSeededChannelTypes());
  }

  /**
   * @return true 表示本次写入了 提示词包
   */
  private boolean upsertPromptPack(JsonNode promptPackNode) {
    String name = text(promptPackNode, "name");
    String scene = text(promptPackNode, "scene");
    PromptPackView existing = findLivePromptPack(name, scene);
    if (existing != null && !force) {
      // 命中旧名别名时只改名，不覆盖用户已改的指令正文
      if (!name.equals(existing.getName())) {
        promptPacks.updatePromptPackById(
            existing.getId(),
            PromptPackSaveRequest.builder().name(name).build());
        log.info("已将 提示词包 旧名「{}」改为「{}」 scene={}", existing.getName(), name, scene);
        return true;
      }
      log.info("跳过已有 提示词包 name={} scene={}", name, scene);
      return false;
    }
    PromptPackSaveRequest body = PromptPackSaveRequest.builder()
        .id(existing == null ? null : existing.getId())
        .name(name)
        .description(text(promptPackNode, "description"))
        .scope(text(promptPackNode, "scope"))
        .promptPackType(text(promptPackNode, "promptPackType"))
        .locked(promptPackNode.path("locked").asBoolean(false))
        .scene(scene)
        .systemInstructions(text(promptPackNode, "systemInstructions"))
        .taskInstructions("")
        .priority(promptPackNode.path("priority").asInt(100))
        .enabled(Boolean.TRUE)
        .nodeBindings(null)
        .build();
    promptPacks.upsertPromptPack(body);
    log.info("{} 提示词包 name={} scene={}", existing == null ? "创建" : "覆盖", name, scene);
    return true;
  }

  /**
   * @return true 表示本次发布了工作流
   */
  private boolean publishWorkflow(JsonNode workflowNode) throws Exception {
    String key = text(workflowNode, "key");
    String name = text(workflowNode, "name");
    String scene = text(workflowNode, "scene");
    workflows.ensureBuiltin(key, name, scene);
    WorkflowSummaryView definition = findWorkflowByKey(key);
    if (definition == null) {
      throw new IllegalStateException("工作流定义未创建 key=" + key);
    }
    if (StrUtil.isNotBlank(definition.getPublishedVersionId()) && !force) {
      log.info("跳过已发布工作流 key={}", key);
      return false;
    }
    ObjectNode definitionJson = (ObjectNode) workflowNode.path("definition").deepCopy();
    bindPromptPackIds(definitionJson, scene);
    workflows.saveDraft(definition.getId(), WorkflowDraftSaveRequest.builder()
        .definitionJson(objectMapper.writeValueAsString(definitionJson))
        .build());
    workflows.publish(definition.getId(), WorkflowPublishRequest.builder()
        .releaseNotes(text(workflowNode, "releaseNotes"))
        .build());
    log.info("已发布工作流 key={}", key);
    return true;
  }

  /**
   * 将节点 promptPackBindings 的 name 解析为库内 promptPackId。
   *
   * @param definition     工作流图
   * @param workflowScene  工作流 scene
   */
  private void bindPromptPackIds(ObjectNode definition, String workflowScene) {
    List<PromptPackView> all = promptPacks.listPromptPacks();
    for (JsonNode node : definition.path("nodes")) {
      if (!(node instanceof ObjectNode)) {
        continue;
      }
      for (JsonNode binding : node.path("promptPackBindings")) {
        if (!(binding instanceof ObjectNode bindingNode)) {
          continue;
        }
        String promptPackName = bindingNode.path("name").asText("");
        String promptPackId = PromptPackBindingIds.resolveForWorkflowScene(all, promptPackName, workflowScene);
        if (StrUtil.isBlank(promptPackId)) {
          throw new IllegalStateException("工作流 " + workflowScene + " 找不到 提示词包 name=" + promptPackName);
        }
        bindingNode.put("promptPackId", promptPackId);
      }
    }
  }

  /**
   * 按稳定 key / 名称导入排版主题。
   *
   * @return true 表示本次写入
   */
  private boolean upsertLayoutTheme(JsonNode themeNode) throws Exception {
    String key = text(themeNode, "key");
    if (StrUtil.isBlank(key)) {
      key = BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA;
    }
    String name = text(themeNode, "name");
    String type = StrUtil.blankToDefault(text(themeNode, "type"), "custom");
    String status = StrUtil.blankToDefault(text(themeNode, "status"), "enabled");
    String dbStatus = "disabled".equalsIgnoreCase(status) ? "disabled" : "active";
    JsonNode configNode = themeNode.get("config");
    Map<String, Object> config = configNode == null || configNode.isNull()
        ? Map.of()
        : objectMapper.convertValue(configNode, new TypeReference<Map<String, Object>>() {});
    String configJson = objectMapper.writeValueAsString(config);
    List<Map<String, Object>> existing = jdbc.queryForList(
        "SELECT id, current_version_id FROM layout_themes WHERE theme_key=? OR name=? LIMIT 1",
        key, name);
    if (!existing.isEmpty() && !force) {
      log.info("跳过已有排版主题 key={} name={}", key, name);
      return false;
    }
    if (existing.isEmpty()) {
      String themeId = IdsUtil.newId();
      String versionId = IdsUtil.newId();
      jdbc.update(
          "INSERT INTO layout_themes (id, theme_key, name, theme_type, status, current_version_id, created_at, updated_at) "
              + "VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
          themeId, key, name, type, dbStatus, versionId);
      jdbc.update(
          "INSERT INTO layout_theme_versions (id, layout_theme_id, version_no, schema_version, config_json, content_hash, created_at) "
              + "VALUES (?,?,1,?,?,?,UTC_TIMESTAMP(3))",
          versionId, themeId, themeNode.path("schemaVersion").asInt(1), configJson, sha256(configJson));
      log.info("创建排版主题 key={} name={}", key, name);
      return true;
    }
    String themeId = String.valueOf(existing.get(0).get("id"));
    Integer maxVersion = jdbc.queryForObject(
        "SELECT COALESCE(MAX(version_no),0) FROM layout_theme_versions WHERE layout_theme_id=?",
        Integer.class,
        themeId);
    int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
    String versionId = IdsUtil.newId();
    jdbc.update(
        "INSERT INTO layout_theme_versions (id, layout_theme_id, version_no, schema_version, config_json, content_hash, created_at) "
            + "VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3))",
        versionId, themeId, nextVersion, themeNode.path("schemaVersion").asInt(1), configJson, sha256(configJson));
    jdbc.update(
        "UPDATE layout_themes SET theme_key=?, name=?, theme_type=?, status=?, current_version_id=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
        key, name, type, dbStatus, versionId, themeId);
    log.info("覆盖排版主题 key={} name={} version={}", key, name, nextVersion);
    return true;
  }

  /**
   * 查找未删除且同名同场景的 提示词包；规则 提示词包 额外认旧名「工作台规则理解」。
   *
   * @param name  名称
   * @param scene 场景
   * @return 视图；无则 null
   */
  private PromptPackView findLivePromptPack(String name, String scene) {
    PromptPackView exact = findLivePromptPackExact(name, scene);
    if (exact != null) {
      return exact;
    }
    // 种子已是新名时，仍要落到旧名行以便改名而非重复创建
    if (CoursePromptPackNames.CHENGWENFANG_RULES.equals(name)) {
      return findLivePromptPackExact(CoursePromptPackNames.LEGACY_RULES, scene);
    }
    return null;
  }

  /**
   * 按精确名称与场景查找未删除提示词包。
   *
   * @param name  名称
   * @param scene 场景
   * @return 视图；无则 null
   */
  private PromptPackView findLivePromptPackExact(String name, String scene) {
    for (PromptPackView promptPack : promptPacks.listPromptPacks()) {
      if (promptPack == null) {
        continue;
      }
      if (name.equals(promptPack.getName()) && scene.equals(StrUtil.nullToEmpty(promptPack.getScene()))) {
        return promptPack;
      }
    }
    return null;
  }

  /**
   * 按业务 key 查找工作流摘要。
   *
   * @param key 工作流 key
   * @return 摘要；无则 null
   */
  private WorkflowSummaryView findWorkflowByKey(String key) {
    for (WorkflowSummaryView summary : workflows.listWorkflows()) {
      if (summary != null && key.equals(summary.getKey())) {
        return summary;
      }
    }
    return null;
  }

  private static String sha256(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      return "";
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private static Path resolveCatalog(String configured) {
    Path direct = Path.of(configured);
    if (direct.isAbsolute() && Files.isRegularFile(direct)) {
      return direct;
    }
    Path cursor = Path.of("").toAbsolutePath();
    for (int depth = 0; depth < 4 && cursor != null; depth++) {
      Path candidate = cursor.resolve(configured).normalize();
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      cursor = cursor.getParent();
    }
    throw new IllegalStateException("找不到种子文件：" + configured);
  }
}
