package com.workbench.app.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.app.support.WorkbenchIT;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 新表结构规范（查 information_schema）。
 *
 * <p>P4.11 起对当前库全部业务表做规范检查（排除 Flyway 历史表）。
 */
class SchemaConventionTest extends WorkbenchIT {

  /** 不纳入业务规范检查的系统表。 */
  static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history");

  /**
   * 02 第一节允许的 JSON 列名白名单（配置文档 + 调试快照）。
   */
  static final Set<String> ALLOWED_JSON_COLUMNS = Set.of(
      "input_json",
      "output_json",
      "payload_json",
      "snapshot_json",
      "validation_json",
      "definition_json",
      "config_json",
      // workflow_node_runs 沿用原列名的调试快照
      "input_summary",
      "output_summary",
      "config_snapshot");

  /** 禁止作为列名的裸保留字（须改为 {@code <x>_key} / {@code <x>_type}）。 */
  static final Set<String> FORBIDDEN_COLUMN_NAMES = Set.of("key", "type");

  /**
   * 对当前库全部业务表执行注释、JSON 白名单、外键类型、列名规范检查。
   */
  @Test
  void scoped_tables_follow_v2_schema_conventions() {
    String catalog = currentCatalog();
    Set<String> tables = listBusinessTables(catalog);
    assertThat(tables).as("business tables in %s", catalog).isNotEmpty();
    List<String> violations = new ArrayList<>();
    for (String tableName : tables) {
      checkTableComment(catalog, tableName, violations);
      checkColumns(catalog, tableName, violations);
      checkForeignKeyColumnTypes(catalog, tableName, violations);
    }
    assertThat(violations)
        .as("schema convention violations:\n%s", String.join("\n", violations))
        .isEmpty();
  }

  /**
   * 读取当前库名。
   *
   * @return 数据库名
   */
  private String currentCatalog() {
    String catalog = jdbc.queryForObject("SELECT DATABASE()", String.class);
    assertThat(catalog).as("DATABASE()").isNotBlank();
    return catalog;
  }

  /**
   * 列出当前库业务表（排除系统表）。
   *
   * @param catalog 库名
   * @return 表名集合（小写）
   */
  private Set<String> listBusinessTables(String catalog) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        """
            SELECT TABLE_NAME AS table_name
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ?
              AND TABLE_TYPE = 'BASE TABLE'
            ORDER BY TABLE_NAME
            """,
        catalog);
    Set<String> tables = new LinkedHashSet<>();
    for (Map<String, Object> row : rows) {
      String tableName = stringValue(row.get("table_name"));
      if (tableName == null) {
        continue;
      }
      String lower = tableName.toLowerCase(Locale.ROOT);
      if (EXCLUDED_TABLES.contains(lower)) {
        continue;
      }
      tables.add(lower);
    }
    return tables;
  }

  /**
   * 表 COMMENT 非空。
   *
   * @param catalog    库名
   * @param tableName  表名
   * @param violations 违规收集
   */
  private void checkTableComment(String catalog, String tableName, List<String> violations) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        """
            SELECT TABLE_COMMENT AS table_comment
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            """,
        catalog,
        tableName);
    if (rows.isEmpty()) {
      violations.add(tableName + ": table missing in information_schema");
      return;
    }
    String comment = stringValue(rows.get(0).get("table_comment"));
    if (comment == null || comment.isBlank()) {
      violations.add(tableName + ": table COMMENT is blank");
    }
  }

  /**
   * 列注释、JSON 白名单、禁止列名。
   *
   * @param catalog    库名
   * @param tableName  表名
   * @param violations 违规收集
   */
  private void checkColumns(String catalog, String tableName, List<String> violations) {
    List<Map<String, Object>> columns = jdbc.queryForList(
        """
            SELECT COLUMN_NAME AS column_name,
                   DATA_TYPE AS data_type,
                   COLUMN_COMMENT AS column_comment
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """,
        catalog,
        tableName);
    if (columns.isEmpty()) {
      violations.add(tableName + ": no columns in information_schema");
      return;
    }
    for (Map<String, Object> column : columns) {
      String columnName = stringValue(column.get("column_name"));
      String dataType = stringValue(column.get("data_type"));
      String comment = stringValue(column.get("column_comment"));
      if (columnName == null) {
        continue;
      }
      String lowerName = columnName.toLowerCase(Locale.ROOT);
      if (comment == null || comment.isBlank()) {
        violations.add(tableName + "." + columnName + ": column COMMENT is blank");
      }
      if (FORBIDDEN_COLUMN_NAMES.contains(lowerName)) {
        violations.add(tableName + "." + columnName + ": forbidden column name (use <x>_key / <x>_type)");
      }
      if (dataType != null && "json".equalsIgnoreCase(dataType)
          && !ALLOWED_JSON_COLUMNS.contains(lowerName)) {
        violations.add(tableName + "." + columnName + ": JSON column not in whitelist "
            + ALLOWED_JSON_COLUMNS);
      }
    }
  }

  /**
   * 已声明外键的 {@code *_id} 列与被引用列类型一致。
   *
   * @param catalog    库名
   * @param tableName  表名
   * @param violations 违规收集
   */
  private void checkForeignKeyColumnTypes(String catalog, String tableName, List<String> violations) {
    List<Map<String, Object>> fks = jdbc.queryForList(
        """
            SELECT
              kcu.COLUMN_NAME AS column_name,
              kcu.REFERENCED_TABLE_NAME AS referenced_table,
              kcu.REFERENCED_COLUMN_NAME AS referenced_column,
              child.COLUMN_TYPE AS child_type,
              parent.COLUMN_TYPE AS parent_type
            FROM information_schema.KEY_COLUMN_USAGE kcu
            JOIN information_schema.COLUMNS child
              ON child.TABLE_SCHEMA = kcu.TABLE_SCHEMA
             AND child.TABLE_NAME = kcu.TABLE_NAME
             AND child.COLUMN_NAME = kcu.COLUMN_NAME
            JOIN information_schema.COLUMNS parent
              ON parent.TABLE_SCHEMA = kcu.TABLE_SCHEMA
             AND parent.TABLE_NAME = kcu.REFERENCED_TABLE_NAME
             AND parent.COLUMN_NAME = kcu.REFERENCED_COLUMN_NAME
            WHERE kcu.TABLE_SCHEMA = ?
              AND kcu.TABLE_NAME = ?
              AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
            """,
        catalog,
        tableName);
    Set<String> seen = new LinkedHashSet<>();
    for (Map<String, Object> fk : fks) {
      String columnName = stringValue(fk.get("column_name"));
      if (columnName == null || !columnName.toLowerCase(Locale.ROOT).endsWith("_id")) {
        continue;
      }
      String childType = stringValue(fk.get("child_type"));
      String parentType = stringValue(fk.get("parent_type"));
      String key = columnName + "->" + fk.get("referenced_table") + "." + fk.get("referenced_column");
      if (!seen.add(key)) {
        continue;
      }
      if (childType == null || parentType == null
          || !childType.equalsIgnoreCase(parentType)) {
        violations.add(tableName + "." + columnName + ": FK type mismatch child="
            + childType + " parent=" + parentType + " (" + key + ")");
      }
    }
  }

  /**
   * 将 JDBC 单元格转为字符串。
   *
   * @param value 任意值
   * @return 字符串；null 保持 null
   */
  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
