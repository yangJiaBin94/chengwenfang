package com.workbench.skill.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workbench.skill.application.WorkflowFacadeImpl;
import com.workbench.skill.facade.constant.CourseWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷新首次部署目录中的工作流图定义。
 * <p>Skill 正文只维护在 bootstrap 目录 JSON 中，本工具不从 Java 生成提示词。
 * <p>用法：{@code java ... ExportBuiltinCatalog <catalog.json>}
 * <p>读取已有 catalog 的 skills，按 skill 名称重建三条内置工作流定义后写回。
 */
public final class ExportBuiltinCatalog {
  private ExportBuiltinCatalog() {}

  /**
   * 写出目录文件。
   *
   * @param args [0] 已有 catalog 路径（读写同一文件）
   * @throws Exception 读写失败
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      throw new IllegalArgumentException("需要 catalog.json 路径");
    }
    refreshWorkflows(Path.of(args[0]));
  }

  /**
   * 保留 catalog 中 skills（及其他非 workflow 字段），仅刷新三条内置工作流图。
   *
   * @param catalogPath 目录文件
   * @throws Exception 读写失败
   */
  public static void refreshWorkflows(Path catalogPath) throws Exception {
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    JsonNode root = mapper.readTree(catalogPath.toFile());
    List<Map<String, Object>> skills = new ArrayList<>();
    JsonNode skillNodes = root.path("skills");
    if (skillNodes.isArray()) {
      for (JsonNode skillNode : skillNodes) {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = mapper.convertValue(skillNode, Map.class);
        skills.add(row);
      }
    }
    Map<String, String> identity = new LinkedHashMap<>();
    for (Map<String, Object> skill : skills) {
      Object name = skill.get("name");
      if (name != null) {
        identity.put(String.valueOf(name), String.valueOf(name));
      }
    }
    List<Map<String, Object>> workflows = List.of(
        workflow(
            CourseWorkflowKeys.DEFINITION_KEY,
            CourseWorkflowKeys.DISPLAY_NAME,
            CourseWorkflowKeys.SCENE,
            CourseWorkflowKeys.BUILTIN_RELEASE_NOTES,
            WorkflowFacadeImpl.exportCourseContentDefinition(identity)),
        workflow(
            HotNewsWorkflowKeys.DEFINITION_KEY,
            HotNewsWorkflowKeys.DISPLAY_NAME,
            HotNewsWorkflowKeys.SCENE,
            HotNewsWorkflowKeys.BUILTIN_RELEASE_NOTES,
            WorkflowFacadeImpl.exportHotNewsArticleDefinition(identity)),
        workflow(
            HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY,
            HotNewsRadarCollectWorkflowKeys.DISPLAY_NAME,
            HotNewsRadarCollectWorkflowKeys.SCENE,
            HotNewsRadarCollectWorkflowKeys.BUILTIN_RELEASE_NOTES,
            WorkflowFacadeImpl.exportRadarCollectDefinition(identity)));
    Map<String, Object> catalog = new LinkedHashMap<>();
    catalog.put("skills", skills);
    catalog.put("workflows", workflows);
    if (root.has("layoutThemes")) {
      catalog.put("layoutThemes", mapper.convertValue(root.get("layoutThemes"), Object.class));
    }
    Files.createDirectories(catalogPath.getParent() == null ? Path.of(".") : catalogPath.getParent());
    mapper.writeValue(catalogPath.toFile(), catalog);
  }

  /**
   * 组装工作流种子行。
   *
   * @param key 定义 key
   * @param name 显示名
   * @param scene 场景
   * @param releaseNotes 发布说明
   * @param definition 图定义
   * @return 一行种子
   */
  private static Map<String, Object> workflow(
      String key, String name, String scene, String releaseNotes, Map<String, Object> definition) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("key", key);
    row.put("name", name);
    row.put("scene", scene);
    row.put("releaseNotes", releaseNotes);
    row.put("definition", definition);
    return row;
  }
}
