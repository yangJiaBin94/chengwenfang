package com.chengwenfang.ai.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.AiEmbeddingFacade;
import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.ai.facade.AiSettingsFacade;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.AiSettingsUpdateRequest;
import com.chengwenfang.ai.facade.dto.request.ImageGenerateRequest;
import com.chengwenfang.ai.facade.dto.request.ProviderConfigPatch;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.request.VisionImagePart;
import com.chengwenfang.ai.facade.dto.view.AiSettingsView;
import com.chengwenfang.ai.facade.dto.view.ConnectionTestResult;
import com.chengwenfang.ai.facade.dto.view.ImageGenerateResult;
import com.chengwenfang.ai.facade.dto.view.ProviderPublicView;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.enums.AiProviderKind;
import com.chengwenfang.ai.facade.enums.ModelCallStatus;
import com.chengwenfang.ai.infrastructure.mapper.AiProviderConfigMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.SecretCipher;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 设置/图像/多模态/嵌入门面默认实现：配置读写与连通性测试路由；补全/生图/嵌入委派至各 Client。
 * <p>implements {@link AiSettingsFacade}, {@link AiImageFacade}, {@link AiVisionFacade}, {@link AiEmbeddingFacade}。
 * <p>对外返回的 settings 不含明文 apiKey，仅暴露 {@code apiKeyConfigured}。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DefaultAiFacade implements AiSettingsFacade, AiImageFacade, AiVisionFacade, AiEmbeddingFacade {
  /** 1x1 PNG（透明），用于 vision 连通性测试。 */
  private static final String TINY_PNG_BASE64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

  /** 提供商配置表读写。 */
  private final AiProviderConfigMapper configMapper;
  /** 成文坊配置：图像/多模态上游超时等。 */
  private final ChengwenfangProperties properties;
  /** 密钥列加解密。 */
  private final SecretCipher secretCipher;
  /** 图像生图客户端。 */
  private final AiImageClient imageClient;
  /** 多模态补全客户端。 */
  private final AiVisionClient visionClient;
  /** 文本嵌入客户端。 */
  private final AiEmbeddingClient embeddingClient;
  /** 模型调用审计写入。 */
  private final ModelCallRecorder modelCallRecorder;

  /**
   * 读取 image / vision / embedding 配置的公开视图。
   *
   * @return 脱敏后的 {@link AiSettingsView}
   */
  @Override
  public AiSettingsView getSettings() {
    return AiSettingsView.builder()
        .image(toPublic(requireKind(AiProviderKind.IMAGE)))
        .vision(toPublic(requireKind(AiProviderKind.VISION)))
        .embedding(toPublic(requireKind(AiProviderKind.EMBEDDING)))
        .build();
  }

  /**
   * 部分更新 image / vision / embedding 配置；请求里 apiKey 为空时不覆盖库中已有密钥。
   *
   * @param request 更新请求；各侧可为 null 表示该侧不改
   * @return 更新完成后的公开 settings
   */
  @Override
  @Transactional
  public AiSettingsView updateSettings(AiSettingsUpdateRequest request) {
    AiSettingsUpdateRequest body =
        request == null ? new AiSettingsUpdateRequest() : request;
    log.info(
        "更新 AI 设置 image={} vision={} embedding={}",
        body.getImage() != null,
        body.getVision() != null,
        body.getEmbedding() != null);
    if (body.getImage() != null) {
      updateKind(AiProviderKind.IMAGE, body.getImage());
    }
    if (body.getVision() != null) {
      updateKind(AiProviderKind.VISION, body.getVision());
    }
    if (body.getEmbedding() != null) {
      updateKind(AiProviderKind.EMBEDDING, body.getEmbedding());
    }
    return getSettings();
  }

  /**
   * 用前端表单草稿做连通性探测（不落库），并写入一条 connection_test 场景的 model_call。
   *
   * @param kind  提供商类型字符串，取值 {@code image} / {@code vision} / {@code embedding}
   * @param patch 页面当前填写值；null 时仅用库中已保存配置
   * @return 探测成功结果
   */
  @Override
  public ConnectionTestResult testConnection(String kind, ProviderConfigPatch patch) {
    AiProviderKind providerKind = AiProviderKind.from(kind);
    log.info("AI 连通性测试 kind={} draft={}", providerKind.getValue(), patch != null);
    AiProviderConfigEntity cfg = resolveTestConfig(providerKind, patch);
    long start = System.currentTimeMillis();
    try {
      if (providerKind == AiProviderKind.IMAGE) {
        ImageGenerateResult result = imageClient.generate(
            cfg,
            ImageGenerateRequest.builder()
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
      if (providerKind == AiProviderKind.EMBEDDING) {
        float[] vector = embeddingClient.embedOne(cfg, "ping", ModelCallScenes.CONNECTION_TEST);
        if (vector == null || vector.length == 0) {
          throw new BusinessException(ErrorCodes.TEXT_FAILED, "嵌入连接测试未返回向量", 502);
        }
        return ConnectionTestResult.builder()
            .ok(true)
            .kind(providerKind.getValue())
            .preview("嵌入成功：" + cfg.getModel() + " · 维度 " + vector.length)
            .build();
      }
      // 先发纯文本探测（与运行时绝大多数无图补全一致）
      String textPing = visionClient.completeInternal(
          cfg, null, "Reply with OK", ModelCallScenes.CONNECTION_TEST, null, null);
      String preview = StrUtil.blankToDefault(textPing, "OK");
      // 再尝试最小 PNG；看图失败不阻断连通性（补全仍可用）
      try {
        String visionPing = visionClient.completeInternal(
            cfg,
            null,
            "Reply with OK",
            ModelCallScenes.CONNECTION_TEST,
            null,
            List.of(VisionImagePart.builder()
                .base64Data(TINY_PNG_BASE64)
                .mimeType("image/png")
                .build()));
        if (StrUtil.isNotBlank(visionPing)) {
          preview = preview + " · 看图可用";
        }
      } catch (Exception imageProbeFailed) {
        preview = preview + " · 看图未通过："
            + AiUpstreamErrorMessages.humanize(imageProbeFailed.getMessage());
      }
      return ConnectionTestResult.builder()
          .ok(true)
          .kind(providerKind.getValue())
          .preview(preview)
          .build();
    } catch (Exception e) {
      modelCallRecorder.recordCall(
          cfg,
          ModelCallScenes.CONNECTION_TEST,
          ModelCallStatus.FAILED,
          start,
          0,
          0,
          ErrorCodes.CONNECTION_FAILED);
      throw new BusinessException(
          ErrorCodes.AI_TEST_FAILED,
          "AI 连通性测试失败：" + AiUpstreamErrorMessages.humanize(e.getMessage()),
          502);
    }
  }

  /**
   * 将表单草稿叠到内存配置上：非密钥字段以草稿为准；apiKey 空白则保留库中密文。
   */
  private AiProviderConfigEntity resolveTestConfig(AiProviderKind kind, ProviderConfigPatch patch) {
    AiProviderConfigEntity saved = requireKind(kind);
    if (patch == null) {
      return saved;
    }
    AiProviderConfigEntity cfg = AiProviderConfigEntity.builder()
        .id(saved.getId())
        .kind(saved.getKind())
        .name(StrUtil.blankToDefault(patch.getName(), saved.getName()))
        .model(patch.getModel() != null ? patch.getModel() : saved.getModel())
        .baseUrl(patch.getBaseUrl() != null ? patch.getBaseUrl() : saved.getBaseUrl())
        .protocol(patch.getProtocol() != null ? patch.getProtocol() : saved.getProtocol())
        .timeoutSeconds(
            patch.getTimeoutSeconds() != null ? patch.getTimeoutSeconds() : saved.getTimeoutSeconds())
        .maxRetries(saved.getMaxRetries())
        .temperature(saved.getTemperature())
        .maxOutputTokens(saved.getMaxOutputTokens())
        .imageQuality(
            patch.getImageQuality() != null ? patch.getImageQuality() : saved.getImageQuality())
        .enabled(patch.getEnabled() != null ? patch.getEnabled() : saved.getEnabled())
        .apiKeyCiphertext(saved.getApiKeyCiphertext())
        .createdAt(saved.getCreatedAt())
        .updatedAt(saved.getUpdatedAt())
        .build();
    if (StrUtil.isNotBlank(patch.getApiKey()) && !"null".equals(patch.getApiKey())) {
      cfg.setApiKeyCiphertext(secretCipher.encrypt(patch.getApiKey()));
    }
    return cfg;
  }

  /**
   * 使用多模态配置做一次补全（图片可选）。
   */
  @Override
  public VisionCompleteResult complete(VisionCompleteRequest request) {
    return visionClient.complete(request);
  }

  @Override
  public boolean supportsWebSearch() {
    return visionClient.supportsWebSearch();
  }

  /**
   * 调用图像模型生成图片。
   */
  @Override
  public ImageGenerateResult generate(ImageGenerateRequest request) {
    return imageClient.generate(request);
  }

  @Override
  public List<float[]> embed(List<String> texts) {
    return embeddingClient.embed(texts);
  }

  @Override
  public float[] embedOne(String text) {
    return embeddingClient.embedOne(text);
  }

  @Override
  public String embeddingModelName() {
    return embeddingClient.embeddingModelName();
  }

  /**
   * 将 patch 中的非空字段合并进指定 kind 的配置行并 update。
   */
  private void updateKind(AiProviderKind kind, ProviderConfigPatch patch) {
    AiProviderConfigEntity cfg = requireKind(kind);
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
    if (StrUtil.isNotBlank(patch.getApiKey()) && !"null".equals(patch.getApiKey())) {
      cfg.setApiKeyCiphertext(secretCipher.encrypt(patch.getApiKey()));
    }
    if (patch.getImageQuality() != null) {
      cfg.setImageQuality(patch.getImageQuality().isBlank() ? null : patch.getImageQuality().strip());
    }
    if (patch.getEnabled() != null) {
      cfg.setEnabled(patch.getEnabled());
    }
    cfg.setUpdatedAt(LocalDateTime.now());
    configMapper.updateById(cfg);
  }

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

  private ProviderPublicView toPublic(AiProviderConfigEntity cfg) {
    AiProviderKind kind = AiProviderKind.from(cfg.getKind());
    // 联网搜索能力仅对多模态配置有意义，与雷达 AI 自搜门控一致
    boolean webSearchSupported =
        kind == AiProviderKind.VISION && AiWebSearchCapability.supports(cfg);
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
        .apiKeyConfigured(StrUtil.isNotBlank(cfg.getApiKeyCiphertext()))
        .webSearchSupported(webSearchSupported)
        .build();
  }

  private int resolveTimeoutSeconds(AiProviderKind kind) {
    int configured;
    if (kind == AiProviderKind.IMAGE) {
      configured = properties.getAiImageTimeoutSeconds();
    } else if (kind == AiProviderKind.EMBEDDING) {
      configured = properties.getAiEmbeddingTimeoutSeconds();
    } else {
      configured = properties.getAiVisionTimeoutSeconds();
    }
    return Math.max(1, configured);
  }
}
