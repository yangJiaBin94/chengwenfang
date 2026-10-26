package com.workbench.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import com.workbench.common.JacksonJsonSupport;
import com.workbench.skill.domain.entity.WorkflowAlertEntity;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import com.workbench.skill.domain.entity.WorkflowNodeRunEntity;
import com.workbench.skill.domain.entity.WorkflowRunEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.facade.constant.CourseWorkflowKeys;
import com.workbench.skill.facade.constant.WorkflowOwnerKeys;
import com.workbench.skill.facade.constant.WorkflowValidationMapKeys;
import com.workbench.skill.facade.dto.view.WorkflowAlertView;
import com.workbench.skill.facade.dto.view.WorkflowDefinitionDataView;
import com.workbench.skill.facade.dto.view.WorkflowDraftView;
import com.workbench.skill.facade.dto.view.WorkflowNodeRunView;
import com.workbench.skill.facade.dto.view.WorkflowRunView;
import com.workbench.skill.facade.dto.view.WorkflowSummaryView;
import com.workbench.skill.facade.dto.view.WorkflowValidationIssueView;
import com.workbench.skill.facade.dto.view.WorkflowValidationView;
import com.workbench.skill.facade.dto.view.WorkflowVersionView;
import com.workbench.skill.facade.enums.WorkflowDefinitionStatus;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowDraftMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 工作流视图组装：实体转 View、JSON 读写与校验条目规范化。
 */
@Component
public class WorkflowViewAssembler {

  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 草稿表。 */
  private final WorkflowDraftMapper draftMapper;
  /** 已发布版本表。 */
  private final WorkflowVersionMapper versionMapper;
  /** Jackson ObjectMapper（图定义 convertValue 等）。 */
  private final ObjectMapper objectMapper;
  /** 统一 JSON 编解码。 */
  private final JacksonJsonSupport json;
  /** 运行态摘要查询。 */
  private final WorkflowMetricsQuery metricsQuery;

  /**
   * @param definitionMapper 定义表
   * @param draftMapper      草稿表
   * @param versionMapper    版本表
   * @param objectMapper     Jackson
   * @param metricsQuery     运行态摘要
   */
  public WorkflowViewAssembler(
      WorkflowDefinitionMapper definitionMapper,
      WorkflowDraftMapper draftMapper,
      WorkflowVersionMapper versionMapper,
      ObjectMapper objectMapper,
      WorkflowMetricsQuery metricsQuery) {
    this.definitionMapper = definitionMapper;
    this.draftMapper = draftMapper;
    this.versionMapper = versionMapper;
    this.objectMapper = objectMapper;
    this.json = new JacksonJsonSupport(objectMapper);
    this.metricsQuery = metricsQuery;
  }

  /**
   * 将节点运行实体转为对外视图。
   *
   * @param entity 节点运行实体
   * @return 节点运行视图
   */
  public WorkflowNodeRunView toNodeRunView(WorkflowNodeRunEntity entity) {
    return WorkflowNodeRunView.builder()
        .id(entity.getId())
        .nodeKey(entity.getNodeKey())
        .sequenceNo(entity.getSequenceNo())
        .attemptNo(entity.getAttemptNo())
        .status(entity.getStatus())
        .inputSummary(readMap(entity.getInputSummary()))
        .outputSummary(readMap(entity.getOutputSummary()))
        .errorCode(entity.getErrorCode())
        .errorMessage(entity.getErrorMessage())
        .startedAt(entity.getStartedAt() == null ? null : entity.getStartedAt().toString())
        .finishedAt(entity.getFinishedAt() == null ? null : entity.getFinishedAt().toString())
        .build();
  }

