package com.chengwenfang.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * 自由结构业务载荷（替代裸 {@code Map} 入参），内部仍可序列化为 JSON。
 * <p>优先使用具名字段的 View/Request；仅当载荷键集合开放时使用本类。
 * <p>反序列化支持扁平 JSON 对象（键直接落入 fields），亦兼容 {@code {"fields":{...}}}。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlexiblePayload {
  /** 键值对载荷。 */
  @Builder.Default
  private Map<String, Object> fields = new LinkedHashMap<>();

  /** 空载荷。 */
  public static FlexiblePayload empty() {
    return FlexiblePayload.builder().fields(new LinkedHashMap<>()).build();
  }

  /** 从 Map 构造（null 安全）。 */
  public static FlexiblePayload of(Map<String, Object> map) {
    return FlexiblePayload.builder()
        .fields(map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map))
        .build();
  }

  /** null 安全：null 返回空载荷。 */
  public static FlexiblePayload ofNullable(FlexiblePayload payload) {
    return payload == null ? empty() : payload;
  }

  /** Jackson：扁平键写入 fields。 */
  @JsonAnySetter
  public void put(String key, Object value) {
    if (fields == null) {
      fields = new LinkedHashMap<>();
    }
    if ("fields".equals(key) && value instanceof Map<?, ?> nested) {
      for (Map.Entry<?, ?> entry : nested.entrySet()) {
        if (entry.getKey() != null) {
          fields.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return;
    }
    fields.put(key, value);
  }

  /** 写入键值并返回 this，便于链式补字段。 */
  public FlexiblePayload with(String key, Object value) {
    put(key, value);
    return this;
  }

  /** 仅当键不存在时写入。 */
  public void putIfAbsent(String key, Object value) {
    if (fields == null) {
      fields = new LinkedHashMap<>();
    }
    fields.putIfAbsent(key, value);
  }

  /** Jackson：序列化为扁平对象。 */
  @JsonAnyGetter
  public Map<String, Object> any() {
    return fields == null ? Map.of() : fields;
  }

  /** 只读视图。 */
  @JsonIgnore
  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(fields == null ? Map.of() : fields);
  }

  /** 可变副本，供写入 Job input_json。 */
  @JsonIgnore
  public Map<String, Object> mutableMap() {
    return fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
  }

  @JsonIgnore
  public Map<String, Object> getFields() {
    return fields;
  }

  public void setFields(Map<String, Object> fields) {
    this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
  }
}
