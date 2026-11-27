package com.chengwenfang.app.application.identity;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.identity.facade.AuthFacade;
import com.chengwenfang.identity.facade.constant.AvatarRules;
import com.chengwenfang.identity.facade.dto.view.CurrentUserView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 当前用户头像编排：校验魔数、落盘、绑定；旧文件在事务提交后再删；失败时补偿删除新文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeAvatarService {
  private final AuthFacade authFacade;
  private final FileFacade fileFacade;

  /**
   * 上传并绑定头像。
   *
   * @param fileName           原始文件名，可空
   * @param claimedContentType 客户端声明 MIME，可空
   * @param bytes              文件字节
   * @return 更新后的当前用户
   */
  @Transactional
  public CurrentUserView uploadAvatar(String fileName, String claimedContentType, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new BusinessException(ErrorCodes.AVATAR_UPLOAD_REJECTED, "请选择头像图片", 400);
    }
    if (bytes.length > AvatarRules.MAX_BYTES) {
      throw new BusinessException(ErrorCodes.AVATAR_UPLOAD_REJECTED, "头像不能超过 2MB", 400);
    }
    String detectedMime = AvatarRules.detectMime(bytes);
    if (detectedMime == null || !AvatarRules.ALLOWED_CONTENT_TYPES.contains(detectedMime)) {
      throw new BusinessException(ErrorCodes.AVATAR_UPLOAD_REJECTED, "仅支持 jpg / png / webp", 400);
    }
    if (!AvatarRules.matchesClaimed(claimedContentType, detectedMime)) {
      throw new BusinessException(ErrorCodes.AVATAR_UPLOAD_REJECTED, "文件内容与类型不符", 400);
    }
    String originalName = StrUtil.blankToDefault(fileName, "avatar." + extensionOf(detectedMime));
    String objectKey = "avatars/" + IdsUtil.newId() + "/" + originalName;
    FileView stored = fileFacade.saveLocalBytes(objectKey, originalName, detectedMime, bytes);
    try {
      String previousFileId = authFacade.replaceAvatarFile(stored.getId());
      // 旧文件等事务提交后再删，避免回滚后库仍指向已删文件
      if (StrUtil.isNotBlank(previousFileId) && !previousFileId.equals(stored.getId())) {
        scheduleDeleteAfterCommit(previousFileId);
      }
      CurrentUserView user = authFacade.currentUser();
      if (user == null) {
        throw new BusinessException(ErrorCodes.UNAUTHORIZED, "请先登录", 401);
      }
      return user;
    } catch (RuntimeException exception) {
      // 绑定失败：补偿删除本次新文件，避免孤儿
      safeDelete(stored.getId());
      throw exception;
    }
  }

  /**
   * 事务提交后再删文件；无事务时立即删。
   *
   * @param fileId 待删文件 ID
   */
  private void scheduleDeleteAfterCommit(String fileId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      safeDelete(fileId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            safeDelete(fileId);
          }
        });
  }

  /**
   * 尽力删除文件，失败只打日志。
   *
   * @param fileId 文件 ID
   */
  private void safeDelete(String fileId) {
    try {
      fileFacade.delete(fileId);
    } catch (RuntimeException exception) {
      log.warn("删除头像文件失败 fileId={}", fileId, exception);
    }
  }

  /**
   * MIME 对应扩展名。
   *
   * @param mime 已识别 MIME
   * @return 扩展名
   */
  private static String extensionOf(String mime) {
    if ("image/png".equals(mime)) {
      return "png";
    }
    if ("image/webp".equals(mime)) {
      return "webp";
    }
    return "jpg";
  }
}
