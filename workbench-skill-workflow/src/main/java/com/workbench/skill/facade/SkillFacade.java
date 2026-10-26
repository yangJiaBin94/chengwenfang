package com.workbench.skill.facade;


import com.workbench.common.PageQuery;
import com.workbench.common.PageResult;
import com.workbench.skill.facade.dto.request.SkillSaveRequest;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.dto.view.SkillView;
import com.workbench.skill.facade.dto.view.SkillWorkflowNodeView;
import java.util.List;

/** Skill 管理门面：CRUD、节点目录与按绑定组合提示词。 */
public interface SkillFacade {
  /** 列出全部 Skill。 */
  List<SkillView> listSkills();

  /**
   * 分页列出 Skill，支持名称/简介/指令关键字筛选。
   *
   * @param query 分页与关键字；null 时按默认页码页大小
   * @return 当前页数据与筛选总数
   */
  PageResult<SkillView> pageSkills(PageQuery query);
  /** 按名称/场景幂等写入 Skill。 */
  SkillView upsertSkill(SkillSaveRequest body);

  /** 创建 Skill。 */
  SkillView createSkill(SkillSaveRequest body);

  /** 按 ID 更新 Skill。 */
  SkillView updateSkillById(String id, SkillSaveRequest body);

  /** 删除（或软删）Skill。 */
  void deleteSkill(String id);

  /** 列出可绑定的工作流节点目录。 */
  List<SkillWorkflowNodeView> listWorkflowNodes();

  /** 解析某工作流类型下节点的默认提示词。 */
  String resolveNodePrompt(String workflowType, String nodeKey);

  /**
   * 按节点 skillBindings（skillId + priority）组合 system/task 提示词。
   * 读取当前 skills 表指令（库内不做 skill 版本表）。
   */
  String composePromptFromBindings(List<SkillPromptBindingView> bindings);
}
