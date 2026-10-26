package com.chengwenfang.job.facade.dto.job;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.common.FlexiblePayload;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务入参/出参编解码：与原 Map 路径共用 Hutool {@link JSONUtil}。
 * <p>{@link FlexiblePayload} 经 {@link FlexiblePayload#mutableMap()} 展平，避免序列化为 {@code {fields:{...}}}。
 */
public final class JobPayloads {

  private JobPayloads() {}

  /**
   * 将任务载荷序列化为 JSON；null 视为空对象；{@link FlexiblePayload} 展平后序列化。
   *
   * @param value 入参或出参对象
   * @return JSON 文本
   */
  public static String toJson(Object value) {
    if (value == null) {
      return JSONUtil.toJsonStr(Map.of());
    }
    if (value instanceof FlexiblePayload flexiblePayload) {
      return JSONUtil.toJsonStr(flexiblePayload.mutableMap());
    }
    return JSONUtil.toJsonStr(value);
  }

  /**
   * 将 JSON 反序列化为指定类型；空/blank 按 {@code {}} 处理。
   *
   * @param json 任务 input_json / output_json
   * @param type 目标类型
   * @param <T>  类型参数
   * @return 解析结果；JSON 无效时行为与 Hutool toBean 一致
   */
  public static <T> T parse(String json, Class<T> type) {
    return JSONUtil.toBean(StrUtil.blankToDefault(json, "{}"), type);
  }

  /**
   * 将 JSON 解析为保序 {@code Map<String, Object>}；空/blank 按 {@code {}} 处理。
   *
   * @param json 任务 input_json / output_json
   * @return 可变保序 Map
   */
  public static Map<String, Object> parseMap(String json) {
    return asMap(JSONUtil.parseObj(StrUtil.blankToDefault(json, "{}")));
  }

  /**
   * 供 themeId/skillSnapshot 提取与终态事件；{@link LinkedHashMap} 保序。
   *
   * @param value 任意载荷
   * @return 可变保序 Map；null 返回空 Map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object value) {
    if (value == null) {
      return new LinkedHashMap<>();
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() != null) {
          copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
      }
      return copy;
    }
    if (value instanceof FlexiblePayload flexiblePayload) {
      return flexiblePayload.mutableMap();
    }
    return JSONUtil.toBean(JSONUtil.toJsonStr(value), LinkedHashMap.class);
  }

  /**
   * 默认成功出参 {@code {ok:true}}。
   *
   * @return 仅含 ok=true 的出参
   */
  public static OkJobOutput okTrue() {
    return OkJobOutput.builder().ok(true).build();
  }
}
