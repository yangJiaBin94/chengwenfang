package com.chengwenfang.ai.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.ImageGenerateRequest;
import com.chengwenfang.ai.facade.dto.view.ImageGenerateResult;
import com.chengwenfang.ai.facade.enums.AiProviderKind;
import com.chengwenfang.ai.facade.enums.ModelCallStatus;
import com.chengwenfang.ai.infrastructure.mapper.AiProviderConfigMapper;
import com.chengwenfang.ai.infrastructure.openai.OpenAiImageGenerationRequest;
import com.chengwenfang.ai.infrastructure.openai.OpenAiImageGenerationResponse;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 图像模型客户端：按 image 配置调用 OpenAI 兼容生图接口，并写入 model_calls。
 */
@Component
@RequiredArgsConstructor
public class AiImageClient {

  /** 提供商配置表读写。 */
  private final AiProviderConfigMapper configMapper;
  /** 成文坊配置：图像上游超时等。 */
  private final ChengwenfangProperties properties;
  /** 密钥列加解密。 */
  private final SecretCipher secretCipher;
  /** 模型调用审计写入。 */
  private final ModelCallRecorder modelCallRecorder;

  /**
   * 按库中 image 配置生成图片。
   *
   * @param request 生图请求；{@code prompt} 提示词，{@code size} 尺寸（默认 1024x1024），{@code dryRun} 是否空跑
   * @return 成功时含 model 与 b64Json/url（视上游返回）；dryRun 时仅 ok/model
   * @throws BusinessException 未配置 apiKey 或上游 HTTP 非 2xx
   */
  public ImageGenerateResult generate(ImageGenerateRequest request) {
    return generate(requireKind(AiProviderKind.IMAGE), request);
  }

  /**
   * 使用指定配置生成图片（连通性测试可传表单草稿，不读库）。
   *
   * @param cfg     提供商配置（须含 apiKey、baseUrl、model）
   * @param request 生图请求；{@code dryRun=true} 时不请求上游
   * @return 生图结果
   * @throws BusinessException 未配置 apiKey 或上游 HTTP 非 2xx
   */
  ImageGenerateResult generate(AiProviderConfigEntity cfg, ImageGenerateRequest request) {
    ImageGenerateRequest req = request == null ? new ImageGenerateRequest() : request;
    // 连通性测试等场景：不打上游，只确认配置存在
    if (Boolean.TRUE.equals(req.getDryRun())) {
      return ImageGenerateResult.builder()
          .ok(true)
          .dryRun(true)
          .model(cfg.getModel())
          .build();
    }
    // 真实生图必须有密钥
    String apiKey = decryptApiKey(cfg);
    if (StrUtil.isBlank(apiKey)) {
      throw new BusinessException(ErrorCodes.AI_KEY_MISSING, "未配置图像模型 API Key", 400);
    }
    long start = System.currentTimeMillis();
    String prompt = req.getPrompt() == null ? "" : req.getPrompt();
    // 未指定尺寸时用常见默认值
    String size = StrUtil.blankToDefault(req.getSize(), "1024x1024");
    // 拼 OpenAI 兼容生图路径；模型与质量一律取自传入配置，不写死模型名
    String url = trimSlash(cfg.getBaseUrl()) + "/images/generations";
    var bodyBuilder = OpenAiImageGenerationRequest.builder()
        .model(cfg.getModel())
        .prompt(prompt)
        .imageCount(1)
        .size(size);
    if (StrUtil.isNotBlank(cfg.getImageQuality())) {
      bodyBuilder.quality(cfg.getImageQuality().strip());
    }
    OpenAiImageGenerationRequest body = bodyBuilder.build();
    try (HttpResponse response = HttpRequest.post(url)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(resolveTimeoutSeconds(AiProviderKind.IMAGE) * 1000)
        .body(JSONUtil.toJsonStr(body))
        .execute()) {
      // 上游非成功：记失败审计并抛业务异常
      if (!response.isOk()) {
        modelCallRecorder.recordCall(
            cfg,
            ModelCallScenes.IMAGE,
            ModelCallStatus.FAILED,
            start,
            prompt.length(),
            0,
            "upstream_" + response.getStatus());
        throw new BusinessException(ErrorCodes.IMAGE_FAILED, "图像生成上游失败：" + response.body(), 502);
      }
      // 解析响应，优先取首张图的 base64 或 url
      OpenAiImageGenerationResponse parsed =
          JSONUtil.toBean(response.body(), OpenAiImageGenerationResponse.class);
      String b64 = null;
      String imageUrl = null;
      if (parsed != null && CollUtil.isNotEmpty(parsed.getImages())) {
        OpenAiImageGenerationResponse.ImageData first = parsed.getImages().get(0);
        b64 = first.getB64Json();
        imageUrl = first.getUrl();
      }
      modelCallRecorder.recordCall(
          cfg, ModelCallScenes.IMAGE, ModelCallStatus.SUCCEEDED, start, prompt.length(), 0, null);
      return ImageGenerateResult.builder()
          .model(cfg.getModel())
          .b64Json(b64)
          .url(imageUrl)
          .build();
    }
  }

  /**
   * 按 kind 加载唯一配置行。
   *
   * @param kind 提供商类型枚举
   * @return 对应 {@link AiProviderConfigEntity}
   * @throws BusinessException 库中无该 kind 时 404
   */
  private AiProviderConfigEntity requireKind(AiProviderKind kind) {
    AiProviderConfigEntity cfg = configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfigEntity>()
        .eq(AiProviderConfigEntity::getKind, kind.getValue())
        .last("LIMIT 1"));
    if (cfg == null) {
      throw new BusinessException(
          ErrorCodes.AI_CONFIG_MISSING,
          "缺少 AI 配置 kind=" + kind.getValue(),
          404);
    }
    return cfg;
  }

  /**
   * 解密配置中的 API Key；空白密文返回空串。
   *
   * @param cfg 提供商配置
   * @return 明文密钥
   */
  private String decryptApiKey(AiProviderConfigEntity cfg) {
    if (cfg == null || StrUtil.isBlank(cfg.getApiKeyCiphertext())) {
      return "";
    }
    return secretCipher.decrypt(cfg.getApiKeyCiphertext());
  }

  /**
   * 解析单次上游调用超时秒数：以 application.yml 为准。
   *
   * @param kind 文本 / 图像生成 / 多模态
   * @return 超时秒数，至少为 1
   */
  private int resolveTimeoutSeconds(AiProviderKind kind) {
    int configured;
    if (kind == AiProviderKind.IMAGE) {
      configured = properties.getAiImageTimeoutSeconds();
    } else if (kind == AiProviderKind.VISION) {
      configured = properties.getAiVisionTimeoutSeconds();
    } else {
      configured = properties.getAiTextTimeoutSeconds();
    }
    return Math.max(1, configured);
  }

  /**
   * 去掉 baseUrl 末尾斜杠，便于与 path 拼接且不出现双斜杠。
   *
   * @param url 原始 baseUrl，可为 null
   * @return 去尾斜杠后的字符串；null 视为空串
   */
  private String trimSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
