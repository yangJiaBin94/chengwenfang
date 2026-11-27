package com.chengwenfang.identity.application;

import cn.hutool.core.util.StrUtil;

/**
 * 头像 URL 组装（不依赖 file 模块）。
 */
final class AvatarUrls {
  private AvatarUrls() {}

  /**
   * 将文件 ID 转为下载路径。
   *
   * @param avatarFileId 文件 ID，可空
   * @return `/api/files/{id}` 或 null
   */
  static String fromFileId(String avatarFileId) {
    if (StrUtil.isBlank(avatarFileId)) {
      return null;
    }
    return "/api/files/" + avatarFileId;
  }
}
