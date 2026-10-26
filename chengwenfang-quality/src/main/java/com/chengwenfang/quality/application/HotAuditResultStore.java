package com.chengwenfang.quality.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.quality.domain.entity.ArticleAuditIssueEntity;
import com.chengwenfang.quality.domain.entity.ArticleAuditRunEntity;
import com.chengwenfang.quality.domain.entity.AuditIssueRefEntity;
import com.chengwenfang.quality.facade.constant.ArticleAuditDomains;
import com.chengwenfang.quality.facade.constant.AuditIssueMapKeys;
import com.chengwenfang.quality.facade.constant.AuditIssueRefTypes;
import com.chengwenfang.quality.facade.constant.HotAuditResultKeys;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import com.chengwenfang.quality.infrastructure.mapper.ArticleAuditIssueMapper;
import com.chengwenfang.quality.infrastructure.mapper.AuditIssueRefMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点审稿结果：运行列 + {@code audit_issues} + {@code audit_issue_refs} ↔ Map。
 */
@Component
@RequiredArgsConstructor
public class HotAuditResultStore {
  private final ArticleAuditIssueMapper issueMapper;
  private final AuditIssueRefMapper refMapper;
  private final ObjectMapper objectMapper;

  /**
   * 将引擎结果 Map 写入运行列，并全量替换 issues / refs。
   *
   * @param run     已持久化或待更新的运行行（须有 id）
   * @param payload 结果 Map；null 按空处理
   * @param now     落库时间
   */
  @Transactional
  public void write(ArticleAuditRunEntity run, Map<String, Object> payload, LocalDateTime now) {
    if (run == null || StrUtil.isBlank(run.getId())) {
      return;
    }
    Map<String, Object> map = payload == null ? Map.of() : payload;
    Object statusValue = map.get(HotAuditResultKeys.STATUS);
    String resultStatus = statusValue == null ? null : String.valueOf(statusValue);
    run.setResultStatus(resultStatus);
    run.setBlockingFailed(Boolean.TRUE.equals(map.get(HotAuditResultKeys.BLOCKING_FAILED)));
    if (map.containsKey(HotAuditResultKeys.DECISION_STATUS)) {
      Object decisionStatus = map.get(HotAuditResultKeys.DECISION_STATUS);
      run.setDecisionStatus(decisionStatus == null ? null : String.valueOf(decisionStatus));
    }
    if (map.containsKey(HotAuditResultKeys.DECISION_COMMENT)) {
      Object decisionComment = map.get(HotAuditResultKeys.DECISION_COMMENT);
      run.setDecisionComment(decisionComment == null ? "" : String.valueOf(decisionComment));
    }
    if (map.containsKey(HotAuditResultKeys.DECIDED_AT)) {
      Object decidedAt = map.get(HotAuditResultKeys.DECIDED_AT);
      run.setDecidedAt(decidedAt == null ? null : String.valueOf(decidedAt));
    }
    if (StrUtil.isNotBlank(resultStatus)) {
      run.setStatus(resultStatus);
    }
    replaceIssues(run.getId(), asResultList(map.get(HotAuditResultKeys.RESULTS)), now);
  }

  /**
   * 仅写入人工裁决三字段（运行不存在时由调用方处理）。
   *
   * @param run     运行行
   * @param status  裁决状态
   * @param comment 裁决说明；null 转空串
   */
  @Transactional
  public void writeDecision(ArticleAuditRunEntity run, String status, String comment) {
    if (run == null) {
      return;
    }
    run.setDecisionStatus(status);
    run.setDecisionComment(comment == null ? "" : comment);
    run.setDecidedAt(LocalDateTime.now().toString());
  }

