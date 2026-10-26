package com.chengwenfang.app.adapter.web.ai;

import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.request.AiSettingsUpdateRequest;
import com.chengwenfang.ai.facade.dto.view.AiSettingsView;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.view.ConnectionTestResult;
import com.chengwenfang.ai.facade.dto.request.ImageGenerateRequest;
import com.chengwenfang.ai.facade.dto.view.ImageGenerateResult;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.ApiResult;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 提供商与模型设置相关 HTTP 接口。 */
@Tag(name = "AI 设置", description = "文本/图片/多模态模型配置与调试")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {
  /** 文本侧门面：设置读写、连通性与补全。 */
  private final AiTextFacade textFacade;
  /** 图像侧门面：生图调试。 */
  private final AiImageFacade imageFacade;
  /** 多模态侧门面：看图补全。 */
  private final AiVisionFacade visionFacade;

  /**
   * 获取文本/图片 AI 设置（不含明文 apiKey）。
   *
   * @return 脱敏后的 settings 视图
   */
  @Operation(summary = "获取 AI 设置")
  @GetMapping("/settings/ai")
  public ApiResult<AiSettingsView> getSettings() {
    return ApiResult.ok(textFacade.getSettings());
  }

  /**
   * 更新 AI 提供商设置；空白 apiKey 表示保留原值。
   *
   * @param body 更新请求；可为 null 表示空请求
   * @return 更新后的脱敏 settings
   */
  @Operation(summary = "更新 AI 设置")
  @PutMapping("/settings/ai")
  public ApiResult<AiSettingsView> putSettings(
      @Valid @RequestBody(required = false) AiSettingsUpdateRequest body) {
    return ApiResult.ok(textFacade.updateSettings(body));
  }

  /**
   * 测试指定 kind（text/image/vision）的上游连通性。
   *
   * @param kind 提供商类型：{@code text}、{@code image} 或 {@code vision}
   * @return 连通性测试结果
   */
  @Operation(summary = "测试 AI 连通性")
  @PostMapping("/settings/ai/{kind}/test")
  public ApiResult<ConnectionTestResult> test(
      @Parameter(description = "text、image 或 vision") @PathVariable String kind) {
    return ApiResult.ok(textFacade.testConnection(kind));
  }

  /**
   * 文本补全调试接口。
   *
   * @param body 补全请求（prompt、scene 等）
   * @return 模型输出与所用 model/protocol
   */
  @Operation(summary = "文本补全调试")
  @PostMapping("/ai/text")
  public ApiResult<TextCompleteResult> aiText(@Valid @RequestBody TextCompleteRequest body) {
    return ApiResult.ok(textFacade.completeText(body));
  }

  /**
   * 图片生成调试接口。
   *
   * @param body 生图请求（prompt、size、dryRun 等）
   * @return 生图结果（含 b64/url 或 dryRun 标记）
   */
  @Operation(summary = "图片生成调试")
  @PostMapping("/ai/image")
  public ApiResult<ImageGenerateResult> aiImage(@Valid @RequestBody ImageGenerateRequest body) {
    return ApiResult.ok(imageFacade.generate(body));
  }

  /**
   * 多模态看图补全调试接口。
   *
   * @param body 含提示词与图片
   * @return 模型输出与所用 model/protocol
   */
  @Operation(summary = "多模态看图调试")
  @PostMapping("/ai/vision")
  public ApiResult<VisionCompleteResult> aiVision(@Valid @RequestBody VisionCompleteRequest body) {
    return ApiResult.ok(visionFacade.complete(body));
  }
}
