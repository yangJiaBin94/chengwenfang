package com.chengwenfang.ai.facade;

import java.util.List;

/** 文本嵌入门面：批量与单向量嵌入。 */
public interface AiEmbeddingFacade {

  /**
   * 批量嵌入。
   *
   * @param texts 待嵌入文本列表
   * @return 与输入顺序一致的向量列表
   */
  List<float[]> embed(List<String> texts);

  /**
   * 单条嵌入。
   *
   * @param text 待嵌入文本
   * @return 向量
   */
  float[] embedOne(String text);

  /**
   * 当前配置的嵌入模型名。
   *
   * @return 模型名
   */
  String embeddingModelName();
}
