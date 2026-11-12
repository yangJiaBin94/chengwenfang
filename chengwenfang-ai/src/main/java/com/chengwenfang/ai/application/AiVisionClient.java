package com.chengwenfang.ai.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.request.VisionImagePart;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.enums.AiProviderKind;
import com.chengwenfang.ai.facade.enums.AiProtocol;
import com.chengwenfang.ai.facade.enums.ModelCallStatus;
import com.chengwenfang.ai.infrastructure.mapper.AiProviderConfigMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.infra.support.SecretCipher;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 多模态补全客户端：按 vision 配置做补全；无图为纯文本消息，有图为多模态消息。
 */
@Component
@RequiredArgsConstructor
public class AiVisionClient {

  /** 提供商配置表读写。 */
  private final AiProviderConfigMapper configMapper;
  /** 成文坊配置：多模态上游超时等。 */
  private final ChengwenfangProperties properties;
  /** 密钥列加解密。 */
  private final SecretCipher secretCipher;
  /** 模型调用审计写入。 */
  private final ModelCallRecorder modelCallRecorder;

  /**
   * 使用多模态配置做一次补全。
   *
   * @param request 含提示词；图片可空
   * @return 模型输出文本及实际使用的 model / protocol
   */
  public VisionCompleteResult complete(VisionCompleteRequest request) {
    AiProviderConfigEntity cfg = requireKind(AiProviderKind.VISION);
    String prompt = request == null || request.getPrompt() == null ? "" : request.getPrompt();
    String systemPrompt = request == null ? null : request.getSystemPrompt();
    String scene = request == null || StrUtil.isBlank(request.getScene())
        ? ModelCallScenes.ADHOC
        : request.getScene();
    Integer timeoutOverride = request == null ? null : request.getTimeoutSeconds();
    List<VisionImagePart> images = request == null ? null : request.getImages();
    String text = completeInternal(cfg, systemPrompt, prompt, scene, timeoutOverride, images);
    return VisionCompleteResult.builder()
        .text(text)
        .model(cfg.getModel())
        .protocol(cfg.getProtocol())
        .build();
  }

