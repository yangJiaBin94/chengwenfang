package com.chengwenfang.app.golden;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.chengwenfang.app.golden.compare.GoldenAssert;
import com.chengwenfang.app.golden.driver.ApiV1ScenarioDriver;
import com.chengwenfang.app.golden.driver.NoOpScenarioDriver;
import com.chengwenfang.app.golden.driver.ScenarioDriver;
import com.chengwenfang.app.golden.normalize.Normalizer;
import com.chengwenfang.app.golden.scenario.EmptyGoldenScenario;
import com.chengwenfang.app.golden.scenario.G01CourseThemeWizardScenario;
import com.chengwenfang.app.golden.scenario.G02CourseContentGenerationScenario;
import com.chengwenfang.app.golden.scenario.G03CourseDraftGenerationScenario;
import com.chengwenfang.app.golden.scenario.G04WechatArticleGenerationScenario;
import com.chengwenfang.app.golden.scenario.G05ImageGenerationScenario;
import com.chengwenfang.app.golden.scenario.G06CourseArticleRepairScenario;
import com.chengwenfang.app.golden.scenario.G07CourseWorkspaceScenario;
import com.chengwenfang.app.golden.scenario.G08HotNewsThemeWizardScenario;
import com.chengwenfang.app.golden.scenario.G09HotNewsRadarFetchScenario;
import com.chengwenfang.app.golden.scenario.G10HotNewsTopicEditorialScenario;
import com.chengwenfang.app.golden.scenario.G11HotNewsTopicResearchScenario;
import com.chengwenfang.app.golden.scenario.G12HotNewsVideoParseScenario;
import com.chengwenfang.app.golden.scenario.G13HotNewsSourceVideoParseScenario;
import com.chengwenfang.app.golden.scenario.G14HotNewsSkeletonBriefScenario;
import com.chengwenfang.app.golden.scenario.G15HotNewsArticleGenerationScenario;
import com.chengwenfang.app.golden.scenario.G16HotNewsArticleRewriteScenario;
import com.chengwenfang.app.golden.scenario.G17HotArticleReviewScenario;
import com.chengwenfang.app.golden.scenario.G18WechatMpPushScenario;
import com.chengwenfang.app.golden.scenario.G19TerminalNotificationScenario;
import com.chengwenfang.app.golden.scenario.G20CustomAiWorkflowScenario;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.app.golden.stub.ExternalSiteStubs;
import com.chengwenfang.app.golden.stub.GoldenTestConfiguration;
import com.chengwenfang.app.golden.stub.ScriptedAiResponder;
import com.chengwenfang.app.support.ChengwenfangIT;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * 黄金场景参数化入口：按 {@code golden.mode=record|compare} 录制或比对。
 */
@ActiveProfiles({"test", "golden"})
@Import(GoldenTestConfiguration.class)
@TestPropertySource(
    properties = {
      "chengwenfang.worker-enabled=true",
      "chengwenfang.worker-concurrency=1",
      "chengwenfang.crawler-allow-loopback=true",
      "spring.application.name=all-scenarios-golden-it"
    })
class AllScenariosGoldenIT extends ChengwenfangIT {
  private static final WireMockServer AI_SERVER;
  private static final WireMockServer EXTERNAL_SERVER;

  /** 热点门面：供 G13 种子待解析信源。 */
  @Autowired
  private HotNewsFacade hotNews;

  static {
    ScriptedAiResponder aiResponder = new ScriptedAiResponder();
    AI_SERVER = new WireMockServer(wireMockConfig().dynamicPort().extensions(aiResponder));
    AI_SERVER.start();
    ScriptedAiResponder.installStubs(AI_SERVER);
    EXTERNAL_SERVER = ExternalSiteStubs.startServer();
  }

  @AfterAll
  static void stopStubs() {
    AI_SERVER.stop();
    EXTERNAL_SERVER.stop();
  }