  /**
   * 从运行列与子表组装结果 Map（键序与原 result_json 一致）。
   *
   * @param run 运行行
   * @return Map；永不为 null
   */
  public Map<String, Object> read(ArticleAuditRunEntity run) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (run == null) {
      return out;
    }
    String status = StrUtil.blankToDefault(run.getResultStatus(), run.getStatus());
    if (StrUtil.isNotBlank(status)) {
      out.put(HotAuditResultKeys.STATUS, status);
    }
    out.put(HotAuditResultKeys.BLOCKING_FAILED, Boolean.TRUE.equals(run.getBlockingFailed()));
    out.put(HotAuditResultKeys.RESULTS, readIssues(run.getId()));
    if (StrUtil.isNotBlank(run.getDecisionStatus())) {
      out.put(HotAuditResultKeys.DECISION_STATUS, run.getDecisionStatus());
      out.put(
          HotAuditResultKeys.DECISION_COMMENT,
          run.getDecisionComment() == null ? "" : run.getDecisionComment());
      if (run.getDecidedAt() != null) {
        out.put(HotAuditResultKeys.DECIDED_AT, run.getDecidedAt());
      }
    }
    return out;
  }

  /**
   * 按 seq 读取问题 Map 列表（含 locations / evidence）。
   *
   * @param auditRunId 运行 id
   * @return 问题列表
   */
  public List<Map<String, Object>> readIssues(String auditRunId) {
    if (StrUtil.isBlank(auditRunId)) {
      return List.of();
    }
    List<ArticleAuditIssueEntity> issues = issueMapper.selectList(
        new LambdaQueryWrapper<ArticleAuditIssueEntity>()
            .eq(ArticleAuditIssueEntity::getAuditRunId, auditRunId)
            .orderByAsc(ArticleAuditIssueEntity::getSeq));
    if (issues.isEmpty()) {
      return List.of();
    }
    List<String> issueIds = issues.stream().map(ArticleAuditIssueEntity::getId).toList();
    List<AuditIssueRefEntity> allRefs = refMapper.selectList(
        new LambdaQueryWrapper<AuditIssueRefEntity>()
            .in(AuditIssueRefEntity::getIssueId, issueIds)
            .orderByAsc(AuditIssueRefEntity::getSeq));
    List<Map<String, Object>> out = new ArrayList<>();
    for (ArticleAuditIssueEntity issue : issues) {
      out.add(toIssueMap(issue, allRefs));
    }
    return out;
  }

  /**
   * 全量替换 issues 与 refs。
   *
   * @param auditRunId 运行 id
   * @param results    问题 Map 列表
   * @param now        创建时间
   */
  @Transactional
  public void replaceIssues(String auditRunId, List<Map<String, Object>> results, LocalDateTime now) {
    if (StrUtil.isBlank(auditRunId)) {
      return;
    }
    List<ArticleAuditIssueEntity> existing = issueMapper.selectList(
        new LambdaQueryWrapper<ArticleAuditIssueEntity>()
            .eq(ArticleAuditIssueEntity::getAuditRunId, auditRunId));
    for (ArticleAuditIssueEntity old : existing) {
      refMapper.delete(new LambdaQueryWrapper<AuditIssueRefEntity>()
          .eq(AuditIssueRefEntity::getIssueId, old.getId()));
    }
    issueMapper.delete(new LambdaQueryWrapper<ArticleAuditIssueEntity>()
        .eq(ArticleAuditIssueEntity::getAuditRunId, auditRunId));
    if (CollUtil.isEmpty(results)) {
      return;
    }
    LocalDateTime createdAt = now == null ? LocalDateTime.now() : now;
    int seq = 0;
    for (Map<String, Object> raw : results) {
      if (raw == null) {
        continue;
      }
      ArticleAuditIssueEntity issue = new ArticleAuditIssueEntity();
      issue.setId(IdsUtil.newId());
      issue.setAuditRunId(auditRunId);
      issue.setSeq(seq++);
      String code = stringOf(raw.get(AuditIssueMapKeys.CODE));
      if (StrUtil.isBlank(code)) {
        code = stringOf(raw.get(AuditIssueMapKeys.ID));
      }
      if (StrUtil.isBlank(code)) {
        code = "UNKNOWN";
      }
      issue.setCode(code);
      String title = stringOf(raw.get(AuditIssueMapKeys.TITLE));
      if (StrUtil.isBlank(title)) {
        title = stringOf(raw.get(AuditIssueMapKeys.NAME));
      }
      issue.setTitle(StrUtil.blankToDefault(title, code));
      String status = stringOf(raw.get(AuditIssueMapKeys.STATUS));
      ReviewStatus parsed = ReviewStatus.from(StrUtil.blankToDefault(status, ReviewStatus.FAILED.getValue()));
      if (parsed == ReviewStatus.PASSED) {
        status = ReviewStatus.PASSED.getValue();
      } else if (parsed.isWarnLike()) {
        status = ReviewStatus.WARN.getValue();
      } else {
        status = ReviewStatus.FAILED.getValue();
      }
      issue.setStatus(status);
      issue.setBlocking(Boolean.TRUE.equals(raw.get(AuditIssueMapKeys.BLOCKING)));
      Object detail = raw.get(AuditIssueMapKeys.DETAIL);
      if (detail == null) {
        detail = raw.get(AuditIssueMapKeys.MESSAGE);
      }
      issue.setDetail(detail == null ? "" : String.valueOf(detail));
      issue.setEngine(stringOrNull(raw.get(AuditIssueMapKeys.ENGINE)));
      issue.setCategory(stringOrNull(raw.get(AuditIssueMapKeys.CATEGORY)));
      issue.setSeverity(stringOrNull(raw.get(AuditIssueMapKeys.SEVERITY)));
      if (raw.get(AuditIssueMapKeys.REPAIRABLE) instanceof Boolean repairable) {
        issue.setRepairable(repairable);
      }
      if (raw.get(AuditIssueMapKeys.REQUIRES_HUMAN_CONFIRMATION) instanceof Boolean requires) {
        issue.setRequiresHumanConfirmation(requires);
      }
      issue.setCreatedAt(createdAt);
      issueMapper.insert(issue);
      writeRefs(issue.getId(), AuditIssueRefTypes.LOCATION, raw.get(AuditIssueMapKeys.LOCATIONS));
      writeRefs(issue.getId(), AuditIssueRefTypes.EVIDENCE, raw.get(AuditIssueMapKeys.EVIDENCE));
    }
  }

  /**
   * 问题实体 + refs → Map。
   *
   * @param issue   问题行
   * @param allRefs 本运行下全部 refs（按 seq 已排序）
   * @return 问题 Map
   */
  private Map<String, Object> toIssueMap(
      ArticleAuditIssueEntity issue, List<AuditIssueRefEntity> allRefs) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(AuditIssueMapKeys.CODE, issue.getCode());
    if (StrUtil.isNotBlank(issue.getEngine())) {
      map.put(AuditIssueMapKeys.ENGINE, issue.getEngine());
    } else {
      map.put(AuditIssueMapKeys.ENGINE, ArticleAuditDomains.ENGINE_RULE);
    }
    if (issue.getCategory() != null) {
      map.put(AuditIssueMapKeys.CATEGORY, issue.getCategory());
    }
    map.put(AuditIssueMapKeys.STATUS, issue.getStatus());
    if (issue.getSeverity() != null) {
      map.put(AuditIssueMapKeys.SEVERITY, issue.getSeverity());
    }
    map.put(AuditIssueMapKeys.BLOCKING, Boolean.TRUE.equals(issue.getBlocking()));
    map.put(AuditIssueMapKeys.TITLE, issue.getTitle());
    map.put(AuditIssueMapKeys.DETAIL, issue.getDetail() == null ? "" : issue.getDetail());
    map.put(
        AuditIssueMapKeys.LOCATIONS,
        collectRefs(issue.getId(), AuditIssueRefTypes.LOCATION, allRefs));
    map.put(
        AuditIssueMapKeys.EVIDENCE,
        collectRefs(issue.getId(), AuditIssueRefTypes.EVIDENCE, allRefs));
    if (issue.getRepairable() != null) {
      map.put(AuditIssueMapKeys.REPAIRABLE, issue.getRepairable());
    }
    if (issue.getRequiresHumanConfirmation() != null) {
      map.put(AuditIssueMapKeys.REQUIRES_HUMAN_CONFIRMATION, issue.getRequiresHumanConfirmation());
    }
    return map;
  }

  /**
   * 从已加载的 refs 中收集某一问题某一类型的内容列表。
   *
   * @param issueId 问题 id
   * @param refType location / evidence
   * @param allRefs 全部 refs
   * @return 反序列化后的元素列表
   */
  private List<Object> collectRefs(
      String issueId, String refType, List<AuditIssueRefEntity> allRefs) {
    List<Object> out = new ArrayList<>();
    if (CollUtil.isEmpty(allRefs)) {
      return out;
    }
    for (AuditIssueRefEntity row : allRefs) {
      if (row != null
          && issueId.equals(row.getIssueId())
          && refType.equals(row.getRefType())) {
        out.add(parseRefContent(row.getContent()));
      }
    }
    return out;
  }

  /**
   * 写入一类 refs。
   *
   * @param issueId 问题 id
   * @param refType location / evidence
   * @param raw     列表或 null
   */
  private void writeRefs(String issueId, String refType, Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return;
    }
    int seq = 0;
    for (Object element : list) {
      if (element == null) {
        continue;
      }
      String content;
      if (element instanceof Map<?, ?> || element instanceof List<?>) {
        content = writeJson(element);
      } else if (element instanceof String text) {
        content = text;
      } else {
        content = writeJson(element);
      }
      refMapper.insert(AuditIssueRefEntity.builder()
          .id(IdsUtil.newId())
          .issueId(issueId)
          .refType(refType)
          .seq(seq++)
          .content(content)
          .build());
    }
  }

  /**
   * 解析 ref content：优先 JSON 对象/数组，否则原字符串。
   *
   * @param content 存库文本
   * @return 反序列化对象或原串
   */
  private Object parseRefContent(String content) {
    if (StrUtil.isBlank(content)) {
      return content;
    }
    String trimmed = content.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        return objectMapper.readValue(content, new TypeReference<Object>() {});
      } catch (Exception ignored) {
        return content;
      }
    }
    return content;
  }

  /**
   * results 字段规范化为 Map 列表。
   *
   * @param raw results 原始值
   * @return Map 列表
   */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asResultList(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object row : list) {
      if (row instanceof Map<?, ?> map) {
        out.add(new LinkedHashMap<>((Map<String, Object>) map));
      }
    }
    return out;
  }

  /**
   * 对象转字符串；null → null。
   *
   * @param value 任意值
   * @return 字符串或 null
   */
  private static String stringOrNull(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 对象转字符串；null → 空串。
   *
   * @param value 任意值
   * @return 字符串
   */
  private static String stringOf(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Jackson 序列化。
   *
   * @param value 任意对象
   * @return JSON 文本
   */
  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
