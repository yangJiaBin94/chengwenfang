package com.chengwenfang.publishing.application;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.publishing.facade.constant.LayoutThemeKeys;
import com.chengwenfang.publishing.facade.dto.request.LayoutThemeSaveRequest;
import com.chengwenfang.publishing.facade.dto.view.LayoutThemeView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 排版主题 CRUD 与配置加载：主题行 / 版本表读写，DB 不可用时回落内存 extras。
 */
@Service
public class LayoutThemeService {
  /** JDBC 访问主题/版本表。 */
  private final JdbcTemplate jdbc;
  /** JSON 序列化（config）。 */
  private final ObjectMapper objectMapper;
  /** DB 不可用时的内存兜底（description/config/status）。 */
  private final ConcurrentHashMap<String, Map<String, Object>> extras = new ConcurrentHashMap<>();

  /**
   * 注入 JDBC 与 ObjectMapper。
   *
   * @param jdbc         主题表 JDBC
   * @param objectMapper JSON 读写
   */
  public LayoutThemeService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  /**
   * 列出可用排版主题；库空时种子默认主题后再查。
   *
   * @return 主题视图列表（可能含内存兜底）
   */
  public List<LayoutThemeView> listThemes() {
    List<LayoutThemeView> rows = queryThemes();
    // 空库时写入系统默认主题，再查一次
    if (rows.isEmpty()) {
      seedDefault();
      rows = queryThemes();
    }
    return rows;
  }

  /**
   * 创建排版主题（主题行 + 首个版本）；DB 失败时回落内存。
   *
   * @param body 创建请求；null 按空请求；name/description/config 可缺省
   * @return 新建主题视图
   */
  public LayoutThemeView createTheme(LayoutThemeSaveRequest body) {
    LayoutThemeSaveRequest input = body == null ? new LayoutThemeSaveRequest() : body;
    String id = IdsUtil.newId();
    String versionId = IdsUtil.newId();
    String name = input.getName() == null ? "Untitled theme" : input.getName();
    String description = input.getDescription() == null ? "" : input.getDescription();
    Object config = input.getConfig() == null ? Map.of() : input.getConfig();
    String key = "theme-" + id.replace("-", "").substring(0, 8);
    // 先写入内存 extras，保证 DB 失败时仍可读
    putExtra(id, description, config, 1);
    try {
      jdbc.update(
          "INSERT INTO layout_themes (id, theme_key, name, theme_type, status, current_version_id, created_at, updated_at) "
              + "VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
          id, key, name, LayoutThemeKeys.TYPE_CUSTOM, LayoutThemeKeys.DB_ACTIVE, versionId);
      String configJson = writeJson(config);
      jdbc.update(
          "INSERT INTO layout_theme_versions (id, layout_theme_id, version_no, schema_version, config_json, content_hash, created_at) "
              + "VALUES (?,?,1,1,?,?,UTC_TIMESTAMP(3))",
          versionId, id, configJson, sha256(configJson));
    } catch (Exception ignored) {
      // DB 不可用：直接返回内存视图
      LayoutThemeView mem = themeView(
          id, key, name, description, LayoutThemeKeys.TYPE_CUSTOM, LayoutThemeKeys.FE_ENABLED,
          versionId, 1, config, LocalDateTime.now().toString());
      extras.put(id, themeToExtra(mem));
      return mem;
    }
    return getTheme(id);
  }