  @DynamicPropertySource
  static void goldenExternalProps(DynamicPropertyRegistry registry) {
    registry.add("chengwenfang.search-base-url", EXTERNAL_SERVER::baseUrl);
    registry.add("chengwenfang.wechat-mp.api-base", EXTERNAL_SERVER::baseUrl);
  }

  /**
   * 已注册场景：G00 + 已实现的业务场景。
   * <p>可用 {@code -Dgolden.only=G15} 或逗号分隔列表只跑指定场景。
   *
   * @return 场景流
   */
  static Stream<GoldenScenario> scenarios() {
    Stream<GoldenScenario> all =
        Stream.of(
            new EmptyGoldenScenario(),
            new G01CourseThemeWizardScenario(),
            new G02CourseContentGenerationScenario(),
            new G03CourseDraftGenerationScenario(),
            new G04WechatArticleGenerationScenario(),
            new G05ImageGenerationScenario(),
            new G06CourseArticleRepairScenario(),
            new G07CourseWorkspaceScenario(),
            new G08HotNewsThemeWizardScenario(),
            new G09HotNewsRadarFetchScenario(),
            new G10HotNewsTopicEditorialScenario(),
            new G11HotNewsTopicResearchScenario(),
            new G12HotNewsVideoParseScenario(),
            new G13HotNewsSourceVideoParseScenario(),
            new G14HotNewsSkeletonBriefScenario(),
            new G15HotNewsArticleGenerationScenario(),
            new G16HotNewsArticleRewriteScenario(),
            new G17HotArticleReviewScenario(),
            new G18WechatMpPushScenario(),
            new G19TerminalNotificationScenario(),
            new G20CustomAiWorkflowScenario());
    String only = StrUtil.trim(StrUtil.nullToEmpty(System.getProperty("golden.only")));
    if (StrUtil.isBlank(only)) {
      return all;
    }
    Set<String> allow =
        Stream.of(only.split(","))
            .map(item -> StrUtil.trim(StrUtil.nullToEmpty(item)).toUpperCase(Locale.ROOT))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toSet());
    return all.filter(
        scenario -> allow.contains(StrUtil.nullToEmpty(scenario.id()).toUpperCase(Locale.ROOT)));
  }

  /**
   * 逐场景执行 prepare → execute → collect，再按模式写基线或比对。
   *
   * @param scenario 场景
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("scenarios")
  @DisplayName("黄金场景")
  void runScenario(GoldenScenario scenario) {
    ScenarioDriver driver = createDriver(scenario);
    GoldenScenarioContext context = new GoldenScenarioContext(scenario.id());
    Path runtimeDir =
        Path.of("target", "golden-runtime", scenario.id()).toAbsolutePath().normalize();
    context.setRuntimeDir(runtimeDir);

    scenario.prepare(driver, context);
    scenario.execute(driver, context);
    Map<String, String> raw = scenario.collect(driver, context);

    Normalizer normalizer = new Normalizer().runtimeDir(runtimeDir.toString());
    Map<String, String> artifacts =
        GoldenAssert.normalizeAll(
            normalizer,
            raw.getOrDefault("ai", ""),
            raw.getOrDefault("external", ""),
            raw.getOrDefault("events", ""),
            raw.getOrDefault("outputs", "{}"));

    Path baselineDir = GoldenPaths.baselineDir(scenario.id());
    GoldenMode mode = GoldenMode.fromSystemProperty();
    if (mode == GoldenMode.RECORD) {
      GoldenAssert.writeBaseline(baselineDir, artifacts);
      return;
    }
    GoldenAssert.compareBaseline(scenario.id(), baselineDir, artifacts, GoldenPaths.diffRoot());
  }

  private ScenarioDriver createDriver(GoldenScenario scenario) {
    if (!scenario.requiresHttp()) {
      return new NoOpScenarioDriver();
    }
    ApiV1ScenarioDriver api = new ApiV1ScenarioDriver(port, objectMapper, jdbc);
    api.bindWireMocks(AI_SERVER, EXTERNAL_SERVER);
    api.bindHotNewsFacade(hotNews);
    return api;
  }
}
