package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 文章配图槽位：固定「第几张」标识（封面为第 1 张，正文插图依次为第 2…N 张）。
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class HotNewsImageSlot {
  /** 展示用序号，从 1 起。 */
  private final int slotIndex;
  /** 本篇文章计划总张数。 */
  private final int totalSlots;
  /** 落库 kind。 */
  private final HotNewsImageKind kind;
  /** 落库 sequenceNo（封面为 0，正文从 1 起）。 */
  private final int sequenceNo;

  /**
   * 按插图档位展开全部槽位（先封面，再正文）。
   *
   * @param inlineCount 正文插图张数（不含封面）
   * @return 有序槽位列表
   */
  public static List<HotNewsImageSlot> plan(int inlineCount) {
    int safeInline = Math.max(0, inlineCount);
    int total = 1 + safeInline;
    List<HotNewsImageSlot> slots = new ArrayList<>(total);
    slots.add(new HotNewsImageSlot(1, total, HotNewsImageKind.COVER, 0));
    for (int sequence = 1; sequence <= safeInline; sequence++) {
      slots.add(new HotNewsImageSlot(1 + sequence, total, HotNewsImageKind.INLINE, sequence));
    }
    return List.copyOf(slots);
  }
}
