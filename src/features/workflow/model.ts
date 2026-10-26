/** 自定义 AI 节点 typeKey（与后端 WorkflowStructuralNodeKeys.CUSTOM_AI 对齐）。 */
export const CUSTOM_AI_TYPE_KEY = "custom_ai";

/** 是否为可插拔的自定义 AI 节点。 */
export function isCustomAiNode(node: { typeKey: string }): boolean {
  return node.typeKey === CUSTOM_AI_TYPE_KEY;
}
