package com.chengwenfang.knowledge.application.connector;

import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Component;

/** 调研资料条目入库连接器（依赖登记时写入的 text_file）。 */
@Component
public class ResearchItemSourceConnector extends StoredTextSourceConnector {

  /**
   * @param documentMapper 文档 Mapper
   * @param fileFacade     文件门面
   * @param ownerScope     用户数据范围
   */
  public ResearchItemSourceConnector(
      KnowledgeDocumentMapper documentMapper, FileFacade fileFacade, OwnerScope ownerScope) {
    super(documentMapper, fileFacade, ownerScope, KnowledgeSourceKind.RESEARCH_ITEM);
  }
}
