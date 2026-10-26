package com.workbench.sourcing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workbench.crawler.facade.dto.request.ChannelFetchConfig;
import com.workbench.infra.support.SecretCipher;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import com.workbench.sourcing.domain.entity.SourceChannelValueEntity;
import com.workbench.sourcing.facade.constant.SourceChannelValueTypes;
import com.workbench.sourcing.infrastructure.mapper.SourceChannelValueMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ChannelFetchConfigStore} 空配置 / 完整配置往返与 apiKey 加密。
 */
@ExtendWith(MockitoExtension.class)
class ChannelFetchConfigStoreTest {

  @Mock
  private SourceChannelValueMapper valueMapper;

  private SecretCipher secretCipher;
  private ChannelFetchConfigStore store;

  @BeforeEach
  void setUp() {
    secretCipher = new SecretCipher("unit-test-secret-key-please-rotate");
    store = new ChannelFetchConfigStore(valueMapper, secretCipher);
  }

  @Test
  void read_without_has_config_returns_empty() {
    SourceChannelEntity channel = SourceChannelEntity.builder()
        .id("c1")
        .hasConfig(false)
        .build();

    ChannelFetchConfig config = store.read(channel);

    assertThat(config.getQuery()).isNull();
    assertThat(config.getQueries()).isEmpty();
    assertThat(config.getUrls()).isEmpty();
    assertThat(config.getApiKey()).isNull();
    verify(valueMapper, never()).selectList(any());
  }

  @Test
  void write_and_read_roundtrip_with_lists_and_api_key() {
    SourceChannelEntity channel = SourceChannelEntity.builder().id("c2").build();
    ChannelFetchConfig incoming = ChannelFetchConfig.builder()
        .query("primary")
        .queries(List.of("q1", "q2"))
        .urls(List.of("https://a.example/", "https://b.example/"))
        .maxResults(10)
        .topic("news")
        .searchDepth("basic")
        .apiKey("sk-secret-key-value")
        .dateField("created")
        .sort("stars")
        .build();

    store.write(channel, incoming, null);

    assertThat(channel.getHasConfig()).isTrue();
    assertThat(channel.getPrimaryQuery()).isEqualTo("primary");
    assertThat(channel.getMaxResults()).isEqualTo(10);
    assertThat(channel.getSearchTopic()).isEqualTo("news");
    assertThat(channel.getSearchDepth()).isEqualTo("basic");
    assertThat(channel.getDateField()).isEqualTo("created");
    assertThat(channel.getSortMode()).isEqualTo("stars");
    assertThat(channel.getApiKeyCiphertext()).isNotBlank();
    assertThat(channel.getApiKeyCiphertext()).doesNotContain("sk-secret-key-value");

    ArgumentCaptor<SourceChannelValueEntity> valueCaptor =
        ArgumentCaptor.forClass(SourceChannelValueEntity.class);
    verify(valueMapper, org.mockito.Mockito.atLeast(4)).insert(valueCaptor.capture());
    List<SourceChannelValueEntity> inserted = valueCaptor.getAllValues();
    assertThat(inserted).anyMatch(row ->
        SourceChannelValueTypes.QUERY.equals(row.getValueType()) && "q1".equals(row.getValue()));
    assertThat(inserted).anyMatch(row ->
        SourceChannelValueTypes.URL.equals(row.getValueType())
            && "https://a.example/".equals(row.getValue()));

    when(valueMapper.selectList(any())).thenReturn(List.of(
        SourceChannelValueEntity.builder()
            .channelId("c2")
            .valueType(SourceChannelValueTypes.QUERY)
            .seq(0)
            .value("q1")
            .build(),
        SourceChannelValueEntity.builder()
            .channelId("c2")
            .valueType(SourceChannelValueTypes.QUERY)
            .seq(1)
            .value("q2")
            .build(),
        SourceChannelValueEntity.builder()
            .channelId("c2")
            .valueType(SourceChannelValueTypes.URL)
            .seq(0)
            .value("https://a.example/")
            .build(),
        SourceChannelValueEntity.builder()
            .channelId("c2")
            .valueType(SourceChannelValueTypes.URL)
            .seq(1)
            .value("https://b.example/")
            .build()));

    ChannelFetchConfig read = store.read(channel);
    assertThat(read.getQuery()).isEqualTo("primary");
    assertThat(read.getQueries()).containsExactly("q1", "q2");
    assertThat(read.getUrls()).containsExactly("https://a.example/", "https://b.example/");
    assertThat(read.getMaxResults()).isEqualTo(10);
    assertThat(read.getTopic()).isEqualTo("news");
    assertThat(read.getSearchDepth()).isEqualTo("basic");
    assertThat(read.getDateField()).isEqualTo("created");
    assertThat(read.getSort()).isEqualTo("stars");
    assertThat(read.getApiKey()).isEqualTo("sk-secret-key-value");
  }

  @Test
  void write_blank_api_key_keeps_previous() {
    SourceChannelEntity channel = SourceChannelEntity.builder().id("c3").build();
    ChannelFetchConfig previous = ChannelFetchConfig.builder()
        .query("keep")
        .apiKey("old-key-value")
        .build();
    ChannelFetchConfig incoming = ChannelFetchConfig.builder()
        .query("keep")
        .apiKey("  ")
        .build();

    store.write(channel, incoming, previous);

    assertThat(secretCipher.decrypt(channel.getApiKeyCiphertext())).isEqualTo("old-key-value");
  }
}
