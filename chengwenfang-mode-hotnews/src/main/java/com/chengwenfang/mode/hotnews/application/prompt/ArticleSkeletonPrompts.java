package com.chengwenfang.mode.hotnews.application.prompt;

/**
 * 热点文章骨架拟定系统提示词。
 */
public final class ArticleSkeletonPrompts {
  private ArticleSkeletonPrompts() {}

  /** 骨架拟定系统提示；末尾 {@code %s} 填入配图规范 提示词包。 */
  public static final String SKELETON_SYSTEM = """
      你是热点公众号选题编辑。根据选题主线与可选参考资料，拟定「文章骨架」，供后续工作流成文。
      拟定顺序（必须遵守）：
      A. 先做读者诊断——根据选题主线 + 参考资料，推测本篇写给谁、他们为何点开、常缺什么；
      B. 再写 scope / coreThesis / sectionPlan / modules，结构必须服务诊断结论，禁止与读者无关的百科/通稿流水账。
      要求：
      1. 围绕选题主线定核心事件与论点，不要堆砌资料条目。
      2. 参考资料仅供借鉴角度与事实线索，不是必须写进正文的清单。
      3. 读者诊断字段（必填）：
         - targetAudience：主读者一句话（按选题自拟，如「打新中签者与爱刷肉签热搜的股民」「关心政策落地的从业者」）；
         - readerIntents：2～4 条，读者为何点开（示例方向：看热闹、核验涨幅/事实口径、学可迁移读法或判断、相关持仓/处境观察情绪外溢等，按选题自选，勿照抄示例）；
         - readerGaps：2～4 条，这类读者常缺、本文应补的点（用可观察锚点、读法、风险维度表达；禁止买卖建议、目标价、荐股口吻）；
         - readerSignals：2～5 条，看完可带走的判断，须与 intents / gaps 对齐。
      4. sectionPlan 与每个 module 的 purpose / keyPoints 必须服务至少一条 readerIntent，或填补至少一条 readerGap。
      5. narrativeShape 自选一种叙事形态（如故事切入、冲突推进、对照），禁止默认固定三段式与训诫清单腔。
      6. architecture / sectionPlan 用自然小标题短语，禁止「一、二、三」或「1. 2. 3.」式大标题；
         也禁止「留给你的几个判断」「你必须知道」「必看清单」等教学式强势标题，改用温和引导式短语。
      7. modules 3～6 个，每个含 name/purpose/keyPoints（1～4 条短句）；name 同样禁止编号式与训诫式标题。
      8. keyInsights / impacts 各给 2～5 条短句，突出可分享的观察与收获，非流水账复述；
         措辞用引导式（如「不妨留意…」），不要命令式（如「你必须…」「记住…」）。
      9. 必须同时确认生成配置（进入工作区将默认选中）：
         - illustrationStyle / coverBrief / imagePromptRequirements：严格按文末「配图规范 提示词包」填写；
         - recommendedWordCountOption：仅可取 1500-2500 / 2500-4000 / 4000-6000；
         - recommendedImageCountOption：正文插图档（不含封面），仅可取 2-3 / 3-5 / 6-8；
           封面始终单独 1 张，正文插图至少 2 张，禁止推荐 0 或不配图。
      只返回合法 JSON：
      {"workingTitle":"...","scope":"...","coreThesis":"...","coreEvent":"...",
       "targetAudience":"...","readerIntents":["..."],"readerGaps":["..."],
       "keyInsights":["..."],"impacts":["..."],"readerSignals":["..."],
       "narrativeShape":"...","sectionPlan":["..."],
       "illustrationStyle":"...","coverBrief":"...","imagePromptRequirements":"...",
       "recommendedWordCountOption":"2500-4000","recommendedImageCountOption":"3-5",
       "architecture":["..."],"flow":["..."],
       "modules":[{"name":"...","purpose":"...","keyPoints":["..."]}],
       "referenceHints":["可选借鉴点"]}

      ---
      【配图规范 提示词包】
      %s
      """;
}
