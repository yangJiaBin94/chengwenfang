package com.workbench.app.adapter.web.skill;

import com.workbench.common.ApiResult;
import com.workbench.common.PageQuery;
import com.workbench.common.PageResult;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.dto.request.SkillSaveRequest;
import com.workbench.skill.facade.dto.view.SkillView;
import com.workbench.skill.facade.dto.view.SkillWorkflowNodeView;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 设置相关 HTTP 接口。
 * <p>仅做入参出参与委托 {@link SkillFacade}，不写业务规则。
 */
@Tag(name = "Skill", description = "Skill 列表、创建与工作流节点绑定")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SkillController {
  /** Skill 领域门面。 */
  private final SkillFacade skills;

  /**
   * 列出全部 Skill。
   *
   * @return 统一包装的 Skill 视图列表
   */
  @GetMapping("/settings/skills")
  @Operation(summary = "列出 Skill")
  public ApiResult<List<SkillView>> listSkills() {
    // 委托门面查询未删除 Skill
    return ApiResult.ok(skills.listSkills());
  }

  /**
   * 分页列出 Skill（含名称/简介/指令关键字筛选）。
   *
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword  名称、简介或指令关键字；可空
   * @return 统一包装的分页结果
   */
  @GetMapping("/settings/skills/page")
  @Operation(summary = "分页列出 Skill")
  public ApiResult<PageResult<SkillView>> pageSkills(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String keyword) {
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).keyword(keyword).build();
    return ApiResult.ok(skills.pageSkills(query));
  }

  /**
   * 列出可绑定的工作流节点目录。
   *
   * @return 统一包装的节点目录列表
   */
  @GetMapping("/settings/skills/workflow-nodes")
  @Operation(summary = "列出可绑定工作流节点")
  public ApiResult<List<SkillWorkflowNodeView>> listSkillWorkflowNodes() {
    // 供前端绑定下拉使用
    return ApiResult.ok(skills.listWorkflowNodes());
  }

  /**
   * 创建 Skill。
   *
   * @param body 创建请求体；可为空，门面按空对象处理缺省字段
   * @return 新建 Skill 视图
   */
  @PostMapping("/settings/skills")
  @Operation(summary = "创建 Skill")
  public ApiResult<SkillView> createSkill(@Valid @RequestBody(required = false) SkillSaveRequest body) {
    // 强制走创建路径（不按 id 幂等）
    return ApiResult.ok(skills.createSkill(body));
  }

  /**
   * 按路径 id 更新 Skill。
   *
   * @param id   Skill 主键
   * @param body 更新请求体；可为空
   * @return 更新后的 Skill 视图
   */
  @PutMapping("/settings/skills/{id}")
  @Operation(summary = "更新 Skill")
  public ApiResult<SkillView> updateSkill(
      @PathVariable String id, @Valid @RequestBody(required = false) SkillSaveRequest body) {
    // 路径 id 为准，委托门面更新
    return ApiResult.ok(skills.updateSkillById(id, body));
  }

  /**
   * 软删除 Skill。
   *
   * @param id Skill 主键
   * @return 空成功结果
   */
  @DeleteMapping("/settings/skills/{id}")
  @Operation(summary = "删除 Skill")
  public ApiResult<Void> deleteSkill(@PathVariable String id) {
    skills.deleteSkill(id);
    return ApiResult.ok();
  }

  /**
   * 幂等写入 Skill（有 id 且存在则更新，否则创建）。
   *
   * @param body 保存请求体
   * @return 写入后的 Skill 视图
   */
  @PutMapping("/settings/skills")
  @Operation(summary = "幂等写入 Skill")
  public ApiResult<SkillView> upsertSkill(@Valid @RequestBody SkillSaveRequest body) {
    // 与 POST 创建区分：走 upsert 语义
    return ApiResult.ok(skills.upsertSkill(body));
  }
}
