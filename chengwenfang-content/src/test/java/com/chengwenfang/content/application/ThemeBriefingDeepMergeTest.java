package com.chengwenfang.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ThemeBriefingDeepMerge} 与旧 ThemeService.deepMerge 语义对齐单测。
 */
class ThemeBriefingDeepMergeTest {

  @Test
  void partialNestedUpdate_preservesSiblings() {
    Map<String, Object> target = new LinkedHashMap<>();
    Map<String, Object> naming = new LinkedHashMap<>();
    naming.put("name", "旧名");
    naming.put("description", "旧描述");
    naming.put("code", "old_code");
    target.put("naming", naming);
    target.put("step", 2);

    Map<String, Object> patch = new LinkedHashMap<>();
    Map<String, Object> namingPatch = new LinkedHashMap<>();
    namingPatch.put("name", "新名");
    patch.put("naming", namingPatch);

    ThemeBriefingDeepMerge.merge(target, patch);

    @SuppressWarnings("unchecked")
    Map<String, Object> mergedNaming = (Map<String, Object>) target.get("naming");
    assertThat(mergedNaming.get("name")).isEqualTo("新名");
    assertThat(mergedNaming.get("description")).isEqualTo("旧描述");
    assertThat(mergedNaming.get("code")).isEqualTo("old_code");
    assertThat(target.get("step")).isEqualTo(2);
  }

  @Test
  void listReplacedAsWhole() {
    Map<String, Object> target = new LinkedHashMap<>();
    Map<String, Object> audience = new LinkedHashMap<>();
    audience.put("readers", new ArrayList<>(List.of("a", "b")));
    target.put("audience", audience);

    Map<String, Object> patch = new LinkedHashMap<>();
    Map<String, Object> audiencePatch = new LinkedHashMap<>();
    audiencePatch.put("readers", List.of("c"));
    patch.put("audience", audiencePatch);

    ThemeBriefingDeepMerge.merge(target, patch);

    @SuppressWarnings("unchecked")
    Map<String, Object> mergedAudience = (Map<String, Object>) target.get("audience");
    assertThat(mergedAudience.get("readers")).isEqualTo(List.of("c"));
  }

  @Test
  void nullOverwritesValue() {
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("tone", "chatty_tech");

    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("tone", null);

    ThemeBriefingDeepMerge.merge(target, patch);

    assertThat(target).containsEntry("tone", null);
  }
}
