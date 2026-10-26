package com.chengwenfang.app.golden.record;

import cn.hutool.json.JSONUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从外部站点 WireMock 请求日志提取外部请求序列。
 */
public final class ExternalRecorder {
  private WireMockServer server;

  /**
   * 绑定 WireMock。
   *
   * @param server 外部服务；可空
   */
  public void bind(WireMockServer server) {
    this.server = server;
  }

  /**
   * 导出为 jsonl。
   *
   * @return jsonl
   */
  public String exportJsonl() {
    if (server == null) {
      return "";
    }
    List<LoggedRequest> requests = server.findAll(
        com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(
            com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    StringBuilder builder = new StringBuilder();
    for (LoggedRequest request : requests) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("method", request.getMethod().getName());
      row.put("url", request.getUrl());
      row.put("body", request.getBodyAsString());
      builder.append(JSONUtil.toJsonStr(row)).append('\n');
    }
    return builder.toString();
  }
}
