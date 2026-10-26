package com.chengwenfang.sourcing.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.crawler.facade.dto.request.ChannelFetchConfig;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsChannelQuotaSupport} 配额校验。 */
class HotNewsChannelQuotaSupportTest {

  @Test
  void assertCanCreate_rejectsAtLimit() {
    assertThatThrownBy(
            () -> HotNewsChannelQuotaSupport.assertCanCreate(HotNewsChannelLimits.MAX_CHANNELS_PER_THEME))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo(ErrorCodes.CHANNEL_LIMIT_EXCEEDED);
  }

  @Test
  void assertCanCreate_allowsBelowLimit() {
    HotNewsChannelQuotaSupport.assertCanCreate(HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - 1);
  }

  @Test
  void assertWebUrlLimit_rejectsOverFive() {
    List<String> urls = new ArrayList<>();
    for (int index = 1; index <= HotNewsChannelLimits.MAX_WEB_URLS_PER_CHANNEL + 1; index++) {
      urls.add("https://example.com/" + index);
    }
    ChannelFetchConfig config = ChannelFetchConfig.builder().urls(urls).build();
    assertThatThrownBy(
            () -> HotNewsChannelQuotaSupport.assertWebUrlLimit(
                ChannelType.WEB.getValue(), config, urls.get(0)))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getCode())
        .isEqualTo(ErrorCodes.CHANNEL_WEB_URLS_LIMIT_EXCEEDED);
  }

  @Test
  void dedupeUrls_ignoresTrailingSlash() {
    assertThat(HotNewsChannelQuotaSupport.dedupeUrls(List.of(
            "https://a.example/x",
            "https://a.example/x/",
            "https://b.example/y")))
        .containsExactly("https://a.example/x", "https://b.example/y");
  }
}
