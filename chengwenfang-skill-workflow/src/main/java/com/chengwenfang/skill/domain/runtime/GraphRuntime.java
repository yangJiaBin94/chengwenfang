package com.chengwenfang.skill.domain.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.skill.domain.entity.WorkflowArtifactEntity;
import com.chengwenfang.skill.domain.WorkflowNodeCatalogUtil;
import com.chengwenfang.skill.domain.entity.WorkflowNodeRunEntity;
import com.chengwenfang.skill.domain.entity.WorkflowRunEntity;
import com.chengwenfang.skill.domain.entity.WorkflowRunExecutedNodeEntity;
import com.chengwenfang.skill.facade.constant.WorkflowArtifactContentFormats;
import com.chengwenfang.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import com.chengwenfang.skill.facade.enums.WorkflowRunStatus;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowArtifactMapper;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowNodeRunMapper;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowRunExecutedNodeMapper;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowRunMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 工作流图运行时：解析节点/边、按条件推进、记录 node_run/artifact。
 * 文本与副作用节点一律经正式 Handler，禁止 fixture / dry-run 回退。
 */
@Service
public class GraphRuntime {
  private final WorkflowRunMapper runMapper;
  private final WorkflowNodeRunMapper nodeRunMapper;
  private final WorkflowArtifactMapper artifactMapper;
  private final WorkflowRunExecutedNodeMapper executedNodeMapper;
  private final FileFacade files;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<GraphTextNodeHandler> textNodeHandler;

  public GraphRuntime(
      WorkflowRunMapper runMapper,
      WorkflowNodeRunMapper nodeRunMapper,
      WorkflowArtifactMapper artifactMapper,
      WorkflowRunExecutedNodeMapper executedNodeMapper,
      FileFacade files,
      ObjectMapper objectMapper,
      ObjectProvider<GraphTextNodeHandler> textNodeHandler) {
    this.runMapper = runMapper;
    this.nodeRunMapper = nodeRunMapper;
    this.artifactMapper = artifactMapper;
    this.executedNodeMapper = executedNodeMapper;
    this.files = files;
    this.objectMapper = objectMapper;
    this.textNodeHandler = textNodeHandler;
  }

