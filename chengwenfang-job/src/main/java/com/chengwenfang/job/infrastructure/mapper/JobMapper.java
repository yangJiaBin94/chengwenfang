package com.chengwenfang.job.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.job.domain.entity.JobEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** jobs 表 Mapper（含认领与续租 SQL）。 */
@Mapper
public interface JobMapper extends BaseMapper<JobEntity> {
  @Select("""
      SELECT id FROM jobs
      WHERE status = 'pending'
         OR (status = 'running' AND lease_expires_at IS NOT NULL AND lease_expires_at < UTC_TIMESTAMP(3))
      ORDER BY created_at ASC
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """)
  String selectClaimCandidateId();

  /**
   * 认领任务为 running。
   * <p>{@code startedAt} 须由应用侧传入 {@link LocalDateTime#now()}，
   * 与 created_at/finished_at 同一时钟；勿用 UTC_TIMESTAMP，否则会与 JVM 本地时间差 8 小时导致前端耗时虚高。
   *
   * @param id           任务 ID
   * @param leaseSeconds 租约秒数
   * @param startedAt    首次开始时间（仅 started_at 为空时写入）
   * @return 更新行数
   */
  @Update("""
      UPDATE jobs
      SET status = 'running',
          started_at = COALESCE(started_at, #{startedAt}),
          lease_expires_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND),
          current_step = COALESCE(NULLIF(current_step, ''), 'claimed')
      WHERE id = #{id}
        AND (status = 'pending'
             OR (status = 'running' AND lease_expires_at IS NOT NULL AND lease_expires_at < UTC_TIMESTAMP(3)))
      """)
  int claimById(
      @Param("id") String id,
      @Param("leaseSeconds") int leaseSeconds,
      @Param("startedAt") LocalDateTime startedAt);

  @Update("""
      UPDATE jobs
      SET lease_expires_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL #{leaseSeconds} SECOND)
      WHERE id = #{id} AND status = 'running'
      """)
  int renewLease(@Param("id") String id, @Param("leaseSeconds") int leaseSeconds);
}
