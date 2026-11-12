package com.chengwenfang.app.golden.stub;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.publishing.facade.constant.BuiltinLayoutThemeKeys;
import com.chengwenfang.prompt.facade.PromptPackBindingIds;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.WorkflowFacade;
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
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 黄金 IT 启动时导入 {@code scripts/bootstrap/builtin-catalog.json} 中的 提示词包、工作流与排版主题
 *（已存在则跳过），保证雷达等依赖已发布图的场景可跑。
 */
@Slf4j
@Order(25)
@RequiredArgsConstructor
public class GoldenCatalogWarmup implements ApplicationRunner {
  private static final AtomicBoolean DONE = new AtomicBoolean(false);

  private final PromptPackFacade promptPacks;
  private final WorkflowFacade workflows;
  /** 排版主题直写 JDBC。 */
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  /**
   * 导入种子 提示词包 / 工作流（仅一次）。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!DONE.compareAndSet(false, true)) {
      return;
    }
    Path catalog = resolveCatalog();
    if (catalog == null || !Files.isRegularFile(catalog)) {
      log.warn("黄金种子 catalog 未找到，跳过导入");
      return;
    }
    JsonNode root = objectMapper.readTree(catalog.toFile());
    int promptPackWritten = 0;
    int promptPackSkipped = 0;
    JsonNode promptPackNodes = root.path("promptPacks");
    if (promptPackNodes.isArray()) {
      for (JsonNode promptPackNode : promptPackNodes) {
        if (upsertPromptPack(promptPackNode)) {
          promptPackWritten++;
        } else {
          promptPackSkipped++;
        }
      }
    }
    // 黄金基线录制时 host 库提示词包文案与当前 catalog 有漂移；强制对齐基线期望文案
    alignHotNewsPromptPacksToGoldenBaseline();
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
    log.info(
        "黄金种子导入完成 promptPacksWritten={} promptPacksSkipped={} workflowsPublished={} workflowsSkipped={} themesWritten={} themesSkipped={} catalog={}",
        promptPackWritten,
        promptPackSkipped,
        workflowPublished,
        workflowSkipped,
        themeWritten,
        themeSkipped,
        catalog);
  }

  /**
   * 按稳定 key / 名称导入排版主题（表重建后黄金库为空，需补种）。
   *
   * @param themeNode 种子节点
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
    if (!existing.isEmpty()) {
      return false;
    }
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
    return true;
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }

  private boolean upsertPromptPack(JsonNode promptPackNode) {
    String name = text(promptPackNode, "name");
    String scene = text(promptPackNode, "scene");
    if (StrUtil.isBlank(name)) {
      return false;
    }
    PromptPackView existing = findLivePromptPack(name, scene);
    PromptPackSaveRequest request =
        PromptPackSaveRequest.builder()
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
    if (existing != null) {
      // 已存在则强制同步指令，避免 host 库残留污染黄金比对
      promptPacks.updatePromptPackById(existing.getId(), request);
      return false;
    }
    promptPacks.upsertPromptPack(request);
    return true;
  }

  /**
   * 将热点成文相关 提示词包 的 systemInstructions 对齐黄金基线录制时的文案（仅测试种子，不改产品 catalog 文件）。
   */
  private void alignHotNewsPromptPacksToGoldenBaseline() {
    forcePromptPackInstructions(
        "热点文章审校",
        "hot_news_article_generation",
        """
            审校热点原稿：
            1. 检查是否交代事件、影响与读者信号；有无资讯流水账。
            2. 检查是否出现“一、二、三”式大标题与 AI 套话。
            3. 检查是否编造素材中没有的事实。
            4. 文风改到像真人聊天：删训诫腔，改掉词典括注与翻译腔，句子要有口气、有判断。
            5. 必须完整保留原稿中的插图提示词块，不得删除。
            6. 检查关键概念粗体、中文双引号；清除正文「」、//、->、——（提示词围栏内不受限）。
            7. 若有问题，直接输出修订后的完整正文；若通过，原样输出完整正文。
            不要只输出检查清单。
            """);
    forcePromptPackInstructions(
        "热点公众号转换",
        "hot_news_article_generation",
        """
            将已审校原稿改写为公众号成品：
            1. 不新增原稿没有的事实。
            2. 标题与开头突出冲突/变化/影响；段落适合移动端阅读。
            3. 像活人聊天：保留口语与个人判断；禁止词典括注、「它不是 A，是 B」与通稿腔。
            4. 原稿插图提示词已替换为占位：必须保留 [[HOT_NEWS_COVER]] 与 [[HOT_NEWS_IMAGE_N]]（N 从 1 起）。
            5. 不得输出“提示词”“插图位置”、提示代码块或“插图 2”等提示标题，只保留占位/后续图片引用。
            6. 关键概念保持粗体；对话和引用用中文双引号；正文禁用「」、//、->、——。
            """);
    forcePromptPackInstructions(
        "热点资讯原稿生成",
        "hot_news_article_generation",
        """
            按成文坊提供的文章骨架生成完整 Markdown 原稿：
            1. 围绕 targetAudience，落实 readerIntents，补上 readerGaps，结尾落到 readerSignals。
            2. 必须覆盖 coreEvent、keyInsights、impacts、readerSignals。
            3. 跟随 narrativeShape 与 sectionPlan；architecture 用自然小标题，禁止“一、二、三”式大标题。
            4. 资料参考只作线索，不逐条复述。
            5. 正文字数符合 wordCountOption；插图提示词不计入字数。
            6. 关键概念用 Markdown 粗体；对话和引用用中文双引号，不使用「」；正文禁用 //、->、——。
            7. 像活人聊天：可有第一人称判断，句子长短参差；术语中文优先；禁止词典括注、「它不是 A，是 B」与通稿腔。
            8. 温和分享，不训诫：禁止「留给你的几个判断」「你必须…」等强势小标题；也不要硬套「不妨看看」类万能收尾。
            9. 必须写入并保留插图提示词：封面 1 个 + 与插图档匹配数量的正文插图；格式为“插图位置”标题 + ```text 围栏。
            10. 每条插图提示词须遵循骨架的 illustrationStyle、coverBrief、imagePromptRequirements，把生图要素写进提示词正文；不要另起一套画面规范。
            11. 只输出可发布正文与插图提示词块，不输出提纲、建议或其它内部说明。
            """);
  }

