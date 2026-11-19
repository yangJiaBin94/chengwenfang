package com.chengwenfang.knowledge.application.connector;

import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Component;

/** 雷达信源条目入库连接器（依赖登记时写入的 text_file）。 */
@Component
public class SourceItemSourceConnector extends StoredTextSourceConnector {

  /**
   * @param documentMapper 文档 Mapper
   * @param fileFacade     文件门面
   */
  public SourceItemSourceConnector(KnowledgeDocumentMapper documentMapper, FileFacade fileFacade) {
    super(documentMapper, fileFacade, KnowledgeSourceKind.SOURCE_ITEM);
  }
}