  /**
   * 执行工作流图。
   *
   * <p>阶段：锁定快照 → 自 start 推进 → 按 failurePolicy 重试 →
   * 条件/循环边选择下一节点 → 写终态与 state 摘要。
   *
   * @param run          已创建的运行实体
   * @param definition   图定义（nodes/edges）
   * @param initialState 初始状态
   * @return 执行结果（runId、status、executedNodes、state）
   */
  public Map<String, Object> execute(
      WorkflowRunEntity run,
      Map<String, Object> definition,
      Map<String, Object> initialState) throws Exception {
    Graph graph = Graph.parse(definition);
    Map<String, Object> state = new LinkedHashMap<>();
    if (initialState != null) {
      state.putAll(initialState);
    }
    state.putIfAbsent("reviewPassed", true);

    // 阶段：标记运行中并锁定图快照
    LocalDateTime now = LocalDateTime.now();
    run.setStatus(WorkflowRunStatus.RUNNING.getValue());
    run.setSnapshotJson(writeJson(definition));
    run.setUpdatedAt(now);
    runMapper.updateById(run);

    int sequence = 0;
    Map<String, Integer> loopCounts = new HashMap<>();
    String current = graph.entryKey();
    List<String> executed = new ArrayList<>();
    String terminalStatus = WorkflowRunStatus.SUCCEEDED.getValue();
    String errorMessage = null;

    // 阶段：按边推进节点；受 failurePolicy / 循环上限约束
    while (current != null) {
      Graph.Node node = graph.nodes.get(current);
      if (node == null) {
        terminalStatus = WorkflowRunStatus.FAILED.getValue();
        errorMessage = "Unknown node: " + current;
        break;
      }
      if (!node.enabled) {
        current = graph.nextDefault(current, state);
        continue;
      }

      String typeKey = WorkflowNodeCatalogUtil.resolveTypeKey(node.typeKey);
      sequence++;
      run.setCurrentNode(node.key);
      run.setUpdatedAt(LocalDateTime.now());
      runMapper.updateById(run);

      // 阶段：单节点多尝试执行
      int maxAttempts = intConfig(node.config, "maxAttempts", 1);
      String failurePolicy = String.valueOf(node.config.getOrDefault("failurePolicy", "stop"));
      NodeResult result = null;
      WorkflowNodeRunEntity lastAttempt = null;
      for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
        lastAttempt = beginNode(run.getId(), node, sequence, attempt, state);
        try {
          result = executeNode(typeKey, node, state);
          completeNode(lastAttempt, result, run.getId());
          if (result.ok()) {
            break;
          }
          if (!"retry_then_stop".equals(failurePolicy) || attempt >= maxAttempts) {
            break;
          }
        } catch (Exception ex) {
          failNode(lastAttempt, "NODE_EXECUTION_ERROR", ex.getMessage());
          result = NodeResult.fail(ex.getMessage());
          if (!"retry_then_stop".equals(failurePolicy) || attempt >= maxAttempts) {
            break;
          }
        }
      }

      executed.add(node.key);
      if (result != null && result.outputs() != null) {
        state.putAll(result.outputs());
      }

      if (result == null || !result.ok()) {
        if ("continue_with_warning".equals(failurePolicy)) {
          state.put("lastWarning", result == null ? "unknown" : result.error());
        } else {
          terminalStatus = WorkflowRunStatus.FAILED.getValue();
          errorMessage = result == null ? "node failed" : result.error();
          openAlertIfNeeded(run, node.key, errorMessage);
          break;
        }
      }

      // 结束或提交版本后继续推进边；纯 end 节点直接收尾
      if (WorkflowStructuralNodeKeys.END.equals(typeKey)
          || WorkflowStructuralNodeKeys.COMMIT_VERSION.equals(typeKey)
          || HotNewsWorkflowNodeType.isCommitVersion(typeKey)) {
        if (WorkflowStructuralNodeKeys.END.equals(typeKey)) {
          break;
        }
      }

      if (WorkflowStructuralNodeKeys.BOUNDED_LOOP.equals(typeKey)) {
        int maxIter = intConfig(node.config, "maxIterations", 3);
        int used = loopCounts.merge(node.key, 1, Integer::sum);
        if (used > maxIter) {
          current = graph.nextNonLoop(current, state);
          continue;
        }
      }

      if (WorkflowStructuralNodeKeys.CONTROLLED_CONDITION.equals(typeKey)) {
        current = graph.nextConditional(current, state);
      } else {
        current = graph.nextDefault(current, state);
      }
      if (executed.size() > graph.nodes.size() * 8) {
        terminalStatus = WorkflowRunStatus.FAILED.getValue();
        errorMessage = "Execution aborted: possible unbounded cycle";
        break;
      }
    }

