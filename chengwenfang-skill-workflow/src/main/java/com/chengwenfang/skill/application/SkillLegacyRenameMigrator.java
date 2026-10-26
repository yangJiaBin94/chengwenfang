package com.chengwenfang.skill.application;

import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.CourseSkillNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把旧 Skill 名「工作台规则理解」迁到「成文坊规则理解」，早于基础配置体检。
 */
@Slf4j
@Component
@Order(70)
@Profile("!bootstrap-seed")
@RequiredArgsConstructor
public class SkillLegacyRenameMigrator implements ApplicationRunner {
  /** Skill 门面。 */
  private final SkillFacade skills;

  /**
   * 执行旧名迁移（幂等）。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    // 幂等：无旧名则直接返回
    if (skills.migrateLegacyRulesSkillName()) {
      log.info(
          "Skill 名称已同步：{} → {}",
          CourseSkillNames.LEGACY_RULES,
          CourseSkillNames.CHENGWENFANG_RULES);
    }
  }
}
