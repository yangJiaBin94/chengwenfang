package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.sourcing.application.VideoTranscriptCliRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** 视频解析工作目录超期清理。 */
class VideoTranscriptWorkDirCleanupTest {

  @TempDir
  Path tempDir;

  @Test
  void cleanupExpiredWorkDirs_删除超过保留天数的条目目录() throws Exception {
    WorkbenchProperties properties = new WorkbenchProperties();
    properties.setRuntimeDir(tempDir.toString());
    VideoTranscriptPipeline pipeline = new VideoTranscriptPipeline(
        properties,
        Mockito.mock(VideoTranscriptCliRunner.class),
        Mockito.mock(VideoSpeechSummarizer.class),
        Mockito.mock(VideoVisionSummarizer.class),
        Mockito.mock(VideoTranscriptStore.class),
        Mockito.mock(VideoDurationCache.class));
    VideoTranscriptService service = new VideoTranscriptService(
        Mockito.mock(TopicResearchItemScoreService.class),
        pipeline,
        Mockito.mock(VideoTranscriptStore.class),
        Mockito.mock(VideoDurationCache.class));

    Path root = tempDir.resolve("video-transcript");
    Files.createDirectories(root);
    Path stale = root.resolve("stale-item");
    Path fresh = root.resolve("fresh-item");
    Files.createDirectories(stale);
    Files.createDirectories(fresh);
    Path staleFile = stale.resolve("audio.mp3");
    Path freshFile = fresh.resolve("audio.mp3");
    Files.writeString(staleFile, "old");
    Files.writeString(freshFile, "new");
    Instant fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS);
    Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
    Files.setLastModifiedTime(staleFile, FileTime.from(fourDaysAgo));
    Files.setLastModifiedTime(stale, FileTime.from(fourDaysAgo));
    Files.setLastModifiedTime(freshFile, FileTime.from(oneHourAgo));
    Files.setLastModifiedTime(fresh, FileTime.from(oneHourAgo));

    int removed = service.cleanupExpiredWorkDirs(root, 3);

    assertThat(removed).isEqualTo(1);
    assertThat(Files.exists(stale)).isFalse();
    assertThat(Files.exists(fresh)).isTrue();
  }
}
