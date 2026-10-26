package com.workbench.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块依赖守卫（对齐 docs/refactor-v2/03 第一节）。
 *
 * <p>分层自上而下：适配层(app) → 模式层 → 内容核心层 → 平台层。同层仅允许 {@code quality → content}、
 * {@code publishing → content}；模式层互不依赖。Facade 边界：他域默认只碰 {@code *.facade..}。
 *
 * <p>已知暂免（见 06 决策）：D-21/D-24/D-28（app 注入编排子包）、D-30（quality→mode.hotnews.facade）、
 * D-31（mode.hotnews→sourcing 内部）、D-32（mode.hotnews→content 内部）、通知 SPI 实现可依赖他域 facade。
 */
@AnalyzeClasses(packages = "com.workbench", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  // —— 平台层 ↛ 模式层 / 内容核心层（纯平台）——

  @ArchTest
  static final ArchRule commonDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.common..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule infraDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.infra..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule fileDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.file..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule jobDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.job..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule crawlerDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.crawler..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.ai..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule skillDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.skill..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.app..");

  @ArchTest
  static final ArchRule aiDoesNotDependOnBusiness =
      noClasses()
          .that().resideInAPackage("com.workbench.ai..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode..",
              "com.workbench.content..",
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..",
              "com.workbench.skill..",
              "com.workbench.notification..",
              "com.workbench.app..");

  /**
   * 通知模块可依赖他域 facade（NotificationTargetResolver 实现），不得穿透 application/domain/infrastructure。
   */
  @ArchTest
  static final ArchRule notificationDoesNotPenetrateUpperLayers =
      noClasses()
          .that().resideInAPackage("com.workbench.notification..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews.application..",
              "com.workbench.mode.hotnews.domain..",
              "com.workbench.mode.hotnews.infrastructure..",
              "com.workbench.mode.course.application..",
              "com.workbench.mode.course.domain..",
              "com.workbench.mode.course.infrastructure..",
              "com.workbench.content.application..",
              "com.workbench.content.domain..",
              "com.workbench.content.infrastructure..",
              "com.workbench.quality.application..",
              "com.workbench.quality.domain..",
              "com.workbench.quality.infrastructure..",
              "com.workbench.publishing.application..",
              "com.workbench.publishing.domain..",
              "com.workbench.publishing.infrastructure..",
              "com.workbench.sourcing.application..",
              "com.workbench.sourcing.domain..",
              "com.workbench.sourcing.infrastructure..",
              "com.workbench.app..");

  // —— 模式层互不依赖 ——

  @ArchTest
  static final ArchRule modeCourseDoesNotDependOnModeHotnews =
      noClasses()
          .that().resideInAPackage("com.workbench.mode.course..")
          .should().dependOnClassesThat().resideInAPackage("com.workbench.mode.hotnews..");

  @ArchTest
  static final ArchRule modeHotnewsDoesNotDependOnModeCourse =
      noClasses()
          .that().resideInAPackage("com.workbench.mode.hotnews..")
          .should().dependOnClassesThat().resideInAPackage("com.workbench.mode.course..");

  // —— 内容核心层 ↛ 模式层（quality→mode.hotnews.facade 见 D-30）——

  @ArchTest
  static final ArchRule contentDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.workbench.content..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews..",
              "com.workbench.mode.course..");

  @ArchTest
  static final ArchRule publishingDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.workbench.publishing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews..",
              "com.workbench.mode.course..");

  @ArchTest
  static final ArchRule sourcingDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.workbench.sourcing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews..",
              "com.workbench.mode.course..");

  @ArchTest
  static final ArchRule qualityDoesNotDependOnModeCourse =
      noClasses()
          .that().resideInAPackage("com.workbench.quality..")
          .should().dependOnClassesThat().resideInAPackage("com.workbench.mode.course..");

  /** D-30：quality 仅可依赖 mode.hotnews.facade，不得穿透其内部。 */
  @ArchTest
  static final ArchRule qualityDoesNotPenetrateModeHotnews =
      noClasses()
          .that().resideInAPackage("com.workbench.quality..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews.application..",
              "com.workbench.mode.hotnews.domain..",
              "com.workbench.mode.hotnews.infrastructure..");

  // —— 同层仅允许 quality→content、publishing→content ——

  @ArchTest
  static final ArchRule contentDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.workbench.content..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.sourcing..");

  @ArchTest
  static final ArchRule publishingDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.workbench.publishing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.quality..",
              "com.workbench.sourcing..");

  @ArchTest
  static final ArchRule sourcingDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.workbench.sourcing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.quality..",
              "com.workbench.publishing..",
              "com.workbench.content..");

  @ArchTest
  static final ArchRule qualityDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.workbench.quality..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.publishing..",
              "com.workbench.sourcing..");

  // —— Controller 仅在 app 适配层 ——

  @ArchTest
  static final ArchRule controllersOnlyInAppAdapterWeb =
      classes()
          .that().areAnnotatedWith(RestController.class)
          .or().areAnnotatedWith(Controller.class)
          .should().resideInAPackage("com.workbench.app.adapter.web..");

  // —— Facade 边界（app 暂免见 D-21/D-24/D-28；mode.hotnews→content 见 D-32；→sourcing 见 D-31）——

  /**
   * content 内部面：mode.hotnews 因 D-32 暂可访问；其余模块（含 app）不得穿透。
   */
  @ArchTest
  static final ArchRule foreignModulesOnlyAccessContentFacade =
      noClasses()
          .that().resideOutsideOfPackages(
              "com.workbench.content..",
              "com.workbench.mode.hotnews..",
              "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.content.application..",
              "com.workbench.content.domain..",
              "com.workbench.content.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessQualityFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.workbench.quality..", "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.quality.application..",
              "com.workbench.quality.domain..",
              "com.workbench.quality.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessPublishingFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.workbench.publishing..", "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.publishing.application..",
              "com.workbench.publishing.domain..",
              "com.workbench.publishing.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessNotificationFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.workbench.notification..", "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.notification.application..",
              "com.workbench.notification.domain..",
              "com.workbench.notification.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessModeCourseFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.workbench.mode.course..", "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.course.application..",
              "com.workbench.mode.course.domain..",
              "com.workbench.mode.course.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessModeHotnewsFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.workbench.mode.hotnews..", "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.mode.hotnews.application..",
              "com.workbench.mode.hotnews.domain..",
              "com.workbench.mode.hotnews.infrastructure..");

  /**
   * sourcing 内部面：mode.hotnews 因 D-23/D-31 暂可访问；其余模块（含 app）不得穿透。
   */
  @ArchTest
  static final ArchRule foreignModulesOnlyAccessSourcingFacade =
      noClasses()
          .that().resideOutsideOfPackages(
              "com.workbench.sourcing..",
              "com.workbench.mode.hotnews..",
              "com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.sourcing.application..",
              "com.workbench.sourcing.domain..",
              "com.workbench.sourcing.infrastructure..");

  // —— app 不得依赖 FacadeImpl / 根包 application / mapper / entity ——

  @ArchTest
  static final ArchRule appDoesNotDependOnFacadeImpl =
      noClasses()
          .that().resideInAPackage("com.workbench.app..")
          .should().dependOnClassesThat().haveSimpleNameEndingWith("FacadeImpl");

  /**
   * 跨域公开面：app 可依赖各域 {@code facade}；禁止依赖他域 {@code application} 根包（FacadeImpl）。
   * 子包编排暂许注入（D-21/D-24/D-28）。
   */
  @ArchTest
  static final ArchRule appDoesNotDependOnForeignApplication =
      noClasses()
          .that().resideInAPackage("com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.content.application",
              "com.workbench.mode.hotnews.application",
              "com.workbench.mode.course.application",
              "com.workbench.publishing.application",
              "com.workbench.notification.application",
              "com.workbench.quality.application",
              "com.workbench.sourcing.application..",
              "com.workbench.skill.application..",
              "com.workbench.file.application..",
              "com.workbench.ai.application..",
              "com.workbench.job.application..",
              "com.workbench.crawler.application..");

  @ArchTest
  static final ArchRule appDoesNotDependOnDomainMappers =
      noClasses()
          .that().resideInAPackage("com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.content.infrastructure.mapper..",
              "com.workbench.mode.hotnews.infrastructure.mapper..",
              "com.workbench.mode.course.infrastructure.mapper..",
              "com.workbench.publishing.infrastructure.mapper..",
              "com.workbench.notification.infrastructure.mapper..",
              "com.workbench.quality.infrastructure.mapper..",
              "com.workbench.sourcing.infrastructure.mapper..",
              "com.workbench.skill.infrastructure.mapper..",
              "com.workbench.file.infrastructure.mapper..",
              "com.workbench.ai.infrastructure.mapper..");

  @ArchTest
  static final ArchRule appDoesNotDependOnDomainEntities =
      noClasses()
          .that().resideInAPackage("com.workbench.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.content.domain.entity..",
              "com.workbench.mode.hotnews.domain.entity..",
              "com.workbench.mode.course.domain.entity..",
              "com.workbench.publishing.domain.entity..",
              "com.workbench.notification.domain.entity..",
              "com.workbench.quality.domain.entity..",
              "com.workbench.sourcing.domain.entity..",
              "com.workbench.skill.domain.entity..",
              "com.workbench.file.domain.entity..",
              "com.workbench.ai.domain.entity..");

  @ArchTest
  static final ArchRule adapterWebDoesNotDependOnWorkerInternals =
      noClasses()
          .that().resideInAPackage("com.workbench.app.adapter.web..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.workbench.app.adapter.worker..",
              "com.workbench.mode.course.application.course..",
              "com.workbench.app.application.generation..");
}
