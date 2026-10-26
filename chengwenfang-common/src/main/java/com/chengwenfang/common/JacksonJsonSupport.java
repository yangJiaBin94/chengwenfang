package com.chengwenfang.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文档与调试快照的统一 Jackson 编解码。
 *
 * <p>进入提示词的序列化仍走调用方原库（多为 Hutool {@code JSONUtil}），勿改用本类。
 */
public final class JacksonJsonSupport {

  /** 与业务库列序列化共用的 ObjectMapper。 */
  private final ObjectMapper objectMapper;

  /**
   * @param objectMapper Jackson ObjectMapper；不可为 null
   */
  public JacksonJsonSupport(ObjectMapper objectMapper) {
    if (objectMapper == null) {
      throw new IllegalArgumentException("objectMapper");
    }
    this.objectMapper = objectMapper;
  }

  /**
   * 序列化为 JSON；失败抛出 {@link IllegalStateException}（写库路径不可静默失败）。
   *
   * @param value 任意可序列化对象
   * @return JSON 文本
   */
  public String writeStrict(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  /**
   * 序列化为 JSON；失败时按类型回退为空数组或空对象。
   *
   * @param value 待序列化对象
   * @return JSON 文本
   */
  public String writeLenient(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ignored) {
      return value instanceof List ? "[]" : "{}";
    }
  }

  /**
   * 序列化为 JSON；失败时恒回退为 {@code []}（内容域历史行为）。
   *
   * @param value 任意可序列化对象
   * @return JSON 文本
   */
  public String writeOrEmptyArray(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  /**
   * 解析为 LinkedHashMap；空白或失败返回可变空 Map。
   *
   * @param json JSON 文本
   * @return Map（永不为 null）
   */
  public Map<String, Object> readMapOrEmpty(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, Object> map =
          objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
      return map == null ? new LinkedHashMap<>() : map;
    } catch (Exception ignored) {
      return new LinkedHashMap<>();
    }
  }

  /**
   * 解析为 LinkedHashMap；空白或失败返回不可变空 Map（内容域历史行为）。
   *
   * @param json JSON 文本
   * @return Map（永不为 null）
   */
  public Map<String, Object> readMapOrImmutableEmpty(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> map =
          objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
      return map == null ? Map.of() : map;
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  /**
   * 解析为 LinkedHashMap；空白返回 null；失败包一层 {@code raw}。
   *
   * @param json JSON 文本
   * @return Map、null，或 {@code {raw: 原文}}
   */
  public Map<String, Object> readMapOrRaw(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception ignored) {
      return Map.of("raw", json);
    }
  }

  /**
   * 解析 JSON 数组为 List&lt;Map&gt;；空白或失败返回空列表。
   *
   * @param json JSON 数组文本
   * @return 列表
   */
  public List<Map<String, Object>> readMapListOrEmpty(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<Map<String, Object>> list =
          objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
      return list == null ? List.of() : list;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  /**
   * 解析为指定类型；空白或失败返回 null。
   *
   * @param json          JSON 文本
   * @param typeReference 类型引用
   * @param <T>           目标类型
   * @return 解析结果；失败为 null
   */
  public <T> T readOrNull(String json, TypeReference<T> typeReference) {
    if (json == null || json.isBlank() || typeReference == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, typeReference);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 解析为指定类型列表；空白或失败返回空列表。
   *
   * @param json          JSON 文本
   * @param typeReference 列表类型引用
   * @param <T>           元素类型
   * @return 列表
   */
  public <T> List<T> readListOrEmpty(String json, TypeReference<List<T>> typeReference) {
    if (json == null || json.isBlank() || typeReference == null) {
      return List.of();
    }
    try {
      List<T> list = objectMapper.readValue(json, typeReference);
      return list == null ? List.of() : list;
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  /**
   * 暴露底层 ObjectMapper（仅供过渡调用）。
   *
   * @return ObjectMapper
   */
  public ObjectMapper objectMapper() {
    return objectMapper;
  }
}
