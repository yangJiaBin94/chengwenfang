-- 蓝绿全量重建：落库 writing_version，消除首包窗口内增量仍写 active 的洞

ALTER TABLE `knowledge_index_state`
  ADD COLUMN `writing_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL
    COMMENT '全量重建进行中的写入版本；空表示无进行中的蓝绿写'
    AFTER `active_version`;
