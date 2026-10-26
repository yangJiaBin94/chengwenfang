package com.chengwenfang.publishing.facade.spi;

import com.chengwenfang.publishing.facade.dto.request.WechatMpDraftPushRequest;
import com.chengwenfang.publishing.facade.dto.view.WechatMpDraftPushResult;
import java.util.function.Function;

/**
 * 渠道发布扩展点：按渠道编码推送产出并记录发布结果。
 *
 * <p>首批实现为公众号草稿推送；新增渠道时实现本接口并注册即可。
 */
public interface ChannelPublisher {

  /**
   * 渠道编码（与 {@code channels.code} 一致）。
   *
   * @return 渠道 code
   */
  String channelCode();

  /**
   * 将产出推送到该渠道。
   *
   * @param request          推送请求（Markdown、主题、标题等）
   * @param imageBytesLoader 按图片 src 加载字节；找不到返回 null
   * @return 推送结果
   */
  WechatMpDraftPushResult publish(
      WechatMpDraftPushRequest request, Function<String, byte[]> imageBytesLoader);
}
