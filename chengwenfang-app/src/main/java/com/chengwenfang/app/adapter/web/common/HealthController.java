package com.chengwenfang.app.adapter.web.common;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康检查接口。 */
@Tag(name = "系统", description = "健康检查与运行信息")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {
  /** 应用名、版本与环境等运行配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 返回服务健康状态与基本运行信息。
   *
   * @return 含 service、version、environment 的健康视图
   */
  @Operation(summary = "健康检查")
  @GetMapping("/health")
  public ApiResult<HealthView> health() {
    return ApiResult.ok(HealthView.builder()
        .service(properties.getAppName())
        .version(properties.getAppVersion())
        .environment(properties.getEnvironment())
        .build());
  }
}
