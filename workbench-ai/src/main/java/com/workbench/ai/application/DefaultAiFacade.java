package com.workbench.ai.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.ai.domain.entity.AiProviderConfigEntity;
import com.workbench.ai.facade.AiImageFacade;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.AiVisionFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.AiSettingsUpdateRequest;
import com.workbench.ai.facade.dto.request.ImageGenerateRequest;
import com.workbench.ai.facade.dto.request.ProviderConfigPatch;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.request.VisionCompleteRequest;
import com.workbench.ai.facade.dto.request.VisionImagePart;
import com.workbench.ai.facade.dto.view.AiSettingsView;
import com.workbench.ai.facade.dto.view.ConnectionTestResult;
import com.workbench.ai.facade.dto.view.ImageGenerateResult;
import com.workbench.ai.facade.dto.view.ProviderPublicView;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.ai.facade.dto.view.VisionCompleteResult;
import com.workbench.ai.facade.enums.AiProviderKind;
import com.workbench.ai.facade.enums.ModelCallStatus;
import com.workbench.ai.infrastructure.mapper.AiProviderConfigMapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.infra.support.SecretCipher;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 文本/图像/多模态门面默认实现：配置读写与连通性测试路由；补全/生图委派至各 Client。
 * <p>implements {@link AiTextFacade}, {@link AiImageFacade}, {@link AiVisionFacade}。
 * <p>对外返回的 settings 不含明文 apiKey，仅暴露 {@code apiKeyConfigured}。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DefaultAiFacade implements AiTextFacade, AiImageFacade, AiVisionFacade {
  /** 1x1 PNG（透明），用于 vision 连通性测试。 */
  private static final String TINY_PNG_BASE64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  /** 提供商配置表读写。 */
  private final AiProviderConfigMapper configMapper;
  /** 工作台配置：文本/图像/多模态上游超时等。 */
  private final WorkbenchProperties properties;
  /** 密钥列加解密。 */
  private final SecretCipher secretCipher;
  /** 文本补全客户端。 */
  private final AiTextClient textClient;
  /** 图像生图客户端。 */
  private final AiImageClient imageClient;
  /** 多模态看图客户端。 */
  private final AiVisionClient visionClient;
  /** 模型调用审计写入。 */
  private final ModelCallRecorder modelCallRecorder;

  /**
   * 读取 text / image / vision 配置的公开视图。
   *
   * @return 含 text、image、vision 的 {@link AiSettingsView}；字段中无明文 apiKey，仅有是否已配置标志
   */
  @Override
  public AiSettingsView getSettings() {
    // 分别加载三侧配置，并脱敏为公开视图
    return AiSettingsView.builder()
        .text(toPublic(requireKind(AiProviderKind.TEXT)))
        .image(toPublic(requireKind(AiProviderKind.IMAGE)))
        .vision(toPublic(requireKind(AiProviderKind.VISION)))
        .build();
  }

  /**
   * 部分更新 text / image / vision 配置；请求里 apiKey 为空时不覆盖库中已有密钥。
   *
   * @param request 更新请求；各侧可为 null 表示该侧不改；整体为 null 视为空请求
   * @return 更新完成后的公开 settings（同 {@link #getSettings()}）
   */
  @Override
  @Transactional
  public AiSettingsView updateSettings(AiSettingsUpdateRequest request) {
    // 空请求按空对象处理，避免 NPE
    AiSettingsUpdateRequest body =
        request == null ? new AiSettingsUpdateRequest() : request;
    log.info(
        "更新 AI 设置 text={} image={} vision={}",
        body.getText() != null,
        body.getImage() != null,
        body.getVision() != null);
    // 仅对请求中出现的一侧做合并更新
    if (body.getText() != null) {
      updateKind(AiProviderKind.TEXT, body.getText());
    }
    if (body.getImage() != null) {
      updateKind(AiProviderKind.IMAGE, body.getImage());
    }
    if (body.getVision() != null) {
      updateKind(AiProviderKind.VISION, body.getVision());
    }
    // 返回最新脱敏配置，供前端回显
    return getSettings();
  }

  /**
   * 对指定 kind 做连通性探测，并写入一条 connection_test 场景的 model_call。
   *
   * @param kind 提供商类型字符串，取值 {@code text} / {@code image} / {@code vision}（大小写不敏感）
   * @return 探测成功结果：图像侧返回真实图片，文本/多模态侧带模型回复预览
   * @throws BusinessException 配置缺失、上游失败或未知 kind 时抛出（失败也会记 model_call）
   */
  @Override
  public ConnectionTestResult testConnection(String kind) {
    // 将前端传入的 kind 解析为枚举，非法值直接抛错
    AiProviderKind providerKind = AiProviderKind.from(kind);
    log.info("AI 连通性测试 kind={}", providerKind.getValue());
    // 加载对应配置行，供后续调用与审计关联
    AiProviderConfigEntity cfg = requireKind(providerKind);
    // 记录开始时间，用于计算耗时
    long start = System.currentTimeMillis();
    try {
      if (providerKind == AiProviderKind.IMAGE) {
        // 与旧版一致：真实请求图像上游，生成一张最小测试图
        ImageGenerateResult result = generate(ImageGenerateRequest.builder()
            .prompt("纯白背景，中央一个绿色圆点，无文字，简洁扁平风格。")
            .dryRun(false)
            .build());
        if (StrUtil.isBlank(result.getB64Json()) && StrUtil.isBlank(result.getUrl())) {
          throw new BusinessException(ErrorCodes.IMAGE_FAILED, "图片连接测试未返回图片", 502);
        }
        modelCallRecorder.recordCall(
            cfg, ModelCallScenes.CONNECTION_TEST, ModelCallStatus.SUCCEEDED, start, 0, 0, null);
        return ConnectionTestResult.builder()
            .ok(true)
            .kind(providerKind.getValue())
            .preview("图片生成成功：" + cfg.getModel() + " · " + cfg.getProtocol())
            .details(result)
            .build();
      }
      if (providerKind == AiProviderKind.VISION) {
        // 多模态：发一张最小 PNG + 短提示，验证模型支持看图
        VisionCompleteResult vision = complete(VisionCompleteRequest.builder()
            .scene(ModelCallScenes.CONNECTION_TEST)
            .prompt("Reply with OK")
            .images(List.of(VisionImagePart.builder()
                .base64Data(TINY_PNG_BASE64)
                .mimeType("image/png")
                .build()))
            .build());
        return ConnectionTestResult.builder()
            .ok(true)
            .kind(providerKind.getValue())
            .preview(vision.getText())
            .build();
      }
      // 文本侧发一句短提示，走真实补全链路验证密钥与协议
      String text = textClient.completeInternal(
          cfg, null, "Reply with OK", ModelCallScenes.CONNECTION_TEST, null, null);
      return ConnectionTestResult.builder()
          .ok(true)
          .kind(providerKind.getValue())
          .preview(text)
          .build();
    } catch (Exception e) {
      // 失败同样落审计，再包装为对外业务异常
      modelCallRecorder.recordCall(
          cfg,
          ModelCallScenes.CONNECTION_TEST,
          ModelCallStatus.FAILED,
          start,
          0,
          0,
          ErrorCodes.CONNECTION_FAILED);
      throw new BusinessException(ErrorCodes.AI_TEST_FAILED, "AI 连通性测试失败：" + e.getMessage(), 502);
    }
  }

  /**
   * 使用文本模型做一次补全。
   *
   * @param request 补全请求；{@code prompt} 为输入文案，{@code scene} 为审计场景（空则 {@code adhoc}）
   * @return 模型输出文本及实际使用的 model / protocol
   */
  @Override
  public TextCompleteResult completeText(TextCompleteRequest request) {
    return textClient.completeText(request);
  }

  /**
   * 使用多模态模型根据图片与提示词做一次补全。
   *
   * @param request 含提示词与至少一张图片
   * @return 模型输出文本及实际使用的 model / protocol
   */
  @Override
  public VisionCompleteResult complete(VisionCompleteRequest request) {
    return visionClient.complete(request);
  }

  /**
   * 调用图像模型生成图片；{@code dryRun=true} 时不请求上游，仅校验配置是否可读。
   *
   * @param request 生图请求；{@code prompt} 提示词，{@code size} 尺寸（默认 1024x1024），{@code dryRun} 是否空跑
   * @return 成功时含 model 与 b64Json/url（视上游返回）；dryRun 时仅 ok/model
   * @throws BusinessException 未配置 apiKey 或上游 HTTP 非 2xx
   */
  @Override
  public ImageGenerateResult generate(ImageGenerateRequest request) {
    return imageClient.generate(request);
  }

  /**
   * 将 patch 中的非空字段合并进指定 kind 的配置行并 update；空白 apiKey 不覆盖已有密钥。
   *
   * @param kind  目标配置行（text / image）
   * @param patch 部分更新字段；各字段 null 表示不改该列
   */
  private void updateKind(AiProviderKind kind, ProviderConfigPatch patch) {
    AiProviderConfigEntity cfg = requireKind(kind);
    // 逐字段合并：仅 patch 显式给出的值才写入
    if (patch.getName() != null) {
      cfg.setName(patch.getName());
    }
    if (patch.getModel() != null) {
      cfg.setModel(patch.getModel());
    }
    if (patch.getBaseUrl() != null) {
      cfg.setBaseUrl(patch.getBaseUrl());
    }
    if (patch.getProtocol() != null) {
      cfg.setProtocol(patch.getProtocol());
    }
    // 空串/"null" 视为前端清空占位，不覆盖库中密钥；写入前加密
    if (StrUtil.isNotBlank(patch.getApiKey()) && !"null".equals(patch.getApiKey())) {
      cfg.setApiKeyCiphertext(secretCipher.encrypt(patch.getApiKey()));
    }
    // 超时秒数以 application.yml 的 workbench.ai-*-timeout-seconds 为准，忽略前端写入
    if (patch.getImageQuality() != null) {
      cfg.setImageQuality(patch.getImageQuality().isBlank() ? null : patch.getImageQuality().strip());
    }
    if (patch.getEnabled() != null) {
      cfg.setEnabled(patch.getEnabled());
    }
    cfg.setUpdatedAt(LocalDateTime.now());
    configMapper.updateById(cfg);
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
   * 将实体转为对外公开视图：用 {@code apiKeyConfigured} 代替明文密钥。
   *
   * @param cfg 库中的完整配置实体（可含 apiKey）
   * @return 可安全返回给前端的 {@link ProviderPublicView}
   */
  private ProviderPublicView toPublic(AiProviderConfigEntity cfg) {
    AiProviderKind kind = AiProviderKind.from(cfg.getKind());
    return ProviderPublicView.builder()
        .kind(cfg.getKind())
        .name(cfg.getName())
        .model(cfg.getModel())
        .baseUrl(cfg.getBaseUrl())
        .protocol(cfg.getProtocol())
        .timeoutSeconds(resolveTimeoutSeconds(kind))
        .maxRetries(cfg.getMaxRetries())
        .temperature(cfg.getTemperature())
        .maxOutputTokens(cfg.getMaxOutputTokens())
        .imageQuality(cfg.getImageQuality())
        .enabled(cfg.getEnabled())
        // 只暴露是否已配置，不回传密钥正文
        .apiKeyConfigured(StrUtil.isNotBlank(cfg.getApiKeyCiphertext()))
        .build();
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
}