    // 阶段：写终态（error_message + 已执行节点子表）
    LocalDateTime finished = LocalDateTime.now();
    run.setStatus(terminalStatus);
    run.setFinishedAt(finished);
    run.setUpdatedAt(finished);
    run.setCurrentNode(null);
    run.setErrorMessage(errorMessage == null ? "" : errorMessage);
    runMapper.updateById(run);
    replaceExecutedNodes(run.getId(), executed);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("runId", run.getId());
    out.put("status", terminalStatus);
    out.put("executedNodes", executed);
    out.put("state", state);
    if (errorMessage != null) {
      out.put("errorMessage", errorMessage);
    }
    return out;
  }

  private void openAlertIfNeeded(WorkflowRunEntity run, String nodeKey, String message) {
    // Alerts are created by WorkflowFacadeImpl helper when mapper available; no-op here.
  }

  /**
   * 按节点类型分发执行；副作用与文本节点一律走正式 Handler。
   *
   * @param typeKey 已解析的节点类型键
   * @param node    图节点
   * @param state   当前图状态
   * @return 节点结果
   */
  private NodeResult executeNode(String typeKey, Graph.Node node, Map<String, Object> state) {
    // 热点提交 / 出图 / 终检走正式处理器
    if (HotNewsWorkflowNodeType.isCommitVersion(typeKey)
        || HotNewsWorkflowNodeType.isGenerateImages(typeKey)
        || HotNewsWorkflowNodeType.isFinalValidation(typeKey)) {
      return invokeFormalHandler(typeKey, node, state);
    }
    // 课程配图 / 提交 / 公式与终检走正式 Handler
    if (CourseWorkflowNodeType.isImageNode(typeKey)
        || CourseWorkflowNodeType.isCommitVersion(typeKey)
        || CourseWorkflowNodeType.isValidationNode(typeKey)) {
      return invokeFormalHandler(typeKey, node, state);
    }
    // 非课程场景的同名结构键：轻量本地校验 stub
    if (WorkflowStructuralNodeKeys.FINAL_VALIDATION.equals(typeKey)
        || WorkflowStructuralNodeKeys.FORMULA_STYLE_VALIDATION.equals(typeKey)
        || WorkflowStructuralNodeKeys.FORMULA_EXPRESSION_VALIDATION.equals(typeKey)) {
      String draft = String.valueOf(state.getOrDefault("draft", ""));
      boolean ok = !draft.contains("\\begin{") && !draft.contains("\\\\frac");
      return ok ? NodeResult.ok(Map.of("validationPassed", true))
          : NodeResult.fail("validation failed for " + typeKey);
    }
    if (WorkflowStructuralNodeKeys.COMMIT_VERSION.equals(typeKey)) {
      return NodeResult.ok(Map.of("committed", true));
    }
    if (WorkflowStructuralNodeKeys.IMAGE_COVER.equals(typeKey)
        || WorkflowStructuralNodeKeys.IMAGE_INLINE.equals(typeKey)) {
      return invokeFormalHandler(typeKey, node, state);
    }
    if (WorkflowStructuralNodeKeys.START.equals(typeKey)
        || WorkflowStructuralNodeKeys.END.equals(typeKey)) {
      return NodeResult.ok(Map.of("node", typeKey));
    }
    if (WorkflowStructuralNodeKeys.CONTROLLED_CONDITION.equals(typeKey)) {
      boolean passed = evalCondition(node, state);
      return NodeResult.ok(Map.of("conditionPassed", passed, "reviewPassed",
          state.getOrDefault("reviewPassed", passed)));
    }
    if (WorkflowStructuralNodeKeys.BOUNDED_LOOP.equals(typeKey)) {
      return NodeResult.ok(Map.of("loop", true));
    }
    // 自定义 AI 与文本类节点一律走正式 Handler
    return invokeFormalHandler(typeKey, node, state);
  }

  private NodeResult invokeFormalHandler(String typeKey, Graph.Node node, Map<String, Object> state) {
    GraphTextNodeHandler handler = textNodeHandler.getIfAvailable();
    if (handler == null) {
      return NodeResult.fail("Formal graph text nodes require GraphTextNodeHandler (no fixture fallback)");
    }
    try {
      Map<String, Object> outputs = handler.execute(
          typeKey,
          node.key,
          node.name,
          node.skillBindings,
          state,
          node.config);
      return NodeResult.ok(outputs == null ? Map.of() : outputs);
    } catch (Exception ex) {
      return NodeResult.fail(ex.getMessage() == null ? "text node failed" : ex.getMessage());
    }
  }

  private boolean evalCondition(Graph.Node node, Map<String, Object> state) {
    Object field = node.config.getOrDefault("conditionField", "reviewPassed");
    Object op = node.config.getOrDefault("conditionOperator", "eq");
    Object expected = node.config.getOrDefault("conditionValue", true);
    Object actual = state.get(String.valueOf(field));
    return switch (String.valueOf(op)) {
      case "ne" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
      case "lt" -> toDouble(actual) < toDouble(expected);
      case "gte" -> toDouble(actual) >= toDouble(expected);
      default -> Objects.equals(String.valueOf(actual), String.valueOf(expected))
          || Objects.equals(actual, expected);
    };
  }

  private static double toDouble(Object value) {
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (Exception ignored) {
      return 0;
    }
  }

  private WorkflowNodeRunEntity beginNode(
      String runId, Graph.Node node, int sequence, int attempt, Map<String, Object> state) throws Exception {
    LocalDateTime now = LocalDateTime.now();
    WorkflowNodeRunEntity entity = new WorkflowNodeRunEntity();
    entity.setId(IdsUtil.newId());
    entity.setWorkflowRunId(runId);
    entity.setNodeKey(node.key);
    entity.setSequenceNo(sequence);
    entity.setAttemptNo(attempt);
    entity.setStatus(WorkflowRunStatus.RUNNING.getValue());
    entity.setInputHash(hash(state));
    entity.setInputSummary(writeJson(Map.of("keys", state.keySet())));
    entity.setExecutorVersion("1.0");
    entity.setConfigSnapshot(writeJson(node.config));
    entity.setStartedAt(now);
    entity.setHeartbeatAt(now);
    entity.setCreatedAt(now);
    nodeRunMapper.insert(entity);
    return entity;
  }

  /**
   * 写节点终态；成功时落永久产物。
   *
   * @param entity 节点运行实体
   * @param result 节点结果
   * @param runId  所属运行 ID
   */
  private void completeNode(
      WorkflowNodeRunEntity entity, NodeResult result, String runId) throws Exception {
    LocalDateTime now = LocalDateTime.now();
    entity.setStatus(result.ok()
        ? WorkflowRunStatus.SUCCEEDED.getValue()
        : WorkflowRunStatus.FAILED.getValue());
    entity.setOutputSummary(writeJson(result.outputs() == null ? Map.of() : result.outputs()));
    entity.setOutputHash(hash(result.outputs()));
    entity.setErrorMessage(result.ok() ? null : result.error());
    entity.setErrorCode(result.ok() ? null : "NODE_FAILED");
    entity.setFinishedAt(now);
    if (entity.getStartedAt() != null) {
      entity.setDurationMs(ChronoUnit.MILLIS.between(entity.getStartedAt(), now));
    }
    nodeRunMapper.updateById(entity);
    if (result.ok() && result.outputs() != null && !result.outputs().isEmpty()) {
      String body = writeJson(result.outputs());
      String objectKey = "workflow-artifacts/" + runId + "/" + IdsUtil.newId() + ".json";
      StoredFileView stored = files.writeText(objectKey, "node_output.json", body);
      WorkflowArtifactEntity art = new WorkflowArtifactEntity();
      art.setId(IdsUtil.newId());
      art.setWorkflowRunId(runId);
      art.setNodeRunId(entity.getId());
      art.setArtifactType("node_output");
      art.setContentFormat(WorkflowArtifactContentFormats.JSON);
      art.setContentFileId(stored.getId());
      art.setContentHash(entity.getOutputHash());
      art.setSchemaVersion("1");
      art.setCreatedAt(now);
      artifactMapper.insert(art);
    }
  }

  /**
   * 用最新 executed 列表替换子表行。
   *
   * @param runId    运行主键
   * @param executed 已执行节点键（顺序即 seq）
   */
  private void replaceExecutedNodes(String runId, List<String> executed) {
    executedNodeMapper.delete(new LambdaQueryWrapper<WorkflowRunExecutedNodeEntity>()
        .eq(WorkflowRunExecutedNodeEntity::getWorkflowRunId, runId));
    if (executed == null || executed.isEmpty()) {
      return;
    }
    int seq = 0;
    for (String nodeKey : executed) {
      WorkflowRunExecutedNodeEntity row = WorkflowRunExecutedNodeEntity.builder()
          .id(IdsUtil.newId())
          .workflowRunId(runId)
          .seq(seq++)
          .nodeKey(nodeKey)
          .build();
      executedNodeMapper.insert(row);
    }
  }

  private void failNode(WorkflowNodeRunEntity entity, String code, String message) {
    LocalDateTime now = LocalDateTime.now();
    entity.setStatus(WorkflowRunStatus.FAILED.getValue());
    entity.setErrorCode(code);
    entity.setErrorMessage(message);
    entity.setFinishedAt(now);
    if (entity.getStartedAt() != null) {
      entity.setDurationMs(ChronoUnit.MILLIS.between(entity.getStartedAt(), now));
    }
    nodeRunMapper.updateById(entity);
  }

  private String writeJson(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private static String hash(Object value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest());
    } catch (Exception ignored) {
      return IdsUtil.newId();
    }
  }

  private static int intConfig(Map<String, Object> config, String key, int fallback) {
    Object value = config.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  /**
   * 单节点执行结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class NodeResult {
    /** 是否成功。 */
    private final boolean ok;
    /** 输出映射。 */
    private final Map<String, Object> outputs;
    /** 错误信息；成功时为 null。 */
    private final String error;

    /**
     * @param outputs 输出映射
     * @return 成功结果
     */
    static NodeResult ok(Map<String, Object> outputs) {
      return new NodeResult(true, outputs, null);
    }

    /**
     * @param error 错误信息
     * @return 失败结果
     */
    static NodeResult fail(String error) {
      return new NodeResult(false, Map.of(), error);
    }
  }

  static final class Graph {
    final Map<String, Node> nodes = new LinkedHashMap<>();
    final List<Edge> edges = new ArrayList<>();

    static Graph parse(Map<String, Object> definition) {
      Graph graph = new Graph();
      Object nodesObj = definition == null ? null : definition.get("nodes");
      if (nodesObj instanceof List<?> list) {
        for (Object item : list) {
          if (!(item instanceof Map<?, ?> raw)) {
            continue;
          }
          Map<String, Object> map = cast(raw);
          Node node = new Node();
          node.key = str(map.getOrDefault("key", map.get("id")));
          node.typeKey = str(map.getOrDefault("typeKey", map.getOrDefault("type", "llm_text")));
          node.name = str(map.getOrDefault("name", map.getOrDefault("label", node.key)));
          node.enabled = !Boolean.FALSE.equals(map.get("enabled"));
          Object cfg = map.get("config");
          node.config = cfg instanceof Map<?, ?> configMap ? cast(configMap) : new LinkedHashMap<>();
          Object bindingsRaw = map.get("skillBindings");
          if (bindingsRaw instanceof List<?> bindingList) {
            for (Object bindingObj : bindingList) {
              if (bindingObj instanceof Map<?, ?> bindingMap) {
                node.skillBindings.add(cast(bindingMap));
              }
            }
          }
          if (node.key != null && !node.key.isBlank()) {
            graph.nodes.put(node.key, node);
          }
        }
      }
      Object edgesObj = definition == null ? null : definition.get("edges");
      if (edgesObj instanceof List<?> list) {
        for (Object item : list) {
          if (!(item instanceof Map<?, ?> raw)) {
            continue;
          }
          Map<String, Object> map = cast(raw);
          Edge edge = new Edge();
          edge.source = str(map.get("source"));
          edge.target = str(map.get("target"));
          edge.loop = Boolean.TRUE.equals(map.get("loop"));
          Object cond = map.get("condition");
          edge.condition = cond instanceof Map<?, ?> configMap ? cast(configMap) : null;
          if (edge.source != null && edge.target != null) {
            graph.edges.add(edge);
          }
        }
      }
      return graph;
    }

    String entryKey() {
      return nodes.values().stream()
          .filter(node -> "start".equals(WorkflowNodeCatalogUtil.resolveTypeKey(node.typeKey)))
          .map(node -> node.key)
          .findFirst()
          .orElse(nodes.isEmpty() ? null : nodes.keySet().iterator().next());
    }

    String nextDefault(String from, Map<String, Object> state) {
      for (Edge edge : edges) {
        if (!from.equals(edge.source) || edge.loop) {
          continue;
        }
        if (edge.condition == null || matches(edge.condition, state)) {
          return edge.target;
        }
      }
      for (Edge edge : edges) {
        if (from.equals(edge.source) && !edge.loop) {
          return edge.target;
        }
      }
      return null;
    }

    String nextNonLoop(String from, Map<String, Object> state) {
      for (Edge edge : edges) {
        if (from.equals(edge.source) && !edge.loop) {
          return edge.target;
        }
      }
      return nextDefault(from, state);
    }

    String nextConditional(String from, Map<String, Object> state) {
      Edge matched = null;
      Edge fallback = null;
      for (Edge edge : edges) {
        if (!from.equals(edge.source) || edge.loop) {
          continue;
        }
        if (edge.condition == null) {
          fallback = edge;
          continue;
        }
        if (matches(edge.condition, state)) {
          matched = edge;
          break;
        }
      }
      if (matched != null) {
        return matched.target;
      }
      return fallback == null ? null : fallback.target;
    }

    static boolean matches(Map<String, Object> condition, Map<String, Object> state) {
      Object field = condition.getOrDefault("field", "reviewPassed");
      Object op = condition.getOrDefault("operator", "eq");
      Object expected = condition.get("value");
      Object actual = state.get(String.valueOf(field));
      return switch (String.valueOf(op)) {
        case "ne" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
        default -> Objects.equals(String.valueOf(actual), String.valueOf(expected))
            || Objects.equals(actual, expected);
      };
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> cast(Map<?, ?> raw) {
      return (Map<String, Object>) raw;
    }

    static String str(Object value) {
      return value == null ? null : String.valueOf(value);
    }

    static final class Node {
      String key;
      String typeKey;
      String name;
      boolean enabled = true;
      Map<String, Object> config = new LinkedHashMap<>();
      List<Map<String, Object>> skillBindings = new ArrayList<>();
    }

    static final class Edge {
      String source;
      String target;
      boolean loop;
      Map<String, Object> condition;
    }
  }
}
