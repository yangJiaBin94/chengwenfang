package com.workbench.content.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.RepairRunCodeEntity;
import com.workbench.content.facade.constant.RepairRunCodeTypes;
import com.workbench.content.infrastructure.mapper.RepairRunCodeMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修复运行问题码：resolved / added ↔ {@code repair_run_codes}。
 */
@Component
@RequiredArgsConstructor
public class RepairRunCodeStore {
  private final RepairRunCodeMapper codeMapper;

  /**
   * 全量替换某次修复运行的 resolved / added 码。
   *
   * @param repairRunId   修复运行 id
   * @param resolvedCodes 已消除码
   * @param addedCodes    新引入码
   */
  @Transactional
  public void replace(String repairRunId, List<String> resolvedCodes, List<String> addedCodes) {
    if (StrUtil.isBlank(repairRunId)) {
      return;
    }
    codeMapper.delete(new LambdaQueryWrapper<RepairRunCodeEntity>()
        .eq(RepairRunCodeEntity::getRepairRunId, repairRunId));
    writeCodes(repairRunId, RepairRunCodeTypes.RESOLVED, resolvedCodes);
    writeCodes(repairRunId, RepairRunCodeTypes.ADDED, addedCodes);
  }

  /**
   * 读取已消除的问题码（按 seq）。
   *
   * @param repairRunId 修复运行 id
   * @return 码列表
   */
  public List<String> readResolved(String repairRunId) {
    return readCodes(repairRunId, RepairRunCodeTypes.RESOLVED);
  }

  /**
   * 读取新引入的问题码（按 seq）。
   *
   * @param repairRunId 修复运行 id
   * @return 码列表
   */
  public List<String> readAdded(String repairRunId) {
    return readCodes(repairRunId, RepairRunCodeTypes.ADDED);
  }

  /**
   * 写入一类码。
   *
   * @param repairRunId 运行 id
   * @param codeType    resolved / added
   * @param codes       码列表
   */
  private void writeCodes(String repairRunId, String codeType, List<String> codes) {
    if (CollUtil.isEmpty(codes)) {
      return;
    }
    int seq = 0;
    for (String code : codes) {
      if (StrUtil.isBlank(code)) {
        continue;
      }
      codeMapper.insert(RepairRunCodeEntity.builder()
          .id(IdsUtil.newId())
          .repairRunId(repairRunId)
          .codeType(codeType)
          .seq(seq++)
          .code(code)
          .build());
    }
  }

  /**
   * 按类型读取码列表。
   *
   * @param repairRunId 运行 id
   * @param codeType    resolved / added
   * @return 码列表
   */
  private List<String> readCodes(String repairRunId, String codeType) {
    if (StrUtil.isBlank(repairRunId)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (RepairRunCodeEntity row : codeMapper.selectList(
        new LambdaQueryWrapper<RepairRunCodeEntity>()
            .eq(RepairRunCodeEntity::getRepairRunId, repairRunId)
            .eq(RepairRunCodeEntity::getCodeType, codeType)
            .orderByAsc(RepairRunCodeEntity::getSeq))) {
      if (row != null && StrUtil.isNotBlank(row.getCode())) {
        out.add(row.getCode());
      }
    }
    return out;
  }
}
