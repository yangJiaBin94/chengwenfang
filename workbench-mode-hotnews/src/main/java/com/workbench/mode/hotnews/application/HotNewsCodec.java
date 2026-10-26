package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.JacksonJsonSupport;
import com.workbench.mode.hotnews.domain.ResearchContentClassifier;
import com.workbench.mode.hotnews.domain.TopicScoreDetails;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.workbench.sourcing.facade.enums.SourceMediaType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 热点协作服务共享编解码：JSON、SHA-256、站点/媒体标签。
 */
@Component
public class HotNewsCodec {

  /** 配置/快照 JSON（Jackson）。 */
  private final JacksonJsonSupport jackson;

  /**
   * @param objectMapper Spring ObjectMapper
   */
  public HotNewsCodec(ObjectMapper objectMapper) {
    this.jackson = new JacksonJsonSupport(objectMapper);
  }

  /**
   * 解析任务入参等 Map；空白或失败返回不可变空 Map。
   *
   * @param inputJson JSON 文本
   * @return Map
   */
  public Map<String, Object> parseJsonMap(String inputJson) {
    return jackson.readMapOrImmutableEmpty(inputJson);
  }

  /**
   * 对象转字符串。
   *
   * @param value 任意值
   * @return 字符串；空则为 null
   */
  public static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 将列表元素转为非空白字符串列表。
   *
   * @param value 任意值
   * @return 字符串列表
   */
  public static List<String> asStringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object entry : list) {
      if (entry != null && StrUtil.isNotBlank(String.valueOf(entry))) {
        out.add(String.valueOf(entry));
      }
    }
    return out;
  }

  /**
   * 解析为可变 LinkedHashMap；空白或失败返回空 Map。
   *
   * @param json JSON 文本
   * @return Map
   */
  public Map<String, Object> readMap(String json) {
    return jackson.readMapOrEmpty(json);
  }

  /**
   * 解析选题评分明细。
   *
   * @param json score_details_json
   * @return 明细对象，永不为 null
   */
  public TopicScoreDetails readScoreDetails(String json) {
    if (StrUtil.isBlank(json)) {
      return new TopicScoreDetails();
    }
    try {
      // 评分明细进入提示词路径保持 Hutool
      TopicScoreDetails details = JSONUtil.toBean(json, TopicScoreDetails.class);
      return details == null ? new TopicScoreDetails() : details;
    } catch (Exception ignored) {
      return new TopicScoreDetails();
    }
  }

  /**
   * 将 JSON 数组字符串解析为 List&lt;Map&gt;；空串或解析失败返回空列表。
   *
   * @param json JSON 数组文本
   * @return 列表结果；失败不抛异常
   */
  public List<Map<String, Object>> readList(String json) {
    return jackson.readMapListOrEmpty(json);
  }

  /**
   * 将来源快照 JSON 解析为类型化行列表。
   *
   * @param json JSON 数组文本
   * @return 行列表；失败返回空列表
   */
  public List<HotNewsBriefSourceRow> readBriefSourceRows(String json) {
    List<HotNewsBriefSourceRow> rows =
        jackson.readListOrEmpty(json, new TypeReference<List<HotNewsBriefSourceRow>>() {});
    return rows == null ? List.of() : rows;
  }

  /**
   * 将对象序列化为 JSON 字符串；失败时按类型回退为 {@code []} 或 {@code {}}。
   *
   * @param value 待序列化对象
   * @return JSON 文本
   */
  public String writeJson(Object value) {
    return jackson.writeLenient(value);
  }

  /**
   * 解析站点展示名：URL 能识别时优先生效，否则回退库内值。
   *
   * @param url         资料链接
   * @param storedLabel 库内站点标签
   * @return 站点展示名
   */
  public static String resolveSiteLabel(String url, String storedLabel) {
    String fromUrl = ResearchContentClassifier.siteLabel(url);
    if (ResearchContentClassifier.isKnownSite(fromUrl)) {
      return fromUrl;
    }
    return StrUtil.blankToDefault(storedLabel, fromUrl);
  }

  /**
   * 解析媒体类型：有 URL/标题时按链接形态识别，避免存量「文章」误标视频。
   *
   * @param url         资料链接
   * @param title       标题
   * @param storedMedia 库内媒体类型
   * @return article 或 video
   */
  public static String resolveMediaType(String url, String title, String storedMedia) {
    if (StrUtil.isNotBlank(url) || StrUtil.isNotBlank(title)) {
      return ResearchContentClassifier.isVideo(url, title)
          ? SourceMediaType.VIDEO.getValue()
          : SourceMediaType.ARTICLE.getValue();
    }
    return SourceMediaType.from(storedMedia).getValue();
  }

  /**
   * 计算 UTF-8 文本的 SHA-256 十六进制摘要；算法异常时返回空串。
   *
   * @param value 原文；调用方通常已对 null 做过处理
   * @return 小写十六进制 hash，或异常时的 ""
   */
  public static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }
}
