package com.chengwenfang.app.application.generation.custom;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.skill.facade.constant.CustomAiNodeActions;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 解析自定义节点模型输出的白名单动作 JSON。
 */
@Component
public class CustomAiActionPlanParser {

  /**
   * 从模型原始文本中解析动作列表；非法 JSON 或未知 op 抛错。
   *
   * @param raw 模型输出
   * @return 动作列表（可能为空列表，但不为 null）
   */
  public List<CustomAiParsedAction> parse(String raw) {
    String text = StrUtil.trim(StrUtil.nullToEmpty(raw));
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("自定义节点输出为空，无法解析动作");
    }
    // 允许模型包一层 markdown 代码块
    String json = stripCodeFence(text);
    JSONObject root;
    try {
      root = JSONUtil.parseObj(json);
    } catch (Exception exception) {
      throw new IllegalStateException("自定义节点输出不是合法 JSON：" + exception.getMessage(), exception);
    }
    Object actionsRaw = root.get(CustomAiNodeActions.FIELD_ACTIONS);
    if (!(actionsRaw instanceof JSONArray actions)) {
      throw new IllegalStateException("自定义节点输出缺少 actions 数组");
    }
    List<CustomAiParsedAction> parsed = new ArrayList<>();
    for (int index = 0; index < actions.size(); index++) {
      Object item = actions.get(index);
      if (!(item instanceof JSONObject actionObj)) {
        throw new IllegalStateException("自定义节点 actions[" + index + "] 必须是对象");
      }
      String op = StrUtil.trim(StrUtil.nullToEmpty(actionObj.getStr(CustomAiNodeActions.FIELD_OP)));
      if (!CustomAiNodeActions.isAllowed(op)) {
        throw new IllegalStateException("自定义节点存在越界动作 op=" + op);
      }
      parsed.add(CustomAiParsedAction.builder()
          .op(op)
          .content(StrUtil.nullToEmpty(actionObj.getStr(CustomAiNodeActions.FIELD_CONTENT)))
          .skillId(StrUtil.nullToEmpty(actionObj.getStr(CustomAiNodeActions.FIELD_SKILL_ID)))
          .build());
    }
    return parsed;
  }

  /**
   * 去掉 ```json ... ``` 包裹。
   *
   * @param text 原始文本
   * @return 尽量纯净的 JSON 串
   */
  private String stripCodeFence(String text) {
    String trimmed = text.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    int firstNl = trimmed.indexOf('\n');
    if (firstNl < 0) {
      return trimmed;
    }
    String body = trimmed.substring(firstNl + 1);
    int fence = body.lastIndexOf("```");
    if (fence >= 0) {
      body = body.substring(0, fence);
    }
    return body.trim();
  }
}
