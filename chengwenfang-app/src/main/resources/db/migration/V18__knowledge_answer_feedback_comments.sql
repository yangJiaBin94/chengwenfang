-- 补齐知识库反馈表注释。不改列类型（V13 已把主键与外键收成 char(36)）。
ALTER TABLE `knowledge_answer_feedback` COMMENT = '知识库答案赞踩反馈';

ALTER TABLE `knowledge_answer_feedback`
  MODIFY COLUMN `id` char(36) NOT NULL COMMENT '主键',
  MODIFY COLUMN `message_id` char(36) NOT NULL COMMENT '助手消息 ID',
  MODIFY COLUMN `session_id` char(36) NOT NULL COMMENT '会话 ID',
  MODIFY COLUMN `note` varchar(1000) NULL COMMENT '踩时的难例说明',
  MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '创建时间';
