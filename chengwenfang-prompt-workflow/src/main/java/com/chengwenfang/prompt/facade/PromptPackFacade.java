package com.chengwenfang.prompt.facade;


import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.prompt.facade.dto.request.PromptPackSaveRequest;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackWorkflowNodeView;
import java.util.List;

/** 提示词包管理门面：CRUD、节点目录与按绑定组合提示词。 */
public interface PromptPackFacade {
  /** 列出全部提示词包。 */
  List<PromptPackView> listPromptPacks();

  /**
   * 分页列出提示词包，支持名称/简介/指令关键字筛选。
   *
   * @param query 分页与关键字；null 时按默认页码页大小
   * @return 当前页数据与筛选总数
   */
  PageResult<PromptPackView> pagePromptPacks(PageQuery query);
  /** 按名称/场景幂等写入 提示词包。 */
  PromptPackView upsertPromptPack(PromptPackSaveRequest body);

  /** 创建提示词包。 */
  PromptPackView createPromptPack(PromptPackSaveRequest body);

  /** 按 ID 更新提示词包。 */
  PromptPackView updatePromptPackById(String id, PromptPackSaveRequest body);

  /** 删除（或软删）提示词包。 */
  void deletePromptPack(String id);

  /**
   * 将库内旧名「工作台规则理解」迁到「成文坊规则理解」。
   * <p>新名已存在则软删旧名行；仅旧名存在则改名，并顺带把指令正文里的「工作台」换成「成文坊」。
   *
   * @return 是否写入了库（改名或软删）
   */
  boolean migrateLegacyRulesPromptPackName();

  /** 列出可绑定的工作流节点目录。 */
  List<PromptPackWorkflowNodeView> listWorkflowNodes();

  /** 解析某工作流类型下节点的默认提示词。 */
  String resolveNodePrompt(String workflowType, String nodeKey);

  /**
   * 按节点 promptPackBindings（promptPackId + priority）组合 system/task 提示词。
   * 读取当前 prompt_packs 表指令（库内不做提示词包版本表）。
   */
  String composePromptFromBindings(List<PromptPackPromptBindingView> bindings);
}
