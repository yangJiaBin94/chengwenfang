-- 产品 Skill → PromptPack（提示词包）：表名与列名更名。
-- JSON 内键名（promptPackBindings 等）由启动期 Java 迁移器处理。

-- 1) 主表
RENAME TABLE `skills` TO `prompt_packs`;
ALTER TABLE `prompt_packs`
  CHANGE COLUMN `skill_type` `prompt_pack_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提示词包类型',
  COMMENT = 'PromptPack（提示词包：可组合提示词定义）';

-- 2) 默认节点绑定（先丢旧 FK，改名后再建）
ALTER TABLE `skill_default_bindings`
  DROP FOREIGN KEY `fk_skill_bindings_skill`;
RENAME TABLE `skill_default_bindings` TO `prompt_pack_default_bindings`;
ALTER TABLE `prompt_pack_default_bindings`
  CHANGE COLUMN `skill_id` `prompt_pack_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词包 ID',
  DROP INDEX `fk_skill_bindings_skill`,
  DROP INDEX `idx_skill_default_bindings_node`,
  ADD KEY `fk_prompt_pack_bindings_pack` (`prompt_pack_id`),
  ADD KEY `idx_prompt_pack_default_bindings_node` (`workflow_type`, `node_key`),
  ADD CONSTRAINT `fk_prompt_pack_bindings_pack`
    FOREIGN KEY (`prompt_pack_id`) REFERENCES `prompt_packs` (`id`) ON DELETE CASCADE,
  COMMENT = '提示词包与工作流节点的全局默认绑定';

-- 3) 工作流版本绑定快照
RENAME TABLE `workflow_version_skill_bindings` TO `workflow_version_prompt_pack_bindings`;
ALTER TABLE `workflow_version_prompt_pack_bindings`
  CHANGE COLUMN `skill_id` `prompt_pack_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提示词包 ID',
  CHANGE COLUMN `skill_version` `prompt_pack_version` int DEFAULT NULL COMMENT '提示词包版本号（预留）',
  COMMENT = '工作流版本的节点提示词包绑定快照';
