package com.chengwenfang.app.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.enums.ContentThemeStatus;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeCommitOrchestrator;
import com.chengwenfang.publishing.facade.constant.BuiltinLayoutThemeKeys;
import com.chengwenfang.skill.facade.SkillBindingIds;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import com.chengwenfang.skill.facade.dto.request.SkillSaveRequest;
import com.chengwenfang.skill.facade.dto.request.WorkflowDraftSaveRequest;
import com.chengwenfang.skill.facade.dto.request.WorkflowPublishRequest;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import com.chengwenfang.skill.facade.dto.view.WorkflowSummaryView;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 仅在 {@code bootstrap-seed} 配置下，把仓库种子导入数据库后退出。
 * <p>已有同名同场景 Skill、已发布工作流默认跳过，避免覆盖库内修改。
 */
@Slf4j
@Component
@Order(20)
@Profile("bootstrap-seed")
@RequiredArgsConstructor
public class BuiltinSeedImporter implements ApplicationRunner {
  /** Skill 门面。 */
  private final SkillFacade skills;
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
  /** Spring 上下文（导入后退出）。 */
  private final ConfigurableApplicationContext context;

  /** 种子文件路径，相对路径会向上查找仓库根。 */
  @Value("${chengwenfang.bootstrap.catalog:scripts/bootstrap/builtin-catalog.json}")
  private String catalogPath;

  /** 默认课程/热点主题种子（问卷 + 课表结构，无正文）。 */
  @Value("${chengwenfang.bootstrap.default-themes:scripts/bootstrap/default-themes.json}")
  private String defaultThemesPath;

  /** 为 true 时用种子覆盖已有内置 Skill 与工作流发布版本。 */
  @Value("${chengwenfang.bootstrap.force:false}")
  private boolean force;

