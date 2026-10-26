package com.workbench.file.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.file.domain.entity.StoredFileEntity;
import com.workbench.file.facade.StorageProvider;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.file.facade.enums.StorageType;
import com.workbench.file.infrastructure.mapper.StoredFileMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * {@link FileFacadeImpl#writeText}/{@link FileFacadeImpl#readText} UTF-8 原样往返。
 */
class FileFacadeTextRoundTripTest {

  @TempDir
  Path tempDir;

  private FileFacadeImpl files;
  private final Map<String, StoredFileEntity> rowsById = new ConcurrentHashMap<>();

  /**
   * 组装临时目录存储与内存元数据 Mapper 的门面（每次写入按新行处理）。
   */
  @BeforeEach
  void setUp() {
    StoredFileMapper mapper = Mockito.mock(StoredFileMapper.class);
    Mockito.when(mapper.selectById(ArgumentMatchers.anyString())).thenAnswer(invocation ->
        rowsById.get(invocation.getArgument(0, String.class)));
    // 本单测每个用例使用唯一 objectKey，selectOne 恒空即可走 insert 路径
    Mockito.when(mapper.selectOne(ArgumentMatchers.any())).thenReturn(null);
    Mockito.when(mapper.insert(ArgumentMatchers.any(StoredFileEntity.class))).thenAnswer(invocation -> {
      StoredFileEntity entity = invocation.getArgument(0);
      rowsById.put(entity.getId(), entity);
      return 1;
    });

    files = new FileFacadeImpl(mapper, List.of(new TempDirStorageProvider(tempDir)), new StoredFileViewMapper());
  }

  /**
   * 中文、换行、空串、超长文本与 null→空串 往返一致。
   */
  @Test
  void writeText_readText_roundTrip_preservesUtf8ExactBytes() {
    assertRoundTrip("zh", "你好，世界\n第二行");
    assertRoundTrip("empty", "");
    assertRoundTrip("crlf", "a\r\nb\nc");
    String longText = "测".repeat(20_000) + "\n" + "x".repeat(50_000);
    assertRoundTrip("long", longText);

    StoredFileView nullWritten = files.writeText("works/null.md", "null.md", null);
    assertThat(files.readText(nullWritten.getId())).isEqualTo("");
  }

  /**
   * 写入后再读出，断言字符串与 UTF-8 字节完全一致。
   *
   * @param suffix 对象键后缀
   * @param text   原文
   */
  private void assertRoundTrip(String suffix, String text) {
    String objectKey = "works/roundtrip-" + suffix + ".md";
    StoredFileView written = files.writeText(objectKey, suffix + ".md", text);
    assertThat(written.getId()).isNotBlank();
    String read = files.readText(written.getId());
    assertThat(read).isEqualTo(text);
    assertThat(read.getBytes(StandardCharsets.UTF_8))
        .containsExactly(text.getBytes(StandardCharsets.UTF_8));
  }

  /** 基于临时目录的 LOCAL 存储。 */
  private static final class TempDirStorageProvider implements StorageProvider {
    private final Path root;

    private TempDirStorageProvider(Path root) {
      this.root = root;
    }

    @Override
    public StorageType type() {
      return StorageType.LOCAL;
    }

    @Override
    public String store(String objectKey, byte[] bytes) {
      Path path = root.resolve(normalize(objectKey));
      try {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
        return normalize(objectKey);
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public InputStream open(String objectKey) {
      try {
        return new ByteArrayInputStream(Files.readAllBytes(resolveLocalPath(objectKey)));
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public Path resolveLocalPath(String objectKey) {
      return root.resolve(normalize(objectKey));
    }

    @Override
    public boolean exists(String objectKey) {
      return Files.isRegularFile(resolveLocalPath(objectKey));
    }

    @Override
    public void delete(String objectKey) {
      try {
        Files.deleteIfExists(resolveLocalPath(objectKey));
      } catch (Exception ignored) {
        // best-effort
      }
    }

    private static String normalize(String objectKey) {
      String key = objectKey.replace('\\', '/');
      while (key.startsWith("/")) {
        key = key.substring(1);
      }
      return key;
    }
  }
}