  /**
   * 对对象 JSON 做 SHA-256 摘要；失败返回空串。
   *
   * @param value 任意可序列化对象
   * @return 十六进制摘要
   */
  public String hashJson(Object value) {
    try {
      // 先 JSON 再哈希，保证 Map 字段序依赖 ObjectMapper 配置
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = objectMapper.writeValueAsBytes(value == null ? Map.of() : value);
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (Exception ignored) {
      // 摘要失败不阻断主流程
      return "";
    }
  }

  /**
   * 将运行实体转为对外视图；时间字段格式化为字符串。
   *
   * @param run 运行实体
   * @return {@link WorkflowRunView}
   */
  public WorkflowRunView toRunView(WorkflowRunEntity run) {
    return WorkflowRunView.builder()
        .id(run.getId())
        .jobId(run.getJobId())
        .workflowName(run.getWorkflowName())
        .status(run.getStatus())
        .currentNode(run.getCurrentNode())
        .workflowDefinitionId(run.getWorkflowDefinitionId())
        .workflowVersionId(run.getWorkflowVersionId())
        .triggerSource(run.getTriggerSource())
        .stateSummary(metricsQuery.assembleStateSummary(run))
        .costSummary(Map.of())
        .createdAt(run.getCreatedAt() == null ? null : run.getCreatedAt().toString())
        .updatedAt(run.getUpdatedAt() == null ? null : run.getUpdatedAt().toString())
        .finishedAt(run.getFinishedAt() == null ? null : run.getFinishedAt().toString())
        .build();
  }

  /**
   * 将告警实体转为视图。
   *
   * @param alert 告警实体
   * @return {@link WorkflowAlertView}
   */
  public WorkflowAlertView toAlertView(WorkflowAlertEntity alert) {
    return WorkflowAlertView.builder()
        .id(alert.getId())
        .workflowId(alert.getWorkflowId())
        .runId(alert.getRunId())
        .metric(alert.getMetric())
        .severity(alert.getSeverity())
        .title(alert.getTitle())
        .message(alert.getMessage())
        .threshold(Map.of())
        .status(alert.getStatus())
        .createdAt(alert.getCreatedAt() == null ? null : alert.getCreatedAt().toString())
        .resolvedAt(alert.getResolvedAt() == null ? null : alert.getResolvedAt().toString())
        .build();
  }

  /**
   * 将 JSON 字符串解析为 Map；空或解析失败返回空 Map。
   *
   * @param json JSON 文本
   * @return Map；永不返回 null
   */
  public Map<String, Object> readJsonMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception ignored) {
      // 坏 JSON 对外按空对象，避免列表接口整体失败
      return Map.of();
    }
  }

  /**
   * 定义实体转摘要视图：补齐已发布版本号与草稿 revision。
   *
   * @param def 定义实体
   * @return {@link WorkflowSummaryView}
   */
  public WorkflowSummaryView toSummary(WorkflowDefinitionEntity def) {
    // 解析已发布版本标签供列表展示
    String publishedVersion = null;
    if (def.getPublishedVersionId() != null) {
      WorkflowVersionEntity version = versionMapper.selectById(def.getPublishedVersionId());
      if (version != null) {
        publishedVersion = version.getVersion();
      }
    }
    WorkflowDraftEntity draft = draftMapper.selectOne(new LambdaQueryWrapper<WorkflowDraftEntity>()
        .eq(WorkflowDraftEntity::getWorkflowId, def.getId())
        .last("LIMIT 1"));
    return WorkflowSummaryView.builder()
        .id(def.getId())
        .key(def.getWorkflowKey())
        .name(def.getName())
        .description(def.getDescription() == null ? "" : def.getDescription())
        .scene(StrUtil.blankToDefault(def.getScene(), CourseWorkflowKeys.SCENE))
        .status(StrUtil.blankToDefault(def.getStatus(), WorkflowDefinitionStatus.DRAFT.getValue()))
        .owner(StrUtil.blankToDefault(def.getOwner(), WorkflowOwnerKeys.LOCAL))
        .publishedVersionId(def.getPublishedVersionId())
        .publishedVersion(publishedVersion)
        .draftRevision(draft == null || draft.getRevision() == null ? 1 : draft.getRevision())
        .updatedAt(def.getUpdatedAt() == null ? LocalDateTime.now().toString() : def.getUpdatedAt().toString())
        .build();
  }

  /**
   * 草稿实体转视图：解析 definition / validation JSON。
   *
   * @param draft 草稿实体
   * @param def   所属定义（用于空定义兜底）
   * @return {@link WorkflowDraftView}
   */
  public WorkflowDraftView toDraftView(WorkflowDraftEntity draft, WorkflowDefinitionEntity def) {
    WorkflowDefinitionDataView definition = toDefinitionData(readDefinition(draft.getDefinitionJson(), def));
    return WorkflowDraftView.builder()
        .id(draft.getId())
        .workflowId(draft.getWorkflowId())
        .baseVersionId(draft.getBaseVersionId())
        .revision(draft.getRevision() == null ? 1 : draft.getRevision())
        .definition(definition)
        // definitionJson 字段对外也回传同一结构化对象，兼容旧前端
        .definitionJson(definition)
        .validation(toValidationView(readValidation(draft.getValidationJson())))
        .updatedAt(draft.getUpdatedAt() == null ? LocalDateTime.now().toString() : draft.getUpdatedAt().toString())
        .build();
  }

  /**
   * 版本实体转视图：加载所属定义以解析快照。
   *
   * @param version 版本实体
   * @return {@link WorkflowVersionView}
   */
  public WorkflowVersionView toVersionView(WorkflowVersionEntity version) {
    WorkflowDefinitionEntity def = definitionMapper.selectById(version.getWorkflowId());
    return WorkflowVersionView.builder()
        .id(version.getId())
        .workflowId(version.getWorkflowId())
        .version(version.getVersion())
        .revision(version.getRevision() == null ? 1 : version.getRevision())
        .snapshot(toDefinitionData(readDefinition(
            version.getSnapshotJson(),
            // 定义被删时仍能解析快照，用空实体兜底元数据
            def == null ? new WorkflowDefinitionEntity() : def)))
        .validation(toValidationView(readValidation(version.getValidationJson())))
        .releaseNotes(version.getReleaseNotes() == null ? "" : version.getReleaseNotes())
        .publishedBy(version.getPublishedBy() == null ? "local" : version.getPublishedBy())
        .createdAt(version.getCreatedAt() == null ? LocalDateTime.now().toString() : version.getCreatedAt().toString())
        .build();
  }

  /**
   * 解析定义 JSON；空/非法时回退为基于 def 的 emptyDefinition。
   *
   * @param json 定义 JSON 文本
   * @param def  所属定义实体（兜底元数据）
   * @return 定义 Map
   */
  public Map<String, Object> readDefinition(String json, WorkflowDefinitionEntity def) {
    Map<String, Object> parsed = readMap(json);
    // 空/坏 JSON 时用定义元数据生成空图，避免编辑器崩溃
    return parsed == null || parsed.isEmpty() ? WorkflowGraphValidator.emptyDefinition(def) : parsed;
  }

  /**
   * 解析校验 JSON；空时返回 {@link #okValidation()}。
   *
   * @param json 校验结果 JSON
   * @return 校验 Map
   */
  public Map<String, Object> readValidation(String json) {
    Map<String, Object> parsed = readMap(json);
    // 无历史校验记录时视为通过
    return parsed == null || parsed.isEmpty() ? WorkflowGraphValidator.okValidation() : parsed;
  }

  /**
   * Map 转 {@link WorkflowDefinitionDataView}；空 Map 返回默认空图 POJO。
   *
   * @param map 定义 Map
   * @return 结构化定义数据
   */
  public WorkflowDefinitionDataView toDefinitionData(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return WorkflowDefinitionDataView.builder()
          .schemaVersion("1")
          .name("")
          .description("")
          .scene(CourseWorkflowKeys.SCENE)
          .modeId("")
          .nodes(List.of())
          .edges(List.of())
          .build();
    }
    return objectMapper.convertValue(map, WorkflowDefinitionDataView.class);
  }

  /**
   * {@link WorkflowDefinitionDataView} 转可写库的 LinkedHashMap。
   *
   * @param data 结构化定义；null 返回空 Map
   * @return 定义 Map
   */
  public Map<String, Object> toDefinitionMap(WorkflowDefinitionDataView data) {
    if (data == null) {
      return new LinkedHashMap<>();
    }
    return objectMapper.convertValue(data, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  /**
   * Map 转 {@link WorkflowValidationView}；空则返回默认通过视图。
   * <p>兼容历史数据：warnings/errors 可能是对象列表，也可能是字符串码列表（如 {@code skill_sot_repair}）。
   *
   * @param map 校验结果 Map
   * @return 校验视图
   */
  public WorkflowValidationView toValidationView(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return WorkflowValidationView.builder()
          .valid(true)
          .errors(List.of())
          .warnings(List.of())
          .checkedAt(LocalDateTime.now().toString())
          .definitionHash("")
          .build();
    }
    Object validRaw = map.get(WorkflowValidationMapKeys.VALID);
    boolean valid = validRaw instanceof Boolean b ? b : !"false".equalsIgnoreCase(String.valueOf(validRaw));
    return WorkflowValidationView.builder()
        .valid(valid)
        .errors(normalizeValidationIssues(map.get(WorkflowValidationMapKeys.ERRORS)))
        .warnings(normalizeValidationIssues(map.get(WorkflowValidationMapKeys.WARNINGS)))
        .checkedAt(map.get(WorkflowValidationMapKeys.CHECKED_AT) == null
            ? LocalDateTime.now().toString()
            : String.valueOf(map.get(WorkflowValidationMapKeys.CHECKED_AT)))
        .definitionHash(map.get(WorkflowValidationMapKeys.DEFINITION_HASH) == null
            ? ""
            : String.valueOf(map.get(WorkflowValidationMapKeys.DEFINITION_HASH)))
        .build();
  }

  /**
   * 将历史/现行校验条目规范为 {@link WorkflowValidationIssueView} 列表。
   *
   * @param raw 可能是 List&lt;Map&gt;、List&lt;String&gt;、单字符串、空串或 null
   * @return 非 null 列表
   */
  @SuppressWarnings("unchecked")
  public List<WorkflowValidationIssueView> normalizeValidationIssues(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof CharSequence seq) {
      String text = seq.toString().strip();
      if (text.isEmpty() || "[]".equals(text)) {
        return List.of();
      }
      return List.of(WorkflowViewAssembler.issueFromLegacyCode(text));
    }
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<WorkflowValidationIssueView> out = new ArrayList<>();
    for (Object item : list) {
      if (item == null) {
        continue;
      }
      if (item instanceof CharSequence seq) {
        String code = seq.toString().strip();
        if (!code.isEmpty()) {
          out.add(WorkflowViewAssembler.issueFromLegacyCode(code));
        }
        continue;
      }
      if (item instanceof Map<?, ?> itemMap) {
        Map<String, Object> asMap = (Map<String, Object>) itemMap;
        String code = asMap.get("code") == null ? "" : String.valueOf(asMap.get("code"));
        String message = asMap.get("message") == null ? code : String.valueOf(asMap.get("message"));
        String nodeKey = asMap.get("nodeKey") == null ? "" : String.valueOf(asMap.get("nodeKey"));
        if (code.isBlank() && message.isBlank()) {
          continue;
        }
        out.add(WorkflowValidationIssueView.builder()
            .code(code.isBlank() ? "legacy_warning" : code)
            .message(message.isBlank() ? code : message)
            .nodeKey(nodeKey)
            .build());
      }
    }
    return out;
  }

  /**
   * 用历史错误码构造校验问题视图（message 与 code 相同）。
   *
   * @param code 错误码
   * @return 校验问题视图
   */
  public static WorkflowValidationIssueView issueFromLegacyCode(String code) {
    return WorkflowValidationIssueView.builder()
        .code(code)
        .message(code)
        .nodeKey("")
        .build();
  }

  /**
   * 对象序列化为 JSON 字符串；失败包装为 IllegalStateException。
   *
   * @param value 任意可序列化对象
   * @return JSON 文本
   */
  public String writeJson(Object value) {
    return json.writeStrict(value);
  }

  /**
   * 解析 JSON 为 LinkedHashMap；空返回 null；解析失败则包一层 {@code raw}。
   *
   * @param json JSON 文本
   * @return Map、null，或 {@code {raw: 原文}}
   */
  public Map<String, Object> readMap(String json) {
    return this.json.readMapOrRaw(json);
  }
}
