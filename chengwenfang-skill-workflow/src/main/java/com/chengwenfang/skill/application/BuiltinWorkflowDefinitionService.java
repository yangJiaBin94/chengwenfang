package com.chengwenfang.skill.application;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.skill.application.graphseed.CourseWorkflowGraphSeed;
import com.chengwenfang.skill.application.graphseed.HotNewsWorkflowGraphSeed;
import com.chengwenfang.skill.application.graphseed.RadarCollectWorkflowGraphSeed;
import com.chengwenfang.skill.domain.entity.WorkflowDefinitionEntity;
import com.chengwenfang.skill.domain.entity.WorkflowDraftEntity;
import com.chengwenfang.skill.domain.entity.WorkflowVersionEntity;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.constant.WorkflowGraphMapKeys;
import com.chengwenfang.skill.facade.constant.WorkflowOwnerKeys;
import com.chengwenfang.skill.facade.dto.request.WorkflowPublishRequest;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.WorkflowDefinitionStatus;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowDraftMapper;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内置工作流种子：ensure* 写库/覆盖发布；图骨架导出供目录与健康检查。
 * <p>规范图构建委托 {@link CourseWorkflowGraphSeed} /
 * {@link HotNewsWorkflowGraphSeed} / {@link RadarCollectWorkflowGraphSeed}。
 * <p>Skill 名 → id 解析委托 {@link BuiltinWorkflowSeeder}；本类负责定义落库与发布编排。
 * <p>由 {@link WorkflowFacadeImpl} 薄委托，不对外暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltinWorkflowDefinitionService {

  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 草稿表。 */
  private final WorkflowDraftMapper draftMapper;
  /** 已发布版本表。 */
  private final WorkflowVersionMapper versionMapper;
  /** 草稿发布（覆盖规范图时调用）。 */
  private final WorkflowDefinitionService definitions;
  /** 共享工具。 */
  private final WorkflowSupport support;

  /**
   * 按名称解析课程相关 Skill id（委托 {@link BuiltinWorkflowSeeder}）。
   *
   * @param all 全部未删除 Skill
   * @return Skill 名 → id
   */
  public static Map<String, String> resolveCourseSkillIds(List<SkillView> all) {
    return BuiltinWorkflowSeeder.resolveCourseSkillIds(all);
  }

  /**
   * 为课程工作流挑选 Skill id（委托 {@link BuiltinWorkflowSeeder}）。
   *
   * @param all  全部未删除 Skill
   * @param name Skill 名称
   * @return 选中的 id；没有合适行则 null
   */
  public static String pickCourseSkillId(List<SkillView> all, String name) {
    return BuiltinWorkflowSeeder.pickCourseSkillId(all, name);
  }

  /**
   * 确保内置工作流定义存在（启动种子）：已存在则补草稿，否则新建定义+空草稿。
   *
   * @param key   定义唯一 key
   * @param name  显示名称
   * @param scene 场景码
   */
  @Transactional
  public void ensureBuiltin(String key, String name, String scene) {
    WorkflowDefinitionEntity existing = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
        .eq(WorkflowDefinitionEntity::getWorkflowKey, key)
        .last("LIMIT 1"));
    if (existing != null) {
      // 已有定义但缺草稿时补一行，避免编辑器打不开
      if (support.findDraft(existing.getId()) == null) {
        LocalDateTime now = LocalDateTime.now();
        draftMapper.insert(support.newDraftEntity(existing.getId(), WorkflowSupport.emptyDefinition(existing), 1, null, now));
      }
      if (StrUtil.isNotBlank(name) && !name.equals(existing.getName())) {
        existing.setName(name);
        existing.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(existing);
      }
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    WorkflowDefinitionEntity def = new WorkflowDefinitionEntity();
    def.setId(IdsUtil.newId());
    def.setWorkflowKey(key);
    def.setName(name);
    def.setDescription("");
    def.setScene(scene);
    def.setStatus(WorkflowDefinitionStatus.ACTIVE.getValue());
    def.setOwner(WorkflowOwnerKeys.SYSTEM);
    def.setPublishedVersionId(null);
    def.setCreatedAt(now);
    def.setUpdatedAt(now);
    definitionMapper.insert(def);
    draftMapper.insert(support.newDraftEntity(def.getId(), WorkflowSupport.emptyDefinition(def), 1, null, now));
  }

  /**
   * 确保课程成文内置图与当前规范一致；必要时覆盖草稿并发布。
   *
   * @param skillIdsByName Skill 名 → id
   */
  @Transactional
  public void ensureCourseContentBuiltin(Map<String, String> skillIdsByName) {
    String key = CourseWorkflowKeys.DEFINITION_KEY;
    String name = CourseWorkflowKeys.DISPLAY_NAME;
    String scene = CourseWorkflowKeys.SCENE;
    ensureBuiltin(key, name, scene);
    WorkflowDefinitionEntity def = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
        .eq(WorkflowDefinitionEntity::getWorkflowKey, key)
        .last("LIMIT 1"));
    if (def == null) {
      return;
    }
    if (StrUtil.isNotBlank(def.getPublishedVersionId())) {
      WorkflowVersionEntity published = versionMapper.selectById(def.getPublishedVersionId());
      if (published != null && published.getSnapshotJson() != null
          && isFullCourseGraph(published.getSnapshotJson())) {
        log.info("课程内置工作流已是当前主流程节点集，跳过覆盖 id={}", def.getId());
        return;
      }
    }
    Map<String, Object> definition = courseFullDefinition(name, scene, skillIdsByName);
    overwriteDraftAndPublish(def, name, definition, CourseWorkflowKeys.BUILTIN_RELEASE_NOTES);
    log.info("课程内置工作流已发布规范主流程图 id={} nodes={}",
        def.getId(), CourseWorkflowNodeType.builtinTypeKeys().size());
  }

  /**
   * 判断已发布快照是否已是当前规范课程主流程。
   * <p>除 builtin typeKey 全集外，还要求公众号进度别名与审校/公众号图级单次尝试（业务 repair 在节点内）。
   *
   * @param snapshotJson 版本快照 JSON
   * @return 已对齐当前规范则为 true
   */
  private boolean isFullCourseGraph(String snapshotJson) {
    Map<String, Object> snapshot = support.readDefinition(snapshotJson, new WorkflowDefinitionEntity());
    Object nodesRaw = snapshot.get(WorkflowGraphMapKeys.NODES);
    if (!(nodesRaw instanceof List<?> nodes) || nodes.isEmpty()) {
      return false;
    }
    Set<String> typeKeys = new HashSet<>();
    boolean wechatProgressOk = false;
    boolean reviewSingleAttempt = false;
    boolean wechatSingleAttempt = false;
    for (Object nodeObj : nodes) {
      if (!(nodeObj instanceof Map<?, ?> map)) {
        continue;
      }
      String typeKey = readTypeKey(map);
      if (StrUtil.isBlank(typeKey)) {
        continue;
      }
      typeKeys.add(typeKey);
      Object configRaw = map.get(WorkflowGraphMapKeys.CONFIG);
      Map<?, ?> config = configRaw instanceof Map<?, ?> configMap ? configMap : Map.of();
      if (CourseWorkflowNodeType.WECHAT_CONVERSION.matches(typeKey)) {
        Object progress = config.get(WorkflowGraphMapKeys.PROGRESS_STEP_KEY);
        wechatProgressOk = CourseWorkflowNodeType.WECHAT_CONVERSION.getProgressStepKey()
            .equals(String.valueOf(progress));
        wechatSingleAttempt = intConfigValue(config.get(WorkflowGraphMapKeys.MAX_ATTEMPTS), 1) == 1;
      }
      if (CourseWorkflowNodeType.REVIEW_DRAFT.matches(typeKey)) {
        reviewSingleAttempt = intConfigValue(config.get(WorkflowGraphMapKeys.MAX_ATTEMPTS), 1) == 1;
      }
    }
    return CourseWorkflowNodeType.isFullCourseGraph(typeKeys)
        && wechatProgressOk
        && reviewSingleAttempt
        && wechatSingleAttempt;
  }

  /**
   * 读取节点配置中的整数，非法时回退默认值。
   *
   * @param raw          原始值
   * @param defaultValue 默认值
   * @return 解析结果
   */
  private static int intConfigValue(Object raw, int defaultValue) {
    Integer parsed = Convert.toInt(raw, null);
    return parsed == null ? defaultValue : parsed;
  }

  /**
   * 导出课程规范主流程图；调用方可用技能名占位 skillId，导入时再换成库内主键。
   *
   * @param skillIdsByName Skill 名 → id（或占位名）
   * @return 定义 Map
   */
  public static Map<String, Object> exportCourseContentDefinition(Map<String, String> skillIdsByName) {
    return courseFullDefinition(
        CourseWorkflowKeys.DISPLAY_NAME, CourseWorkflowKeys.SCENE, skillIdsByName);
  }

  /**
   * 导出热点成文规范图。
   *
   * @param skillIdsByName Skill 名 → id（或占位名）
   * @return 定义 Map
   */
  public static Map<String, Object> exportHotNewsArticleDefinition(Map<String, String> skillIdsByName) {
    return hotNewsFullDefinition(
        HotNewsWorkflowKeys.DISPLAY_NAME, HotNewsWorkflowKeys.SCENE, skillIdsByName);
  }

  /**
   * 导出雷达采集规范图。
   *
   * @param skillIdsByName Skill 名 → id（或占位名）
   * @return 定义 Map
   */
  public static Map<String, Object> exportRadarCollectDefinition(Map<String, String> skillIdsByName) {
    return radarCollectFullDefinition(
        HotNewsRadarCollectWorkflowKeys.DISPLAY_NAME,
        HotNewsRadarCollectWorkflowKeys.SCENE,
        skillIdsByName);
  }

  /**
   * 构建课程成文 14 节点线性定义（委托 {@link CourseWorkflowGraphSeed}）。
   *
   * @param name           展示名
   * @param scene          场景码
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> courseFullDefinition(
      String name, String scene, Map<String, String> skillIdsByName) {
    return CourseWorkflowGraphSeed.toDefinitionMap(name, scene, skillIdsByName);
  }

  /**
   * 确保热点成文内置图与当前规范一致；必要时覆盖草稿并发布。
   *
   * @param skillIdsByName Skill 名 → id
   */
  @Transactional
  public void ensureHotNewsArticleBuiltin(Map<String, String> skillIdsByName) {
    String key = HotNewsWorkflowKeys.DEFINITION_KEY;
    String name = HotNewsWorkflowKeys.DISPLAY_NAME;
    String scene = HotNewsWorkflowKeys.SCENE;
    ensureBuiltin(key, name, scene);
    WorkflowDefinitionEntity def = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
        .eq(WorkflowDefinitionEntity::getWorkflowKey, key)
        .last("LIMIT 1"));
    if (def == null) {
      return;
    }
    if (StrUtil.isNotBlank(def.getPublishedVersionId())) {
      WorkflowVersionEntity published = versionMapper.selectById(def.getPublishedVersionId());
      if (published != null && published.getSnapshotJson() != null
          && isFullHotNewsGraph(published.getSnapshotJson())) {
        log.info("热点内置工作流已是当前主流程节点集，跳过覆盖 id={}", def.getId());
        return;
      }
    }
    // 旧版图（精简 6 / 旧 14）或不完整图：覆盖为当前骨架优先主流程
    Map<String, Object> definition = hotNewsFullDefinition(name, scene, skillIdsByName);
    overwriteDraftAndPublish(def, name, definition, HotNewsWorkflowKeys.BUILTIN_RELEASE_NOTES);
    log.info("热点内置工作流已发布骨架优先主流程图 id={} nodes={}",
        def.getId(), HotNewsWorkflowNodeType.builtinTypeKeys().size());
  }

  /**
   * 确保雷达采集内置图与当前规范一致；必要时覆盖草稿并发布。
   *
   * @param skillIdsByName Skill 名 → id
   */
  @Transactional
  public void ensureHotNewsRadarCollectBuiltin(Map<String, String> skillIdsByName) {
    String key = HotNewsRadarCollectWorkflowKeys.DEFINITION_KEY;
    String name = HotNewsRadarCollectWorkflowKeys.DISPLAY_NAME;
    String scene = HotNewsRadarCollectWorkflowKeys.SCENE;
    ensureBuiltin(key, name, scene);
    WorkflowDefinitionEntity def = definitionMapper.selectOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
        .eq(WorkflowDefinitionEntity::getWorkflowKey, key)
        .last("LIMIT 1"));
    if (def == null) {
      return;
    }
    if (StrUtil.isNotBlank(def.getPublishedVersionId())) {
      WorkflowVersionEntity published = versionMapper.selectById(def.getPublishedVersionId());
      if (published != null && published.getSnapshotJson() != null
          && isFullRadarCollectGraph(published.getSnapshotJson())) {
        log.info("雷达采集内置工作流已是当前主流程节点集，跳过覆盖 id={}", def.getId());
        return;
      }
    }
    Map<String, Object> definition = radarCollectFullDefinition(name, scene, skillIdsByName);
    overwriteDraftAndPublish(def, name, definition, HotNewsRadarCollectWorkflowKeys.BUILTIN_RELEASE_NOTES);
    log.info("雷达采集内置工作流已发布主流程图 id={} nodes={}",
        def.getId(), HotNewsRadarCollectNodeType.builtinTypeKeys().size());
  }

  /**
   * 判断已发布快照是否已包含当前雷达采集主流程节点集。
   *
   * @param snapshotJson 版本快照 JSON
   * @return 已覆盖全部 builtin typeKey（可多不可少）则为 true
   */
  private boolean isFullRadarCollectGraph(String snapshotJson) {
    return collectTypeKeys(snapshotJson).containsAll(HotNewsRadarCollectNodeType.builtinTypeKeys());
  }

  /**
   * 构建雷达采集工作流定义（委托 {@link RadarCollectWorkflowGraphSeed}）。
   *
   * @param name           展示名
   * @param scene          场景码
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> radarCollectFullDefinition(
      String name, String scene, Map<String, String> skillIdsByName) {
    return RadarCollectWorkflowGraphSeed.toDefinitionMap(name, scene, skillIdsByName);
  }

  /**
   * 判断已发布快照是否已包含当前内置热点主流程节点集。
   * <p>允许额外插入 custom_ai 等自定义节点，避免启动 seed 覆盖用户编排。
   *
   * @param snapshotJson 版本快照 JSON
   * @return 已覆盖全部 builtin typeKey（可多不可少）则为 true
   */
  private boolean isFullHotNewsGraph(String snapshotJson) {
    return collectTypeKeys(snapshotJson).containsAll(HotNewsWorkflowNodeType.builtinTypeKeys());
  }

  /**
   * 构建骨架优先热点成文定义（委托 {@link HotNewsWorkflowGraphSeed}）。
   *
   * @param name           展示名
   * @param scene          场景码
   * @param skillIdsByName Skill 名 → id
   * @return 定义 Map
   */
  public static Map<String, Object> hotNewsFullDefinition(
      String name, String scene, Map<String, String> skillIdsByName) {
    return HotNewsWorkflowGraphSeed.toDefinitionMap(name, scene, skillIdsByName);
  }

  /**
   * 覆盖草稿并发布新版本。
   *
   * @param def          定义实体
   * @param name         展示名
   * @param definition   规范图 Map
   * @param releaseNotes 发布说明
   */
  private void overwriteDraftAndPublish(
      WorkflowDefinitionEntity def,
      String name,
      Map<String, Object> definition,
      String releaseNotes) {
    LocalDateTime now = LocalDateTime.now();
    WorkflowDraftEntity draft = support.findDraft(def.getId());
    if (draft == null) {
      draftMapper.insert(support.newDraftEntity(def.getId(), definition, 1, null, now));
    } else {
      draft.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(definition));
      draft.setDefinitionJson(support.writeJson(definition));
      draft.setValidationJson(support.writeJson(support.validateDefinition(definition)));
      draft.setUpdatedAt(now);
      draftMapper.updateById(draft);
    }
    def.setName(name);
    def.setUpdatedAt(now);
    definitionMapper.updateById(def);
    // 版本号留空，由 publish 按已有版本数自增，避免重复发布 version=1 冲突
    definitions.publish(def.getId(), WorkflowPublishRequest.builder()
        .releaseNotes(releaseNotes)
        .build());
  }

  /**
   * 从快照 JSON 收集节点 typeKey。
   *
   * @param snapshotJson 版本快照
   * @return typeKey 集合
   */
  private Set<String> collectTypeKeys(String snapshotJson) {
    Map<String, Object> snapshot = support.readDefinition(snapshotJson, new WorkflowDefinitionEntity());
    Object nodesRaw = snapshot.get(WorkflowGraphMapKeys.NODES);
    Set<String> typeKeys = new HashSet<>();
    if (!(nodesRaw instanceof List<?> nodes) || nodes.isEmpty()) {
      return typeKeys;
    }
    for (Object nodeObj : nodes) {
      if (nodeObj instanceof Map<?, ?> map) {
        String typeKey = readTypeKey(map);
        if (StrUtil.isNotBlank(typeKey)) {
          typeKeys.add(typeKey);
        }
      }
    }
    return typeKeys;
  }

  /**
   * 读取节点 typeKey（兼容旧 type 字段）。
   *
   * @param map 节点 Map
   * @return typeKey；缺失则为空白
   */
  private static String readTypeKey(Map<?, ?> map) {
    Object typeKeyRaw = map.get(WorkflowGraphMapKeys.TYPE_KEY);
    if (typeKeyRaw == null) {
      typeKeyRaw = map.get(WorkflowGraphMapKeys.TYPE);
    }
    return typeKeyRaw == null ? "" : String.valueOf(typeKeyRaw);
  }
}
