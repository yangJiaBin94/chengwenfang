package com.workbench.app.golden.stub;

import static org.assertj.core.api.Assertions.assertThat;

import com.workbench.crawler.application.article.TrafilaturaCliResult;
import com.workbench.sourcing.application.VideoTranscriptCliRunner;
import com.workbench.sourcing.application.model.VideoTranscriptCliResult;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import com.workbench.infra.config.WorkbenchProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 冒烟：CLI / 抽取替身能读到全部固定输出。
 */
class GoldenTestBeansTest {
  @TempDir Path tempDir;

  @Test
  void extractors_return_fixed_bodies() {
    TrafilaturaCliResult trafilatura =
        GoldenTestBeans.fixedTrafilatura().extract("https://example.com/a");
    assertThat(trafilatura.isOk()).isTrue();
    assertThat(trafilatura.getText().length()).isGreaterThan(80);

    TrafilaturaCliResult lightpanda =
        GoldenTestBeans.fixedLightpanda().extract("https://example.com/b");
    assertThat(lightpanda.isOk()).isTrue();
    assertThat(lightpanda.getText()).contains("Lightpanda");
  }

  @Test
  void video_cli_fixture_covers_all_cli_steps() throws Exception {
    VideoTranscriptCliRunner runner =
        GoldenTestBeans.fixtureVideoTranscriptCliRunner(new WorkbenchProperties());
    List<VideoParseStep> cliSteps =
        List.of(
            VideoParseStep.PROBE_DURATION,
            VideoParseStep.FETCH_SUBTITLE,
            VideoParseStep.DOWNLOAD_AUDIO,
            VideoParseStep.CONVERT_FORMAT,
            VideoParseStep.ASR,
            VideoParseStep.DOWNLOAD_VIDEO,
            VideoParseStep.EXTRACT_FRAMES);
    for (VideoParseStep step : cliSteps) {
      Path workDir = tempDir.resolve(step.getValue());
      Files.createDirectories(workDir);
      VideoTranscriptCliResult result = runner.run("https://example.com/v.mp4", workDir, step);
      assertThat(result.isOk()).as(step.getValue()).isTrue();
      assertThat(result.getStep()).isEqualTo(step.getValue());
      if (step == VideoParseStep.EXTRACT_FRAMES) {
        assertThat(result.getArtifacts()).containsKey("frame_paths");
        @SuppressWarnings("unchecked")
        List<String> frames = (List<String>) result.getArtifacts().get("frame_paths");
        assertThat(frames).isNotEmpty();
        assertThat(Path.of(frames.get(0))).exists();
      }
      if (step == VideoParseStep.FETCH_SUBTITLE) {
        assertThat(result.getText()).contains("字幕");
      }
    }
  }
}