  /**
   * 更新主题配置并追加新版本。
   *
   * @param id   主题主键
   * @param body 更新请求；null 字段保留原值
   * @return 更新后的主题视图
   * @throws BusinessException 主题不存在时 404
   */
  public LayoutThemeView updateTheme(String id, LayoutThemeSaveRequest body) {
    LayoutThemeView existing = getTheme(id);
    LayoutThemeSaveRequest input = body == null ? new LayoutThemeSaveRequest() : body;
    // 未传字段沿用原值
    String name = input.getName() != null ? input.getName() : existing.getName();
    String description = input.getDescription() != null
        ? input.getDescription()
        : (existing.getDescription() == null ? "" : existing.getDescription());
    Object config = input.getConfig() != null ? input.getConfig() : existing.getConfig();
    int nextVersion = existing.getVersionNo() + 1;
    String versionId = IdsUtil.newId();
    putExtra(id, description, config, nextVersion);
    try {
      jdbc.update(
          "UPDATE layout_themes SET name=?, current_version_id=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
          name, versionId, id);
      jdbc.update(
          "INSERT INTO layout_theme_versions (id, layout_theme_id, version_no, schema_version, config_json, content_hash, created_at) "
              + "VALUES (?,?,?,1,?,?,UTC_TIMESTAMP(3))",
          versionId, id, nextVersion, writeJson(config), sha256(writeJson(config)));
    } catch (Exception ignored) {
      // DB 失败：拼内存视图返回
      LayoutThemeView mem = LayoutThemeView.builder()
          .id(existing.getId())
          .key(existing.getKey())
          .name(name)
          .description(description)
          .type(existing.getType())
          .status(existing.getStatus())
          .currentVersionId(versionId)
          .versionNo(nextVersion)
          .config(config instanceof Map<?, ?> ? castMap(config) : Map.of())
          .updatedAt(LocalDateTime.now().toString())
          .build();
      extras.put(id, themeToExtra(mem));
      return mem;
    }
    return getTheme(id);
  }

  /**
   * 删除主题（系统主题不可删）。
   *
   * @param id 主题主键
   * @throws BusinessException 系统主题只读，或主题不存在
   */
  public void deleteTheme(String id) {
    LayoutThemeView theme = getTheme(id);
    if (LayoutThemeKeys.TYPE_SYSTEM.equals(theme.getType())) {
      throw new BusinessException(ErrorCodes.THEME_READONLY, "系统主题不可删除", 400);
    }
    try {
      jdbc.update("DELETE FROM layout_theme_versions WHERE layout_theme_id=?", id);
      jdbc.update("DELETE FROM layout_themes WHERE id=?", id);
    } catch (Exception ignored) {
      // ignore
    }
    extras.remove(id);
  }

  /**
   * 克隆主题：以源主题 name/description/config 创建副本。
   *
   * @param id 源主题主键
   * @return 新建副本主题视图
   * @throws BusinessException 源主题不存在时 404
   */
  public LayoutThemeView cloneTheme(String id) {
    LayoutThemeView src = getTheme(id);
    return createTheme(LayoutThemeSaveRequest.builder()
        .name(String.valueOf(src.getName() == null ? "Theme" : src.getName()) + " (copy)")
        .description(src.getDescription() == null ? "" : src.getDescription())
        .config(src.getConfig() == null ? Map.of() : src.getConfig())
        .build());
  }

  /**
   * 设置主题启用/停用状态。
   *
   * @param id     主题主键
   * @param status 目标状态；{@code disabled} 外均视为 enabled
   * @return 更新后的主题视图（status 为前端 enabled/disabled）
   * @throws BusinessException 主题不存在时 404
   */
  public LayoutThemeView setStatus(String id, String status) {
    getTheme(id);
    // 归一化：仅 disabled 为停用，其余为启用
    String normalized = LayoutThemeKeys.DISABLED.equalsIgnoreCase(status)
        ? LayoutThemeKeys.DISABLED
        : LayoutThemeKeys.FE_ENABLED;
    String dbStatus = LayoutThemeKeys.FE_ENABLED.equals(normalized)
        ? LayoutThemeKeys.DB_ACTIVE
        : LayoutThemeKeys.DISABLED;
    try {
      jdbc.update("UPDATE layout_themes SET status=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?", dbStatus, id);
    } catch (Exception ignored) {
      // memory-only path
    }
    Map<String, Object> extra = extras.computeIfAbsent(id, key -> new LinkedHashMap<>());
    extra.put("status", normalized);
    LayoutThemeView view = getTheme(id);
    view.setStatus(normalized);
    return view;
  }

