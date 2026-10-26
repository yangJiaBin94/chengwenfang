package com.workbench.publishing.infrastructure.wechat;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.publishing.facade.constant.WechatMpApiPaths;
import com.workbench.publishing.facade.constant.WechatMpErrorCodes;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 草稿 update 请求体形状与 media_id 失效错误码映射。 */
class WechatMpApiClientDraftUpdateTest {

  @Test
  void draftUpdatePath_isOfficialEndpoint() {
    assertThat(WechatMpApiPaths.DRAFT_UPDATE).isEqualTo("/cgi-bin/draft/update");
  }

  @Test
  void buildNewsArticle_containsRequiredDraftFields() throws Exception {
    WechatMpApiClient client = new WechatMpApiClient(new WorkbenchProperties());
    Method method =
        WechatMpApiClient.class.getDeclaredMethod(
            "buildNewsArticle",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> article =
        (Map<String, Object>)
            method.invoke(client, "标题", "作者", "摘要", "<p>正文</p>", "thumb_media");
    assertThat(article)
        .containsEntry("article_type", "news")
        .containsEntry("title", "标题")
        .containsEntry("author", "作者")
        .containsEntry("digest", "摘要")
        .containsEntry("content", "<p>正文</p>")
        .containsEntry("thumb_media_id", "thumb_media")
        .containsEntry("need_open_comment", 0)
        .containsEntry("only_fans_can_comment", 0);
  }

  @Test
  void wechatError_mapsInvalidMediaIdToDraftGone() throws Exception {
    WechatMpApiClient client = new WechatMpApiClient(new WorkbenchProperties());
    Method method =
        WechatMpApiClient.class.getDeclaredMethod("wechatError", String.class, JSONObject.class);
    method.setAccessible(true);
    JSONObject json =
        JSONUtil.createObj()
            .set("errcode", WechatMpErrorCodes.INVALID_MEDIA_ID)
            .set("errmsg", "invalid media_id");
    BusinessException exception =
        (BusinessException) method.invoke(client, "更新草稿失败", json);
    assertThat(exception.getCode()).isEqualTo(ErrorCodes.WECHAT_MP_DRAFT_GONE);
    assertThat(exception.getHttpStatus()).isEqualTo(404);
  }
}
