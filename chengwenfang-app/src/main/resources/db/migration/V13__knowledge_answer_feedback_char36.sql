-- 反馈表主键/会话 ID 与全局 IdsUtil（36 位）对齐；勿再改已应用的 V11

ALTER TABLE `knowledge_answer_feedback`
  MODIFY COLUMN `id` char(36) NOT NULL,
  MODIFY COLUMN `message_id` char(36) NOT NULL,
  MODIFY COLUMN `session_id` char(36) NOT NULL;
