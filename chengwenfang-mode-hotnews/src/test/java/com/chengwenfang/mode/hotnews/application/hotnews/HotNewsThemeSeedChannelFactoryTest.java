package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.content.facade.constant.HotNewsThemeSeedChannelKeys;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link HotNewsThemeSeedChannelFactory} 固定名与请求组装。 */
class HotNewsThemeSeedChannelFactoryTest {

  @Test
  void buildChannelRequest_searxngUsesFixedDisplayNameAndQueries() {
    HotNewsChannelCreateRequest request = HotNewsThemeSeedChannelFactory.buildChannelRequest(
        HotNewsThemeSeedChannelKeys.SEARXNG, List.of("词A", "词B"), null);
    assertThat(request).isNotNull();
    assertThat(request.getName()).isEqualTo(HotNewsThemeSeedChannelKeys.SEARXNG_DISPLAY_NAME);
    assertThat(request.getConfig().getQueries()).containsExactly("词A", "词B");
  }

  @Test
  void buildChannelRequest_githubWithoutQueryReturnsNull() {
    assertThat(HotNewsThemeSeedChannelFactory.buildChannelRequest(
            HotNewsThemeSeedChannelKeys.GITHUB, List.of(), "  "))
        .isNull();
  }
}
