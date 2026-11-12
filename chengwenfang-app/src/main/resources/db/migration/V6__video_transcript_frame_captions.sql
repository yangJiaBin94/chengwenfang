-- 视频抽帧逐帧文字描述，供成文佐证时由 AI 选型
ALTER TABLE `video_transcripts`
  ADD COLUMN `frame_captions` json DEFAULT NULL
    COMMENT '与 frame_file_ids 等长的逐帧中文描述 JSON 数组' AFTER `frame_file_ids`;
