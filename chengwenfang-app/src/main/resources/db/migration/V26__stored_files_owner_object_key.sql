-- stored_files：同用户下 (storage_type, object_key) 唯一；查询按 owner 收窄

-- 同用户重复 key：保留 id 更大的一行
DELETE t1 FROM `stored_files` t1
INNER JOIN `stored_files` t2
  ON t1.`owner_user_id` = t2.`owner_user_id`
 AND t1.`storage_type` = t2.`storage_type`
 AND t1.`object_key` = t2.`object_key`
 AND t1.`id` < t2.`id`;

ALTER TABLE `stored_files`
  ADD UNIQUE KEY `uk_stored_files_owner_type_key` (`owner_user_id`, `storage_type`, `object_key`(255));
