package com.chengwenfang.app.adapter.web.system;

import com.chengwenfang.app.bootstrap.BuiltinReadyQuery;
import com.chengwenfang.app.bootstrap.BuiltinReadyView;
import com.chengwenfang.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统只读接口：内置配置就绪等。
 */
@Tag(name = "系统", description = "健康检查与系统就绪")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {
  /** 内置配置就绪查询。 */
  private final BuiltinReadyQuery builtinReadyQuery;

  /**
   * 返回内置 Skill / 工作流是否就绪及问题列表。
   *
   * @return 就绪视图
   */
  @Operation(summary = "内置配置就绪体检")
  @GetMapping("/builtin-ready")
  public ApiResult<BuiltinReadyView> builtinReady() {
    return ApiResult.ok(builtinReadyQuery.inspect());
  }
}
