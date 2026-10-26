package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.dto.view.WorkflowNodeRunView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点成文失败重试时的产物复用（对齐课程 {@code CourseArticleGraphSupport} 的文本产物模式）。
 * <p>优先读带 inputHash 的 contentText 产物；若无（旧运行仅有 node_output JSON），
 * 再从已成功节点的 outputSummary 取字段，避免整图重跑昂贵 LLM。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsArtifactReuseSupport {
  /** 工作流门面。 */
  private final WorkflowFacade workflows;

  /**
   * 尝试复用文本产物；无则返回 null。
   *
   * @param jobId         任务 ID
   * @param nodeKey       节点键（通常为 typeKey）
   * @param artifactType  产物类型
   * @param inputSummary  输入摘要；null 则取最近成功产物
   * @return 可复用正文；无则 null
   */
  public String tryReuseText(
      String jobId,
      String nodeKey,
      String artifactType,
      Map<String, Object> inputSummary) {
    if (StrUtil.isBlank(jobId) || StrUtil.isBlank(nodeKey) || StrUtil.isBlank(artifactType)) {
      return null;
    }
    String fromArtifact = inputSummary == null
        ? workflows.latestSucceededArtifactText(jobId, nodeKey, artifactType)
        : workflows.reusableArtifactText(jobId, nodeKey, artifactType, inputSummary);
    if (StrUtil.isNotBlank(fromArtifact)) {
      log.info("热点产物复用 contentText jobId={} nodeKey={} type={}", jobId, nodeKey, artifactType);
      return fromArtifact.strip();
    }
    return null;
  }

  /**
   * 从已成功节点的 outputSummary 取字段（兼容尚未落 contentText 的旧运行）。
   *
   * @param jobId    任务 ID
   * @param nodeKey  节点键
   * @param fieldKeys 按优先级尝试的字段名
   * @return 字段文本；无则 null
   */
  public String tryReuseFromNodeOutput(String jobId, String nodeKey, String... fieldKeys) {
    if (StrUtil.isBlank(jobId) || StrUtil.isBlank(nodeKey) || fieldKeys == null || fieldKeys.length == 0) {
      return null;
    }
    List<WorkflowNodeRunView> runs = workflows.listNodeRunsByJobId(jobId);
    for (int index = runs.size() - 1; index >= 0; index--) {
      WorkflowNodeRunView run = runs.get(index);
      if (run == null || !nodeKey.equals(run.getNodeKey())) {
        continue;
      }
      if (!"succeeded".equals(run.getStatus())) {
        continue;
      }
      Map<String, Object> summary = run.getOutputSummary();
      if (summary == null || summary.isEmpty()) {
        continue;
      }
      for (String fieldKey : fieldKeys) {
        Object value = summary.get(fieldKey);
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
          log.info(
              "热点产物复用 nodeOutput jobId={} nodeKey={} field={}",
              jobId,
              nodeKey,
              fieldKey);
          return text.strip();
        }
      }
    }
    return null;
  }

  /**
   * 复用事实包：先 contentText，再节点输出。
   *
   * @param jobId        任务 ID
   * @param nodeKey      节点键
   * @param inputSummary 输入摘要
   * @return 事实包 JSON；无则 null
   */
  public String tryReuseFacts(String jobId, String nodeKey, Map<String, Object> inputSummary) {
    String text = tryReuseText(
        jobId, nodeKey, com.chengwenfang.mode.hotnews.facade.constant.HotNewsArtifactTypes.FACTS, inputSummary);
    if (StrUtil.isNotBlank(text)) {
      return text;
    }
    return tryReuseFromNodeOutput(
        jobId, nodeKey, HotNewsGraphStateKeys.FACTS_JSON, "factsJson");
  }

  /**
   * 复用原稿。
   *
   * @param jobId         任务 ID
   * @param nodeKey       节点键
   * @param artifactType  DRAFT 或 REVIEWED_DRAFT
   * @param inputSummary  输入摘要
   * @return 原稿；无则 null
   */
  public String tryReuseDraft(
      String jobId,
      String nodeKey,
      String artifactType,
      Map<String, Object> inputSummary) {
    String text = tryReuseText(jobId, nodeKey, artifactType, inputSummary);
    if (StrUtil.isNotBlank(text)) {
      return text;
    }
    return tryReuseFromNodeOutput(
        jobId, nodeKey, HotNewsGraphStateKeys.DRAFT, nodeKey, "draft");
  }

  /**
   * 复用公众号正文。
   *
   * @param jobId         任务 ID
   * @param nodeKey       节点键
   * @param artifactType  WECHAT 或 REVIEWED_WECHAT
   * @param inputSummary  输入摘要
   * @return 公众号正文；无则 null
   */
  public String tryReuseWechat(
      String jobId,
      String nodeKey,
      String artifactType,
      Map<String, Object> inputSummary) {
    String text = tryReuseText(jobId, nodeKey, artifactType, inputSummary);
    if (StrUtil.isNotBlank(text)) {
      return text;
    }
    return tryReuseFromNodeOutput(
        jobId, nodeKey, HotNewsGraphStateKeys.WECHAT, nodeKey, "wechat");
  }

  /**
   * 写入可复用文本产物（另开 node_run，供后续重试按 hash 命中）。
   *
   * @param jobId         任务 ID
   * @param nodeKey       节点键
   * @param inputSummary  输入摘要
   * @param artifactType  产物类型
   * @param contentText   正文
   * @param outputSummary 输出摘要
   */
  public void persistTextArtifact(
      String jobId,
      String nodeKey,
      Map<String, Object> inputSummary,
      String artifactType,
      String contentText,
      Map<String, Object> outputSummary) {
    if (StrUtil.isBlank(jobId)
        || StrUtil.isBlank(nodeKey)
        || StrUtil.isBlank(artifactType)
        || StrUtil.isBlank(contentText)) {
      return;
    }
    String nodeRunId = workflows.beginNodeRun(
        jobId, nodeKey, 0, inputSummary == null ? Map.of() : inputSummary);
    if (StrUtil.isBlank(nodeRunId)) {
      return;
    }
    workflows.completeNodeRun(
        nodeRunId,
        true,
        outputSummary == null ? Map.of() : outputSummary,
        artifactType,
        contentText,
        null,
        null);
  }

  /**
   * 将对象压成稳定短摘要，避免整篇正文进 inputHash。
   *
   * @param value 任意值
   * @return 摘要字符串
   */
  public static String fingerprint(Object value) {
    if (value == null) {
      return "";
    }
    String text = value instanceof String string ? string : JSONUtil.toJsonStr(value);
    if (StrUtil.isBlank(text)) {
      return "";
    }
    // 长度 + 首尾片段，足够判断输入是否相对上次变化
    int length = text.length();
    String head = text.substring(0, Math.min(80, length));
    String tail = length <= 80 ? "" : text.substring(Math.max(0, length - 40));
    return length + "|" + head + "|" + tail;
  }
}