  /**
   * 按版本 ID 或主题 key 加载主题 config_json。
   *
   * @param themeVersionId 主题版本主键，或兼容传入的 theme key/id
   * @return 可变 config Map；找不到则空 Map
   */
  public Map<String, Object> loadThemeConfig(String themeVersionId) {
    if (StrUtil.isBlank(themeVersionId)) {
      return Map.of();
    }
    // 优先按 theme_version.id 查
    try {
      List<Map<String, Object>> rows = jdbc.query(
          "SELECT v.config_json, t.theme_key AS theme_key "
              + "FROM layout_theme_versions v "
              + "LEFT JOIN layout_themes t ON t.id = v.layout_theme_id "
              + "WHERE v.id = ? LIMIT 1",
          (rs, rowNum) -> {
            Map<String, Object> cfg = readMap(rs.getString("config_json"));
            String key = rs.getString("theme_key");
            if (StrUtil.isNotBlank(key)) {
              cfg.put("_themeKey", key);
            }
            return cfg;
          },
          themeVersionId);
      if (!rows.isEmpty()) {
        return rows.get(0);
      }
    } catch (Exception ignored) {
      // fall through
    }
    // 兼容旧调用方仍传 theme key / theme id
    try {
      List<Map<String, Object>> rows = jdbc.query(
          "SELECT v.config_json, t.theme_key AS theme_key "
              + "FROM layout_themes t "
              + "JOIN layout_theme_versions v ON v.id = t.current_version_id "
              + "WHERE t.theme_key = ? OR t.id = ? LIMIT 1",
          (rs, rowNum) -> {
            Map<String, Object> cfg = readMap(rs.getString("config_json"));
            String key = rs.getString("theme_key");
            if (StrUtil.isNotBlank(key)) {
              cfg.put("_themeKey", key);
            }
            return cfg;
          },
          themeVersionId,
          themeVersionId);
      if (!rows.isEmpty()) {
        return rows.get(0);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return Map.of();
  }

  /**
   * 按 ID 取主题；不存在则查 extras，仍无则 404。
   *
   * @param id 主题主键
   * @return 主题视图
   * @throws BusinessException 不存在时 404
   */
  public LayoutThemeView getTheme(String id) {
    // 按主键点查，避免 listThemes 全表扫描
    LayoutThemeView row = queryThemeById(id);
    if (row != null) {
      return row;
    }
    Map<String, Object> mem = extras.get(id);
    if (mem == null || mem.get("id") == null) {
      throw new BusinessException(ErrorCodes.THEME_NOT_FOUND, "发布主题不存在：" + id, 404);
    }
    return themeFromExtra(mem);
  }

  /** 库空时写入系统默认主题（idempotent）。 */
  private void seedDefault() {
    String id = LayoutThemeKeys.DEFAULT_ID;
    if (extras.containsKey(id)) {
      return;
    }
    String versionId = IdsUtil.newId();
    putExtra(id, "System default publishing theme", Map.of(), 1);
    try {
      Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM layout_themes", Integer.class);
      if (count != null && count > 0) {
        return;
      }
      jdbc.update(
          "INSERT INTO layout_themes (id, theme_key, name, theme_type, status, current_version_id, created_at, updated_at) "
              + "VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
          id, LayoutThemeKeys.DEFAULT_KEY, "Default", LayoutThemeKeys.TYPE_SYSTEM,
          LayoutThemeKeys.DB_ACTIVE, versionId);
      jdbc.update(
          "INSERT INTO layout_theme_versions (id, layout_theme_id, version_no, schema_version, config_json, content_hash, created_at) "
              + "VALUES (?,?,1,1,'{}',?,UTC_TIMESTAMP(3))",
          versionId, id, sha256("{}"));
    } catch (Exception ignored) {
      extras.put(id, themeToExtra(themeView(
          id, LayoutThemeKeys.DEFAULT_KEY, "Default", "System default publishing theme",
          LayoutThemeKeys.TYPE_SYSTEM, LayoutThemeKeys.FE_ENABLED,
          versionId, 1, Map.of(), LocalDateTime.now().toString())));
    }
  }

  /**
   * 查询全部主题；DB 失败时回落 extras 内存列表。
   *
   * @return 主题视图列表
   */
  private List<LayoutThemeView> queryThemes() {
    try {
      List<LayoutThemeView> rows = jdbc.query(
          "SELECT t.id, t.theme_key, t.name, t.theme_type, t.status, t.current_version_id, t.updated_at, "
              + "(SELECT MAX(v.version_no) FROM layout_theme_versions v WHERE v.layout_theme_id = t.id) AS version_no, "
              + "(SELECT v2.config_json FROM layout_theme_versions v2 WHERE v2.id = t.current_version_id) AS config_json "
              + "FROM layout_themes t ORDER BY t.updated_at DESC",
          (rs, rowNum) -> mapThemeRow(rs, extras.getOrDefault(rs.getString("id"), Map.of())));
      if (!rows.isEmpty()) {
        return rows;
      }
    } catch (Exception ignored) {
      // fall through
    }
    // 纯内存兜底
    List<LayoutThemeView> mem = new java.util.ArrayList<>();
    for (Map<String, Object> extraMap : extras.values()) {
      if (extraMap.get("id") != null) {
        mem.add(themeFromExtra(extraMap));
      }
    }
    return mem;
  }

  /**
   * 按主键点查主题；不存在或 DB 失败返回 null。
   *
   * @param id 主题主键
   * @return 主题视图或 null
   */
  private LayoutThemeView queryThemeById(String id) {
    if (StrUtil.isBlank(id)) {
      return null;
    }
    try {
      List<LayoutThemeView> rows = jdbc.query(
          "SELECT t.id, t.theme_key, t.name, t.theme_type, t.status, t.current_version_id, t.updated_at, "
              + "(SELECT MAX(v.version_no) FROM layout_theme_versions v WHERE v.layout_theme_id = t.id) AS version_no, "
              + "(SELECT v2.config_json FROM layout_theme_versions v2 WHERE v2.id = t.current_version_id) AS config_json "
              + "FROM layout_themes t WHERE t.id = ? LIMIT 1",
          (rs, rowNum) -> mapThemeRow(rs, extras.getOrDefault(id, Map.of())),
          id);
      if (!rows.isEmpty()) {
        return rows.get(0);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return null;
  }

  /**
   * ResultSet 行映射为主题视图（合并 extras 描述/配置/状态）。
   *
   * @param rs    JDBC 行
   * @param extra 内存 extras
   * @return 主题视图
   * @throws java.sql.SQLException 列读取失败
   */
  private LayoutThemeView mapThemeRow(java.sql.ResultSet rs, Map<String, Object> extra)
      throws java.sql.SQLException {
    String id = rs.getString("id");
    Map<String, Object> config = readMap(rs.getString("config_json"));
    if (config.isEmpty() && extra.get("config") instanceof Map<?, ?>) {
      config = castMap(extra.get("config"));
    }
    int versionNo = rs.getObject("version_no") == null ? 0 : rs.getInt("version_no");
    if (extra.get("versionNo") instanceof Number number) {
      versionNo = Math.max(versionNo, number.intValue());
    }
    String status = rs.getString("status");
    String feStatus = LayoutThemeKeys.DB_ACTIVE.equalsIgnoreCase(status)
            || LayoutThemeKeys.FE_ENABLED.equalsIgnoreCase(status)
        ? LayoutThemeKeys.FE_ENABLED
        : LayoutThemeKeys.DISABLED;
    if (extra.get("status") != null) {
      feStatus = String.valueOf(extra.get("status"));
    }
    return themeView(
        id,
        rs.getString("theme_key"),
        rs.getString("name"),
        String.valueOf(extra.getOrDefault("description", "")),
        rs.getString("theme_type"),
        feStatus,
        rs.getString("current_version_id") == null ? "" : rs.getString("current_version_id"),
        versionNo,
        config,
        rs.getObject("updated_at") == null ? null : String.valueOf(rs.getObject("updated_at")));
  }

  /**
   * 写入/覆盖 extras 中的 description/config/versionNo。
   *
   * @param id          主题主键
   * @param description 描述
   * @param config      配置对象
   * @param versionNo   版本号
   */
  private void putExtra(String id, String description, Object config, int versionNo) {
    Map<String, Object> extra = new LinkedHashMap<>();
    extra.put("description", description == null ? "" : description);
    extra.put("config", config instanceof Map<?, ?> ? config : Map.of());
    extra.put("versionNo", versionNo);
    extras.put(id, extra);
  }

  /**
   * 组装主题视图。
   *
   * @param id               主键
   * @param key              主题 key
   * @param name             名称
   * @param description      描述
   * @param type             system/custom
   * @param status           enabled/disabled
   * @param currentVersionId 当前版本 ID
   * @param versionNo        版本号
   * @param config           配置
   * @param updatedAt        更新时间字符串
   * @return 主题视图
   */
  private static LayoutThemeView themeView(
      String id,
      String key,
      String name,
      String description,
      String type,
      String status,
      String currentVersionId,
      int versionNo,
      Object config,
      String updatedAt) {
    return LayoutThemeView.builder()
        .id(id)
        .key(key)
        .name(name)
        .description(description == null ? "" : description)
        .type(type == null ? LayoutThemeKeys.TYPE_CUSTOM : type)
        .status(status)
        .currentVersionId(currentVersionId == null ? "" : currentVersionId)
        .versionNo(versionNo)
        .config(config instanceof Map<?, ?> ? castMap(config) : Map.of())
        .updatedAt(updatedAt)
        .build();
  }

  /**
   * 将 Object 安全转为可变 Map。
   *
   * @param config 预期为 Map
   * @return LinkedHashMap 副本
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object config) {
    return new LinkedHashMap<>((Map<String, Object>) config);
  }

  /**
   * 主题视图转 extras Map（完整字段，供内存兜底）。
   *
   * @param view 主题视图
   * @return extras 条目
   */
  private static Map<String, Object> themeToExtra(LayoutThemeView view) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", view.getId());
    payload.put("key", view.getKey());
    payload.put("name", view.getName());
    payload.put("description", view.getDescription());
    payload.put("type", view.getType());
    payload.put("status", view.getStatus());
    payload.put("currentVersionId", view.getCurrentVersionId());
    payload.put("versionNo", view.getVersionNo());
    payload.put("config", view.getConfig() == null ? Map.of() : view.getConfig());
    payload.put("updatedAt", view.getUpdatedAt());
    return payload;
  }

  /**
   * extras Map 还原为主题视图。
   *
   * @param extra extras 条目
   * @return 主题视图
   */
  private static LayoutThemeView themeFromExtra(Map<String, Object> extra) {
    Object config = extra.getOrDefault("config", Map.of());
    return LayoutThemeView.builder()
        .id(String.valueOf(extra.get("id")))
        .key(extra.get("key") == null ? null : String.valueOf(extra.get("key")))
        .name(extra.get("name") == null ? null : String.valueOf(extra.get("name")))
        .description(String.valueOf(extra.getOrDefault("description", "")))
        .type(String.valueOf(extra.getOrDefault("type", LayoutThemeKeys.TYPE_CUSTOM)))
        .status(extra.get("status") == null
            ? LayoutThemeKeys.FE_ENABLED
            : String.valueOf(extra.get("status")))
        .currentVersionId(String.valueOf(extra.getOrDefault("currentVersionId", "")))
        .versionNo(extra.get("versionNo") instanceof Number number ? number.intValue() : 0)
        .config(config instanceof Map<?, ?> ? castMap(config) : Map.of())
        .updatedAt(extra.get("updatedAt") == null ? null : String.valueOf(extra.get("updatedAt")))
        .build();
  }

  /**
   * 解析 JSON 对象为 Map；失败返回空 LinkedHashMap。
   *
   * @param json JSON 文本
   * @return 可变 Map
   */
  private Map<String, Object> readMap(String json) {
    if (StrUtil.isBlank(json)) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
      return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
    } catch (Exception ignored) {
      return new LinkedHashMap<>();
    }
  }

  /**
   * 对象序列化为 JSON；失败返回 {@code []}。
   *
   * @param value 任意对象
   * @return JSON 文本
   */
  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  /**
   * 计算字符串 SHA-256 十六进制摘要。
   *
   * @param value 原文
   * @return hex 摘要；算法不可用时返回空串
   */
  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }
}
