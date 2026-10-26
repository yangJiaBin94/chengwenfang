package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.hutool.json.JSONUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link HotNewsGraphState} 与 Map 互转：骨架反序列化。
 */
class HotNewsGraphStateTest {

  @Test
  void fromMap_rehydratesSkeletonFromNestedMap() {
    Map<String, Object> skeletonMap = new LinkedHashMap<>();
    skeletonMap.put("topicId", "t1");
    skeletonMap.put("illustrationStyle", "科技立体");
    skeletonMap.put("coverBrief", "冲突封面");
    skeletonMap.put("imagePromptRequirements", "3～5 个信息元素，忌空装饰");
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put(HotNewsGraphStateKeys.ARTICLE_ID, "a1");
    raw.put(HotNewsGraphStateKeys.SKELETON, skeletonMap);
    raw.put(HotNewsGraphStateKeys.SKELETON_JSON, "{\"topicId\":\"t1\"}");

    HotNewsGraphState state = HotNewsGraphState.fromMap(raw);

    assertNotNull(state.getSkeleton());
    assertEquals("科技立体", state.getSkeleton().getIllustrationStyle());
    assertEquals("冲突封面", state.getSkeleton().getCoverBrief());
    assertEquals("3～5 个信息元素，忌空装饰", state.getSkeleton().getImagePromptRequirements());
  }

  @Test
  void fromMap_rehydratesSkeletonFromJsonWhenObjectMissing() {
    HotNewsArticleSkeletonView skeleton = HotNewsArticleSkeletonView.builder()
        .topicId("t2")
        .illustrationStyle("微立体")
        .imagePromptRequirements("主题可读")
        .build();
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put(HotNewsGraphStateKeys.SKELETON_JSON, JSONUtil.toJsonStr(skeleton));

    HotNewsGraphState state = HotNewsGraphState.fromMap(raw);

    assertNotNull(state.getSkeleton());
    assertEquals("微立体", state.getSkeleton().getIllustrationStyle());
    assertEquals("主题可读", state.getSkeleton().getImagePromptRequirements());
  }
}
