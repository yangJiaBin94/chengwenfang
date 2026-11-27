package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.domain.entity.KnowledgeIndexStateEntity;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeIndexStateMapper;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库索引活跃 / 写入版本读写（每用户蓝绿读指针与重建窗）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIndexVersionService {

  /** 默认版本。 */
  public static final String DEFAULT_VERSION = "v1";
  /** 解析 {@code vN} 数字后缀。 */
  private static final Pattern VERSION_NUMBER = Pattern.compile("^v(\\d+)$", Pattern.CASE_INSENSITIVE);

  /** 状态表 Mapper。 */
  private final KnowledgeIndexStateMapper indexStateMapper;
  /** 所属用户范围。 */
  private final OwnerScope ownerScope;

  /**
   * 当前读路径活跃版本（当前登录 / OwnerContext 用户）。
   *
   * @return 版本号
   */
  public String activeVersion() {
    KnowledgeIndexStateEntity row = loadOrNull(resolveOwnerId());
    if (row == null || StrUtil.isBlank(row.getActiveVersion())) {
      return DEFAULT_VERSION;
    }
    return row.getActiveVersion().trim();
  }

  /**
   * 全量重建进行中的写入版本；无则 null。
   *
   * @return writing 版本或 null
   */
  public String writingVersion() {
    KnowledgeIndexStateEntity row = loadOrNull(resolveOwnerId());
    if (row == null || StrUtil.isBlank(row.getWritingVersion())) {
      return null;
    }
    return row.getWritingVersion().trim();
  }

  /**
   * 由当前活跃版本推导下一写入版本（不落库）。
   *
   * @param active 活跃版本
   * @return 下一版本，如 v1→v2
   */
  public String nextVersionOf(String active) {
    String base = StrUtil.blankToDefault(active, DEFAULT_VERSION).trim();
    Matcher matcher = VERSION_NUMBER.matcher(base);
    if (matcher.matches()) {
      int number = Integer.parseInt(matcher.group(1));
      return "v" + (number + 1);
    }
    return base + "_next";
  }

  /**
   * 开始全量蓝绿写：落库 {@code writing_version}（已有则复用，便于中断续跑）。
   *
   * @return 写入版本
   */
  @Transactional
  public String beginNextWritingVersion() {
    String existing = writingVersion();
    if (StrUtil.isNotBlank(existing)) {
      return existing;
    }
    String writing = nextVersionOf(activeVersion());
    upsertWritingVersion(writing);
    return writing;
  }

  /**
   * 切换读路径到新版本并清空 writing 标记（金标门禁通过后调用）。
   *
   * @param version 新活跃版本
   */
  @Transactional
  public void promoteActiveVersion(String version) {
    if (StrUtil.isBlank(version)) {
      throw new IllegalArgumentException("索引版本不能为空");
    }
    String ownerId = resolveOwnerId();
    String next = version.trim();
    KnowledgeIndexStateEntity row = loadOrNull(ownerId);
    if (row == null) {
      indexStateMapper.insert(KnowledgeIndexStateEntity.builder()
          .ownerUserId(ownerId)
          .activeVersion(next)
          .writingVersion(null)
          .updatedAt(LocalDateTime.now())
          .build());
      return;
    }
    row.setActiveVersion(next);
    row.setWritingVersion(null);
    row.setUpdatedAt(LocalDateTime.now());
    indexStateMapper.updateById(row);
  }

  /**
   * 解析当前必须存在的所属用户（登录或 OwnerContext）。
   *
   * @return 用户 ID
   */
  public String resolveOwnerId() {
    return ownerScope.requireOwnerUserIdForWrite().get();
  }

  /**
   * 按所属用户加载状态行。
   *
   * @param ownerId 用户 ID
   * @return 行或 null
   */
  private KnowledgeIndexStateEntity loadOrNull(String ownerId) {
    return indexStateMapper.selectById(ownerId);
  }

  /**
   * 写入或更新 writing_version。
   *
   * @param writing 写入版本
   */
  private void upsertWritingVersion(String writing) {
    String ownerId = resolveOwnerId();
    LocalDateTime now = LocalDateTime.now();
    KnowledgeIndexStateEntity row = loadOrNull(ownerId);
    if (row == null) {
      indexStateMapper.insert(KnowledgeIndexStateEntity.builder()
          .ownerUserId(ownerId)
          .activeVersion(DEFAULT_VERSION)
          .writingVersion(writing)
          .updatedAt(now)
          .build());
      return;
    }
    row.setWritingVersion(writing);
    row.setUpdatedAt(now);
    indexStateMapper.updateById(row);
  }
}