  /**
   * 多模态补全统一入口：按配置中的 protocol 分流，并写入 model_calls。
   *
   * @param cfg                    提供商配置（须含 apiKey、baseUrl、model、protocol）
   * @param systemPrompt           系统提示词；空白则只发送用户消息
   * @param prompt                 发给模型的用户提示词
   * @param scene                  审计场景码，写入 model_calls.scene
   * @param timeoutSecondsOverride 可选超时秒数；null 则用配置文件默认
   * @param images                 可选图片；非空时按多模态消息发送
   * @return 模型生成的纯文本
   */
  String completeInternal(
      AiProviderConfigEntity cfg,
      String systemPrompt,
      String prompt,
      String scene,
      Integer timeoutSecondsOverride,
      List<VisionImagePart> images) {
    if (StrUtil.isBlank(decryptApiKey(cfg))) {
      throw new BusinessException(ErrorCodes.AI_KEY_MISSING, "未配置多模态模型 API Key", 400);
    }
    long start = System.currentTimeMillis();
    int timeoutSeconds = resolveCallTimeoutSeconds(timeoutSecondsOverride);
    try {
      AiProtocol protocol = AiProtocol.from(cfg.getProtocol());
      String text;
      if (protocol == AiProtocol.OPENAI_RESPONSES) {
        text = completeResponses(cfg, systemPrompt, prompt, timeoutSeconds, images);
      } else {
        text = completeChat(cfg, systemPrompt, prompt, timeoutSeconds, images);
      }
      if (StrUtil.isBlank(text)) {
        throw new BusinessException(ErrorCodes.TEXT_FAILED, "多模态 AI 未返回可用内容", 502);
      }
      modelCallRecorder.recordCall(
          cfg,
          scene,
          ModelCallStatus.SUCCEEDED,
          start,
          prompt.length() + (systemPrompt == null ? 0 : systemPrompt.length()),
          text == null ? 0 : text.length(),
          null);
      return text;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      modelCallRecorder.recordCall(
          cfg,
          scene,
          ModelCallStatus.FAILED,
          start,
          prompt.length() + (systemPrompt == null ? 0 : systemPrompt.length()),
          0,
          ErrorCodes.TEXT_FAILED);
      throw new BusinessException(
          ErrorCodes.TEXT_FAILED,
          "多模态补全失败：" + AiUpstreamErrorMessages.humanize(e.getMessage()),
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

  /** 单次调用超时：配置文件默认与请求下限取较大值。 */
  private int resolveCallTimeoutSeconds(Integer timeoutSecondsOverride) {
    int configured = Math.max(1, properties.getAiVisionTimeoutSeconds());
    if (timeoutSecondsOverride != null && timeoutSecondsOverride > 0) {
      return Math.max(configured, timeoutSecondsOverride);
    }
    return configured;
  }

  /**
   * Chat Completions via LangChain4j {@link OpenAiChatModel}。
   */
  private String completeChat(
      AiProviderConfigEntity cfg,
      String systemPrompt,
      String prompt,
      int timeoutSeconds,
      List<VisionImagePart> images) {
    OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
        .apiKey(decryptApiKey(cfg))
        .baseUrl(trimSlash(cfg.getBaseUrl()))
        .modelName(cfg.getModel())
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .maxRetries(cfg.getMaxRetries() == null ? 1 : cfg.getMaxRetries())
        .temperature(cfg.getTemperature())
        .returnThinking(false);
    if (cfg.getMaxOutputTokens() != null && cfg.getMaxOutputTokens() > 0) {
      builder.maxTokens(cfg.getMaxOutputTokens());
    }
    ChatModel model = builder.build();
    return extractAssistantText(chatWithOptionalSystem(model, systemPrompt, prompt, images));
  }

  /**
   * Responses via LangChain4j {@link OpenAiResponsesChatModel}。
   */
  private String completeResponses(
      AiProviderConfigEntity cfg,
      String systemPrompt,
      String prompt,
      int timeoutSeconds,
      List<VisionImagePart> images) {
    Duration timeout = Duration.ofSeconds(timeoutSeconds);
    OpenAiResponsesChatModel.Builder builder = OpenAiResponsesChatModel.builder()
        .apiKey(decryptApiKey(cfg))
        .baseUrl(trimSlash(cfg.getBaseUrl()))
        .modelName(cfg.getModel())
        .temperature(cfg.getTemperature())
        .httpClientBuilder(JdkHttpClient.builder()
            .connectTimeout(timeout)
            .readTimeout(timeout));
    if (cfg.getMaxOutputTokens() != null && cfg.getMaxOutputTokens() > 0) {
      builder.maxOutputTokens(cfg.getMaxOutputTokens());
    }
    ChatModel model = builder.build();
    return extractAssistantText(chatWithOptionalSystem(model, systemPrompt, prompt, images));
  }

  /**
   * 组装系统消息与用户消息；有图片时使用 TextContent + ImageContent。
   */
  private ChatResponse chatWithOptionalSystem(
      ChatModel model, String systemPrompt, String prompt, List<VisionImagePart> images) {
    UserMessage userMessage = buildUserMessage(prompt, images);
    if (StrUtil.isBlank(systemPrompt)) {
      return model.chat(userMessage);
    }
    return model.chat(SystemMessage.from(systemPrompt), userMessage);
  }

  /**
   * 构建用户消息；无图时为纯文本，有图时为多模态内容列表。
   */
  private UserMessage buildUserMessage(String prompt, List<VisionImagePart> images) {
    if (CollUtil.isEmpty(images)) {
      return UserMessage.from(StrUtil.nullToEmpty(prompt));
    }
    List<Content> contents = new ArrayList<>();
    contents.add(TextContent.from(StrUtil.nullToEmpty(prompt)));
    for (VisionImagePart part : images) {
      contents.add(toImageContent(part));
    }
    return UserMessage.from(contents);
  }

  /**
   * 将图片部件转为 LangChain4j ImageContent。
   */
  private ImageContent toImageContent(VisionImagePart part) {
    if (part == null) {
      throw new BusinessException(ErrorCodes.TEXT_FAILED, "图片部件为空", 400);
    }
    String mime = StrUtil.blankToDefault(part.getMimeType(), "image/jpeg").trim().toLowerCase(Locale.ROOT);
    if (StrUtil.isNotBlank(part.getBase64Data())) {
      return ImageContent.from(part.getBase64Data().trim(), mime);
    }
    if (StrUtil.isBlank(part.getFilePath())) {
      throw new BusinessException(ErrorCodes.TEXT_FAILED, "图片缺少 filePath 或 base64Data", 400);
    }
    try {
      Path path = Path.of(part.getFilePath()).toAbsolutePath().normalize();
      if (!Files.isRegularFile(path)) {
        throw new BusinessException(ErrorCodes.TEXT_FAILED, "图片文件不存在：" + path, 400);
      }
      String inferred = inferMimeFromPath(path, mime);
      return ImageContent.from(path, inferred);
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(
          ErrorCodes.TEXT_FAILED, "读取图片失败：" + exception.getMessage(), 400);
    }
  }

  /** 按文件扩展名推断 MIME。 */
  private static String inferMimeFromPath(Path path, String defaultMime) {
    String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".png")) {
      return "image/png";
    }
    if (name.endsWith(".webp")) {
      return "image/webp";
    }
    if (name.endsWith(".gif")) {
      return "image/gif";
    }
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    return defaultMime;
  }

  /** 只取助手正文；thinking/reasoning 一律丢弃。 */
  private static String extractAssistantText(ChatResponse response) {
    if (response == null || response.aiMessage() == null) {
      return "";
    }
    String text = response.aiMessage().text();
    return text == null ? "" : text.strip();
  }

  /** 解密配置中的 API Key；空白密文返回空串。 */
  private String decryptApiKey(AiProviderConfigEntity cfg) {
    if (cfg == null || StrUtil.isBlank(cfg.getApiKeyCiphertext())) {
      return "";
    }
    return secretCipher.decrypt(cfg.getApiKeyCiphertext());
  }

  /** 去掉 baseUrl 末尾斜杠。 */
  private String trimSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
