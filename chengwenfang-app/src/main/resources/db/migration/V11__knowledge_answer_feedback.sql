-- 知识库答案赞踩反馈（难例人工审后进金标，不自动改 CI）
CREATE TABLE IF NOT EXISTS knowledge_answer_feedback (
  id            VARCHAR(32)  NOT NULL PRIMARY KEY,
  message_id    VARCHAR(32)  NOT NULL,
  session_id    VARCHAR(32)  NOT NULL,
  rating        VARCHAR(16)  NOT NULL COMMENT 'up / down',
  note          VARCHAR(1000) NULL,
  created_at    DATETIME(3)  NOT NULL,
  KEY idx_knowledge_feedback_message (message_id),
  KEY idx_knowledge_feedback_session (session_id),
  KEY idx_knowledge_feedback_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
