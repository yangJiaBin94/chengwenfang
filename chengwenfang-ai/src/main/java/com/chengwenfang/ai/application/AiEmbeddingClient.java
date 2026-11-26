package com.chengwenfang.ai.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.enums.AiProviderKind;
import com.chengwenfang.ai.facade.enums.ModelCallStatus;
import com.chengwenfang.ai.infrastructure.mapper.AiProviderConfigMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.SecretCipher;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 嵌入模型客户端：按 embedding 配置调用 OpenAI 兼容 Embeddings API，并写入 model_calls。
 */
@Component
@RequiredArgsConstructor
public class AiEmbeddingClient {

  /** 提供商配置表读写。 */
  private final AiProviderConfigMapper configMapper;
  /** 成文坊配置：嵌入上游超时等。 */
  private final ChengwenfangProperties properties;
  /** 密钥列加解密。 */
  private final SecretCipher secretCipher;
  /** 模型调用审计写入。 */
  private final ModelCallRecorder modelCallRecorder;
  /** 用户级数据范围。 */
  private final OwnerScope ownerScope;

  /**
   * 使用库中 embedding 配置嵌入多条文本。
   *
   * @param texts 待嵌入文案
   * @return 向量列表
   */
  public List<float[]> embed(List<String> texts) {
    return embedInternal(requireKind(AiProviderKind.EMBEDDING), texts, ModelCallScenes.EMBEDDING);
  }

  /**
   * 使用库中 embedding 配置嵌入单条文本。
   *
   * @param text 待嵌入文案
   * @return 向量
   */
  public float[] embedOne(String text) {
    List<float[]> vectors = embed(List.of(StrUtil.nullToEmpty(text)));
    return vectors.isEmpty() ? new float[0] : vectors.get(0);
  }

  /**
   * 使用指定配置嵌入单条文本（连通性测试可传表单草稿）。
   *
   * @param cfg   提供商配置
   * @param text  待嵌入文案
   * @param scene 审计场景
   * @return 向量
   */
  float[] embedOne(AiProviderConfigEntity cfg, String text, String scene) {
    List<float[]> vectors = embedInternal(cfg, List.of(StrUtil.nullToEmpty(text)), scene);
    return vectors.isEmpty() ? new float[0] : vectors.get(0);
  }

  /**
   * 读取 embedding 配置中的模型名。
   *
   * @return 模型名
   */
  public String embeddingModelName() {
    return requireKind(AiProviderKind.EMBEDDING).getModel();
  }

  /**
   * 嵌入实现：构建 LangChain4j {@link OpenAiEmbeddingModel} 并写审计。
   *
   * @param cfg   提供商配置
   * @param texts 文案列表
   * @param scene 审计场景
   * @return 向量列表
   */
  private List<float[]> embedInternal(
      AiProviderConfigEntity cfg, List<String> texts, String scene) {
    if (CollUtil.isEmpty(texts)) {
      return Collections.emptyList();
    }
    if (StrUtil.isBlank(decryptApiKey(cfg))) {
      throw new BusinessException(ErrorCodes.AI_KEY_MISSING, "未配置嵌入模型 API Key", 400);
    }
    long start = System.currentTimeMillis();
    int inputChars = texts.stream().mapToInt(value -> value == null ? 0 : value.length()).sum();
    try {
      EmbeddingModel model = buildModel(cfg);
      List<TextSegment> segments = new ArrayList<>();
      for (String text : texts) {
        segments.add(TextSegment.from(StrUtil.nullToEmpty(text)));
      }
      Response<List<Embedding>> response = model.embedAll(segments);
      List<Embedding> embeddings =
          response == null || response.content() == null ? Collections.emptyList() : response.content();
      if (embeddings.size() != texts.size()) {
        throw new BusinessException(ErrorCodes.TEXT_FAILED, "嵌入模型返回数量与输入不一致", 502);
      }
      List<float[]> vectors = new ArrayList<>(embeddings.size());
      for (Embedding embedding : embeddings) {
        vectors.add(embedding == null ? new float[0] : embedding.vector());
      }
      int outputDims = vectors.stream().mapToInt(vector -> vector == null ? 0 : vector.length).sum();
      modelCallRecorder.recordCall(
          cfg, scene, ModelCallStatus.SUCCEEDED, start, inputChars, outputDims, null);
      return vectors;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      modelCallRecorder.recordCall(
          cfg, scene, ModelCallStatus.FAILED, start, inputChars, 0, ErrorCodes.TEXT_FAILED);
      throw new BusinessException(
          ErrorCodes.TEXT_FAILED,
          "文本嵌入失败：" + AiUpstreamErrorMessages.humanize(exception.getMessage()),
          502);
    }
  }

  /**
   * 按 kind 加载唯一配置行。
   *
   * @param kind 提供商类型枚举
   * @return 对应配置实体
   */
  AiProviderConfigEntity requireKind(AiProviderKind kind) {
    AiProviderConfigEntity cfg = configMapper.selectOne(new LambdaQueryWrapper<AiProviderConfigEntity>()
        .eq(AiProviderConfigEntity::getOwnerUserId, ownerScope.effectiveOwnerUserId())
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
   * 构建 OpenAI 兼容嵌入模型实例。
   *
   * @param cfg 提供商配置
   * @return 嵌入模型
   */
  private EmbeddingModel buildModel(AiProviderConfigEntity cfg) {
    int timeoutSeconds = Math.max(1, properties.getAiEmbeddingTimeoutSeconds());
    Duration timeout = Duration.ofSeconds(timeoutSeconds);
    return OpenAiEmbeddingModel.builder()
        .apiKey(decryptApiKey(cfg))
        .baseUrl(trimSlash(cfg.getBaseUrl()))
        .modelName(cfg.getModel())
        .timeout(timeout)
        .maxRetries(cfg.getMaxRetries() == null ? 1 : cfg.getMaxRetries())
        .build();
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
   * 去掉 baseUrl 末尾斜杠。
   *
   * @param url 原始 baseUrl
   * @return 去尾斜杠后的字符串
   */
  private static String trimSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
