-- 视频解析保留抽帧文件 id，供成文佐证复用
ALTER TABLE `video_transcripts`
  ADD COLUMN `frame_file_ids` json DEFAULT NULL
    COMMENT '抽帧截图 stored_files.id 列表 JSON' AFTER `text_file_id`;
