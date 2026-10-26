package com.workbench.content.facade;

/**
 * 作品产出正文存取：正文落文件，业务表只存 file_id；实现放 P4.4。
 *
 * <p>读写均为 UTF-8 原样字符串，不做换行或编码转换；版本只追加不覆盖。
 */
public interface WorkContentStore {

  /**
   * 读取产出当前版本正文。
   *
   * @param outputId 产出 ID（原 documentId）
   * @return 正文；无当前版本时由实现约定（空串或异常）
   */
  String readCurrent(String outputId);

  /**
   * 读取产出指定版本正文。
   *
   * @param outputId  产出 ID
   * @param versionNo 版本号
   * @return 正文
   */
  String readVersion(String outputId, int versionNo);

  /**
   * 追加一版正文：先写文件，再登记版本行并更新产出当前版本指针。
   *
   * @param outputId 产出 ID
   * @param text     正文字符串（原样）
   * @return 新版本号
   */
  int appendVersion(String outputId, String text);
}
