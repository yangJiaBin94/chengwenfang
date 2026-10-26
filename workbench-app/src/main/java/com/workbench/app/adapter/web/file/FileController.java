package com.workbench.app.adapter.web.file;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.ApiResult;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.SyncLearningContentView;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.FileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 文件下载与内容导入相关 HTTP 接口。 */
@Tag(name = "文件与导入", description = "存储文件下载与学习内容同步")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {
  /** 文件门面：元数据与本地路径解析。 */
  private final FileFacade files;
  /** 内容门面：学习内容同步入库。 */
  private final ContentFacade content;

  /**
   * 下载已存储文件。
   *
   * @param fileId 文件主键
   * @return 带 Content-Type 与缓存头的文件资源响应
   */
  @GetMapping("/files/{fileId}")
  @Operation(summary = "下载已存储文件")
  public ResponseEntity<Resource> getStoredFile(@PathVariable String fileId) {
    FileView meta = files.get(fileId);
    Path path = files.getLocalPath(fileId);
    FileSystemResource resource = new FileSystemResource(path);
    // 元数据无类型时按二进制流返回
    String contentType = StrUtil.isBlank(meta.getContentType())
        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
        : meta.getContentType();
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
        .contentType(MediaType.parseMediaType(contentType))
        .body(resource);
  }

  /**
   * 从学习内容 JSON 同步入库。
   *
   * @return 同步结果视图
   */
  @PostMapping("/migrations/sync-learning-content")
  @Operation(summary = "从学习内容 JSON 同步入库")
  public ApiResult<SyncLearningContentView> syncLearningContent() {
    return ApiResult.ok(content.syncLearningContent());
  }
}
