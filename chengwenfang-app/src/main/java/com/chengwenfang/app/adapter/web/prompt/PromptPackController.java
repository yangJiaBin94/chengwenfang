package com.chengwenfang.app.adapter.web.prompt;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.dto.request.PromptPackSaveRequest;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackWorkflowNodeView;
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
 * 提示词包 设置相关 HTTP 接口。
 * <p>仅做入参出参与委托 {@link PromptPackFacade}，不写业务规则。
 */
@Tag(name = "PromptPack", description = "提示词包列表、创建与工作流节点绑定")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromptPackController {
  /** 提示词包 领域门面。 */
  private final PromptPackFacade promptPacks;

  /**
   * 列出全部提示词包。
   *
   * @return 统一包装的 提示词包视图列表
   */
  @GetMapping("/settings/prompt-packs")
  @Operation(summary = "列出提示词包")
  public ApiResult<List<PromptPackView>> listPromptPacks() {
    // 委托门面查询未删除提示词包
    return ApiResult.ok(promptPacks.listPromptPacks());
  }

  /**
   * 分页列出 PromptPack（含名称/简介/指令关键字筛选）。
   *
   * @param page     页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword  名称、简介或指令关键字；可空
   * @return 统一包装的分页结果
   */
  @GetMapping("/settings/prompt-packs/page")
  @Operation(summary = "分页列出提示词包")
  public ApiResult<PageResult<PromptPackView>> pagePromptPacks(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String keyword) {
    PageQuery query = PageQuery.builder().page(page).pageSize(pageSize).keyword(keyword).build();
    return ApiResult.ok(promptPacks.pagePromptPacks(query));
  }

  /**
   * 列出可绑定的工作流节点目录。
   *
   * @return 统一包装的节点目录列表
   */
  @GetMapping("/settings/prompt-packs/workflow-nodes")
  @Operation(summary = "列出可绑定工作流节点")
  public ApiResult<List<PromptPackWorkflowNodeView>> listPromptPackWorkflowNodes() {
    // 供前端绑定下拉使用
    return ApiResult.ok(promptPacks.listWorkflowNodes());
  }

  /**
   * 创建提示词包。
   *
   * @param body 创建请求体；可为空，门面按空对象处理缺省字段
   * @return 新建 提示词包视图
   */
  @PostMapping("/settings/prompt-packs")
  @Operation(summary = "创建提示词包")
  public ApiResult<PromptPackView> createPromptPack(@Valid @RequestBody(required = false) PromptPackSaveRequest body) {
    // 强制走创建路径（不按 id 幂等）
    return ApiResult.ok(promptPacks.createPromptPack(body));
  }

  /**
   * 按路径 id 更新提示词包。
   *
   * @param id   提示词包 主键
   * @param body 更新请求体；可为空
   * @return 更新后的 提示词包视图
   */
  @PutMapping("/settings/prompt-packs/{id}")
  @Operation(summary = "更新提示词包")
  public ApiResult<PromptPackView> updatePromptPack(
      @PathVariable String id, @Valid @RequestBody(required = false) PromptPackSaveRequest body) {
    // 路径 id 为准，委托门面更新
    return ApiResult.ok(promptPacks.updatePromptPackById(id, body));
  }

  /**
   * 软删除提示词包。
   *
   * @param id 提示词包 主键
   * @return 空成功结果
   */
  @DeleteMapping("/settings/prompt-packs/{id}")
  @Operation(summary = "删除提示词包")
  public ApiResult<Void> deletePromptPack(@PathVariable String id) {
    promptPacks.deletePromptPack(id);
    return ApiResult.ok();
  }

  /**
   * 幂等写入 PromptPack（有 id 且存在则更新，否则创建）。
   *
   * @param body 保存请求体
   * @return 写入后的 提示词包视图
   */
  @PutMapping("/settings/prompt-packs")
  @Operation(summary = "幂等写入 提示词包")
  public ApiResult<PromptPackView> upsertPromptPack(@Valid @RequestBody PromptPackSaveRequest body) {
    // 与 POST 创建区分：走 upsert 语义
    return ApiResult.ok(promptPacks.upsertPromptPack(body));
  }
}
