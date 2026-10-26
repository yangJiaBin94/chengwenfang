package com.workbench.app.golden.compare;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.app.golden.normalize.Normalizer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.opentest4j.AssertionFailedError;

/**
 * 黄金基线读写与比对：失败时写出差异到 {@code target/golden-diff/<场景ID>/}。
 */
public final class GoldenAssert {
  private GoldenAssert() {}

  /**
   * 将采集结果写入 baseline 目录（四个文件）。
   *
   * @param baselineDir 场景 baseline 目录
   * @param artifacts   归一化后的产物文本（键：ai / external / events / outputs）
   */
  public static void writeBaseline(Path baselineDir, Map<String, String> artifacts) {
    FileUtil.mkdir(baselineDir.toFile());
    writeFile(baselineDir.resolve("ai.jsonl"), artifacts.getOrDefault("ai", ""));
    writeFile(baselineDir.resolve("external.jsonl"), artifacts.getOrDefault("external", ""));
    writeFile(baselineDir.resolve("events.jsonl"), artifacts.getOrDefault("events", ""));
    writeFile(baselineDir.resolve("outputs.json"), artifacts.getOrDefault("outputs", "{}"));
  }

  /**
   * 与 baseline 比对；不一致时写差异文件并抛断言失败。
   *
   * @param scenarioId  场景 ID
   * @param baselineDir baseline 目录
   * @param artifacts   当前采集（已归一化）
   * @param diffRoot    差异输出根目录（通常 target/golden-diff）
   */
  public static void compareBaseline(
      String scenarioId, Path baselineDir, Map<String, String> artifacts, Path diffRoot) {
    Map<String, String> expectedRaw = readBaseline(baselineDir);
    // 用当前 Normalizer 再归一化 baseline，兼容旧基线中未覆盖的动态片段（如 GitHub updated 日期）
    Map<String, String> expected =
        normalizeAll(
            new Normalizer(),
            expectedRaw.getOrDefault("ai", ""),
            expectedRaw.getOrDefault("external", ""),
            expectedRaw.getOrDefault("events", ""),
            expectedRaw.getOrDefault("outputs", "{}"));
    Map<String, String> mismatches = new LinkedHashMap<>();
    for (String key : expected.keySet()) {
      String want = StrUtil.nullToEmpty(expected.get(key));
      String got = StrUtil.nullToEmpty(artifacts.get(key));
      if (!Objects.equals(want, got)) {
        mismatches.put(key, "length expected=" + want.length() + " actual=" + got.length());
      }
    }
    for (String key : artifacts.keySet()) {
      if (!expected.containsKey(key)) {
        mismatches.put(key, "unexpected artifact key");
      }
    }
    if (mismatches.isEmpty()) {
      return;
    }
    Path diffDir = diffRoot.resolve(scenarioId);
    FileUtil.mkdir(diffDir.toFile());
    for (String key : mismatches.keySet()) {
      writeFile(diffDir.resolve(key + ".expected"), expected.getOrDefault(key, ""));
      writeFile(diffDir.resolve(key + ".actual"), artifacts.getOrDefault(key, ""));
    }
    writeFile(diffDir.resolve("summary.txt"), String.join("\n", mismatches.entrySet().stream()
        .map(entry -> entry.getKey() + ": " + entry.getValue())
        .toList()));
    throw new AssertionFailedError(
        "黄金基线比对失败 scenario=" + scenarioId + " keys=" + mismatches.keySet()
            + " 详见 " + diffDir.toAbsolutePath());
  }

  /**
   * 读取 baseline 四文件；缺文件视为空串。
   *
   * @param baselineDir 目录
   * @return 键 → 文本
   */
  public static Map<String, String> readBaseline(Path baselineDir) {
    Map<String, String> out = new LinkedHashMap<>();
    out.put("ai", readFile(baselineDir.resolve("ai.jsonl")));
    out.put("external", readFile(baselineDir.resolve("external.jsonl")));
    out.put("events", readFile(baselineDir.resolve("events.jsonl")));
    out.put("outputs", readFile(baselineDir.resolve("outputs.json")));
    return out;
  }

  /**
   * 用同一 {@link Normalizer} 归一化四类产物。
   *
   * @param normalizer 归一化器
   * @param ai         AI 请求日志
   * @param external   外部请求日志
   * @param events     任务事件
   * @param outputs    业务产物 JSON
   * @return 归一化后的 map
   */
  public static Map<String, String> normalizeAll(
      Normalizer normalizer, String ai, String external, String events, String outputs) {
    Map<String, String> out = new LinkedHashMap<>();
    // AI 与外部请求均可能并行发出，JSONL 行序不稳定，统一按行排序
    out.put("ai", normalizer.normalizeExternalJsonl(ai));
    out.put("external", normalizer.normalizeExternalJsonl(external));
    out.put("events", normalizer.normalizeText(events));
    out.put("outputs", normalizer.normalizeOutputsJson(outputs));
    return out;
  }

  private static void writeFile(Path path, String content) {
    FileUtil.writeString(StrUtil.nullToEmpty(content), path.toFile(), StandardCharsets.UTF_8);
  }

  private static String readFile(Path path) {
    if (!Files.isRegularFile(path)) {
      return "";
    }
    return FileUtil.readString(path.toFile(), StandardCharsets.UTF_8);
  }
}
