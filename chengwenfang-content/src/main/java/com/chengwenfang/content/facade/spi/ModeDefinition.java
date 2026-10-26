package com.chengwenfang.content.facade.spi;

import com.chengwenfang.content.facade.dto.DefaultOutputSpec;
import com.chengwenfang.content.facade.dto.ModeWorkNavInfo;
import com.chengwenfang.content.facade.dto.WorkExtensionPayload;
import java.util.List;

/**
 * 内容模式扩展点：声明模式编码、默认产出组合，以及作品扩展数据的创建/删除与导航信息。
 */
public interface ModeDefinition {

  /**
   * 模式编码（与 content_modes.code 一致）。
   *
   * @return 模式 code
   */
  String modeCode();

  /**
   * 创建作品时默认保证存在的产出组合（渠道 × 格式）。
   *
   * @return 默认产出规格列表；顺序稳定
   */
  List<DefaultOutputSpec> defaultOutputs();

  /**
   * 创建或补齐模式扩展表行（课次/热点作品等）。
   *
   * @param payload 扩展入参
   */
  void createExtension(WorkExtensionPayload payload);

  /**
   * 删除模式扩展表行（不删 works / work_outputs，由调用方或外键处理）。
   *
   * @param workId 作品 ID
   */
  void deleteExtension(String workId);

  /**
   * 解析作品标题与跳转所需信息。
   *
   * @param workId 作品 ID
   * @return 导航信息；作品不存在时为 null
   */
  ModeWorkNavInfo resolveNavInfo(String workId);
}
