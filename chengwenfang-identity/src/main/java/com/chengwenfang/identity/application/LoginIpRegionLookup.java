package com.chengwenfang.identity.application;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 将登录 IP 解析为市级属地文案；内网/本机本地判定，公网短超时查询。
 */
@Slf4j
final class LoginIpRegionLookup {
  /** 公网属地查询超时毫秒。 */
  private static final int LOOKUP_TIMEOUT_MS = 800;

  private LoginIpRegionLookup() {
  }

  /**
   * 解析市级属地。
   *
   * @param clientIp 客户端 IP，可空
   * @return 市级文案；无法解析时为空串
   */
  static String resolveCity(String clientIp) {
    String ip = StrUtil.trim(clientIp);
    if (StrUtil.isBlank(ip)) {
      return "";
    }
    if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
      return "本机";
    }
    // IPv4 内网直接返回，避免无意义的公网查询
    if (NetUtil.isInnerIP(ip)) {
      return "内网";
    }
    try {
      String body = HttpUtil.get(
          "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,regionName,city",
          LOOKUP_TIMEOUT_MS);
      if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
        return "";
      }
      JSONObject json = JSONUtil.parseObj(body);
      if (!"success".equalsIgnoreCase(json.getStr("status"))) {
        return "";
      }
      String city = StrUtil.trim(json.getStr("city"));
      String regionName = StrUtil.trim(json.getStr("regionName"));
      // 只要市级；城市为空时退到省级
      if (StrUtil.isNotBlank(city)) {
        return city;
      }
      return StrUtil.nullToEmpty(regionName);
    } catch (RuntimeException exception) {
      log.debug("登录 IP 属地解析失败 ip={} message={}", ip, exception.getMessage());
      return "";
    }
  }
}