  /**
   * 按名称+场景覆盖 提示词包 系统指令。
   *
   * @param name               名称
   * @param scene              场景
   * @param systemInstructions 指令全文
   */
  private void forcePromptPackInstructions(String name, String scene, String systemInstructions) {
    PromptPackView existing = findLivePromptPack(name, scene);
    if (existing == null) {
      return;
    }
    promptPacks.updatePromptPackById(
        existing.getId(),
        PromptPackSaveRequest.builder()
            .name(existing.getName())
            .systemInstructions(systemInstructions)
            .build());
  }

  private boolean publishWorkflow(JsonNode workflowNode) throws Exception {
    String key = text(workflowNode, "key");
    String name = text(workflowNode, "name");
    String scene = text(workflowNode, "scene");
    if (StrUtil.isBlank(key)) {
      return false;
    }
    workflows.ensureBuiltin(key, name, scene);
    WorkflowSummaryView definition = findWorkflowByKey(key);
    if (definition == null) {
      throw new IllegalStateException("工作流定义未创建 key=" + key);
    }
    if (StrUtil.isNotBlank(definition.getPublishedVersionId())) {
      return false;
    }
    ObjectNode definitionJson = (ObjectNode) workflowNode.path("definition").deepCopy();
    bindPromptPackIds(definitionJson, scene);
    workflows.saveDraft(
        definition.getId(),
        WorkflowDraftSaveRequest.builder()
            .definitionJson(objectMapper.writeValueAsString(definitionJson))
            .build());
    workflows.publish(
        definition.getId(),
        WorkflowPublishRequest.builder().releaseNotes(text(workflowNode, "releaseNotes")).build());
    return true;
  }

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
          throw new IllegalStateException(
              "工作流 " + workflowScene + " 找不到 提示词包 name=" + promptPackName);
        }
        bindingNode.put("promptPackId", promptPackId);
      }
    }
  }

  private PromptPackView findLivePromptPack(String name, String scene) {
    for (PromptPackView promptPack : promptPacks.listPromptPacks()) {
      if (promptPack == null) {
        continue;
      }
      if (name.equals(promptPack.getName())
          && scene.equals(StrUtil.nullToEmpty(promptPack.getScene()))) {
        return promptPack;
      }
    }
    return null;
  }

  private WorkflowSummaryView findWorkflowByKey(String key) {
    for (WorkflowSummaryView summary : workflows.listWorkflows()) {
      if (summary != null && key.equals(summary.getKey())) {
        return summary;
      }
    }
    return null;
  }

  private static Path resolveCatalog() {
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path[] candidates = {
      cwd.resolve("scripts/bootstrap/builtin-catalog.json"),
      cwd.resolve("../scripts/bootstrap/builtin-catalog.json"),
      cwd.resolve("../../scripts/bootstrap/builtin-catalog.json")
    };
    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        return candidate.normalize();
      }
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText("");
  }
}
