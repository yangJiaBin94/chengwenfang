package com.workbench.quality.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.IdsUtil;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.quality.domain.entity.HotArticleRepairCandidateEntity;
import com.workbench.quality.domain.entity.RepairCandidateIssueEntity;
import com.workbench.quality.infrastructure.mapper.RepairCandidateIssueMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修复候选：正文存文件；issueIds ↔ {@code repair_candidate_issues}；issueIdsJson 按 Jackson 还原。
 */
@Component
@RequiredArgsConstructor
public class RepairCandidateStore {
  private final RepairCandidateIssueMapper issueMapper;
  private final FileFacade files;
  private final ObjectMapper objectMapper;

  /**
   * 写入候选正文文件并回填 {@code bodyFileId}。
   *
   * @param candidate 候选实体（须有 id、outputId）
   * @param content   正文；null 按空串
   */
  public void writeBody(HotArticleRepairCandidateEntity candidate, String content) {
    if (candidate == null || StrUtil.isBlank(candidate.getId())) {
      return;
    }
    String outputId = StrUtil.blankToDefault(candidate.getOutputId(), "unknown");
    String objectKey = "repairs/" + outputId + "/" + candidate.getId() + ".md";
    StoredFileView stored = files.writeText(objectKey, candidate.getId() + ".md", content);
    candidate.setBodyFileId(stored.getId());
  }

  /**
   * 读取候选正文。
   *
   * @param candidate 候选实体
   * @return 正文；无文件时为空串
   */
  public String readBody(HotArticleRepairCandidateEntity candidate) {
    if (candidate == null || StrUtil.isBlank(candidate.getBodyFileId())) {
      return "";
    }
    return files.readText(candidate.getBodyFileId());
  }

  /**
   * 全量替换候选关联问题；空列表时 {@code repairAll=true}。
   *
   * @param candidateId 候选 id
   * @param issueIds    问题 id 列表（任意可转字符串元素）；null/空 → repair_all
   * @return 是否 repair_all
   */
  @Transactional
  public boolean replaceIssues(String candidateId, Object issueIds) {
    if (StrUtil.isBlank(candidateId)) {
      return true;
    }
    issueMapper.delete(new LambdaQueryWrapper<RepairCandidateIssueEntity>()
        .eq(RepairCandidateIssueEntity::getCandidateId, candidateId));
    List<String> refs = normalizeIssueIds(issueIds);
    if (refs.isEmpty()) {
      return true;
    }
    int seq = 0;
    for (String ref : refs) {
      issueMapper.insert(RepairCandidateIssueEntity.builder()
          .id(IdsUtil.newId())
          .candidateId(candidateId)
          .seq(seq++)
          .issueRef(ref)
          .build());
    }
    return false;
  }

  /**
   * 按 seq 读取问题 id 列表。
   *
   * @param candidateId 候选 id
   * @return 列表；永不为 null
   */
  public List<String> readIssueIds(String candidateId) {
    if (StrUtil.isBlank(candidateId)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (RepairCandidateIssueEntity row : issueMapper.selectList(
        new LambdaQueryWrapper<RepairCandidateIssueEntity>()
            .eq(RepairCandidateIssueEntity::getCandidateId, candidateId)
            .orderByAsc(RepairCandidateIssueEntity::getSeq))) {
      if (row != null && StrUtil.isNotBlank(row.getIssueRef())) {
        out.add(row.getIssueRef());
      }
    }
    return out;
  }

  /**
   * 序列化 issueIdsJson，与 {@code ObjectMapper.writeValueAsString(list)} 一致（空为 {@code []}）。
   *
   * @param candidateId 候选 id
   * @return JSON 数组字符串
   */
  public String issueIdsJson(String candidateId) {
    List<String> ids = readIssueIds(candidateId);
    try {
      return objectMapper.writeValueAsString(ids);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  /**
   * 将任意 issueIds 入参规范为字符串列表。
   *
   * @param issueIds List / 单值 / null
   * @return 字符串列表
   */
  private static List<String> normalizeIssueIds(Object issueIds) {
    if (issueIds == null) {
      return List.of();
    }
    if (issueIds instanceof List<?> list) {
      if (CollUtil.isEmpty(list)) {
        return List.of();
      }
      List<String> out = new ArrayList<>();
      for (Object element : list) {
        if (element != null) {
          out.add(String.valueOf(element));
        }
      }
      return out;
    }
    return List.of(String.valueOf(issueIds));
  }
}