  /**
   * 导入种子并结束进程。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    int code = 0;
    try {
      importCatalog(resolveCatalog(catalogPath));
    } catch (Exception exception) {
      log.error("基础配置导入失败：{}", exception.getMessage(), exception);
      code = 1;
    }
    int exitCode = code;
    int springCode = SpringApplication.exit(context, () -> exitCode);
    System.exit(springCode);
  }

  /**
   * 读取目录并按条导入。
   *
   * @param catalogFile 种子文件
   * @throws Exception 解析或写入失败
   */
  void importCatalog(Path catalogFile) throws Exception {
    JsonNode root = objectMapper.readTree(catalogFile.toFile());
    // 先迁旧 Skill 名，避免后续按新名 upsert 再插一行
    if (skills.migrateLegacyRulesSkillName()) {
      log.info(
          "Skill 名称已同步：{} → {}",
          CourseSkillNames.LEGACY_RULES,
          CourseSkillNames.CHENGWENFANG_RULES);
    }
    int skillCreated = 0;
    int skillSkipped = 0;
    JsonNode skillNodes = root.path("skills");
    if (skillNodes.isArray()) {
      for (JsonNode skillNode : skillNodes) {
        if (upsertSkill(skillNode)) {
          skillCreated++;
        } else {
          skillSkipped++;
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
    importDefaultThemes(resolveCatalog(defaultThemesPath));
    log.info(
        "基础配置导入完成 skillsWritten={} skillsSkipped={} workflowsPublished={} workflowsSkipped={} themesWritten={} themesSkipped={} force={}",
        skillCreated, skillSkipped, workflowPublished, workflowSkipped, themeWritten, themeSkipped, force);
  }

  /**
   * 导入默认课程/热点主题：问卷 + 课表结构（课壳），不生成正文。
   *
   * @param themesFile default-themes.json
   * @throws Exception 解析或落库失败
   */
  private void importDefaultThemes(Path themesFile) throws Exception {
    JsonNode root = objectMapper.readTree(themesFile.toFile());
    seedCourseTheme(root.path("courseTheme"));
    seedHotNewsTheme(root.path("hotNewsTheme"));
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
   * 统计主题下热点渠道行数。
   *
   * @param themeId 主题 id
   * @return 行数
   */
  private int countHotNewsChannels(String themeId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(1) FROM source_channels WHERE theme_id=?", Integer.class, themeId);
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
    // 与 ThemeController hot-news:commit 同一编排，确保渠道/查询词与手动创建一致
    HotNewsThemeCommitView committed = hotNewsThemeCommit.commit(theme.getId());
    log.info(
        "已确认热点种子主题 id={} channels={} types={}",
        theme.getId(),
        committed.getChannelCount(),
        committed.getSeededChannelTypes());
  }

  /**
   * @return true 表示本次写入了 Skill
   */
  private boolean upsertSkill(JsonNode skillNode) {
    String name = text(skillNode, "name");
    String scene = text(skillNode, "scene");
    SkillView existing = findLiveSkill(name, scene);
    if (existing != null && !force) {
      // 命中旧名别名时只改名，不覆盖用户已改的指令正文
      if (!name.equals(existing.getName())) {
        skills.updateSkillById(
            existing.getId(),
            SkillSaveRequest.builder().name(name).build());
        log.info("已将 Skill 旧名「{}」改为「{}」 scene={}", existing.getName(), name, scene);
        return true;
      }
      log.info("跳过已有 Skill name={} scene={}", name, scene);
      return false;
    }
    SkillSaveRequest body = SkillSaveRequest.builder()
        .id(existing == null ? null : existing.getId())
        .name(name)
        .description(text(skillNode, "description"))
        .scope(text(skillNode, "scope"))
        .skillType(text(skillNode, "skillType"))
        .locked(skillNode.path("locked").asBoolean(false))
        .scene(scene)
        .systemInstructions(text(skillNode, "systemInstructions"))
        .taskInstructions("")
        .priority(skillNode.path("priority").asInt(100))
        .enabled(Boolean.TRUE)
        .nodeBindings(null)
        .build();
    skills.upsertSkill(body);
    log.info("{} Skill name={} scene={}", existing == null ? "创建" : "覆盖", name, scene);
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
    bindSkillIds(definitionJson, scene);
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
   * 将节点 skillBindings 的 name 解析为库内 skillId。
   *
   * @param definition     工作流图
   * @param workflowScene  工作流 scene
   */
  private void bindSkillIds(ObjectNode definition, String workflowScene) {
    List<SkillView> all = skills.listSkills();
    for (JsonNode node : definition.path("nodes")) {
      if (!(node instanceof ObjectNode)) {
        continue;
      }
      for (JsonNode binding : node.path("skillBindings")) {
        if (!(binding instanceof ObjectNode bindingNode)) {
          continue;
        }
        String skillName = bindingNode.path("name").asText("");
        String skillId = SkillBindingIds.resolveForWorkflowScene(all, skillName, workflowScene);
        if (StrUtil.isBlank(skillId)) {
          throw new IllegalStateException("工作流 " + workflowScene + " 找不到 Skill name=" + skillName);
        }
        bindingNode.put("skillId", skillId);
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
   * 查找未删除且同名同场景的 Skill；规则 Skill 额外认旧名「工作台规则理解」。
   *
   * @param name  名称
   * @param scene 场景
   * @return 视图；无则 null
   */
  private SkillView findLiveSkill(String name, String scene) {
    SkillView exact = findLiveSkillExact(name, scene);
    if (exact != null) {
      return exact;
    }
    // 种子已是新名时，仍要落到旧名行以便改名而非重复创建
    if (CourseSkillNames.CHENGWENFANG_RULES.equals(name)) {
      return findLiveSkillExact(CourseSkillNames.LEGACY_RULES, scene);
    }
    return null;
  }

  /**
   * 按精确名称与场景查找未删除 Skill。
   *
   * @param name  名称
   * @param scene 场景
   * @return 视图；无则 null
   */
  private SkillView findLiveSkillExact(String name, String scene) {
    for (SkillView skill : skills.listSkills()) {
      if (skill == null) {
        continue;
      }
      if (name.equals(skill.getName()) && scene.equals(StrUtil.nullToEmpty(skill.getScene()))) {
        return skill;
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
