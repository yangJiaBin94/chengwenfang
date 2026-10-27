package com.chengwenfang.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模块依赖守卫（对齐 docs/代码结构与注释规范.md）。
 *
 * <p>分层自上而下：适配层(app) → 模式层 → 内容核心层 → 平台层。同层仅允许 {@code quality → content}、
 * {@code publishing → content}；模式层互不依赖。Facade 边界：他域默认只碰 {@code *.facade..}。
 *
 * <p>已知暂免（见 06 决策）：D-21/D-24/D-28（app 注入编排子包）、D-30（quality→mode.hotnews.facade）、
 * D-31（mode.hotnews→sourcing 内部）、D-32（mode.hotnews→content 内部）、通知 SPI 实现可依赖他域 facade。
 */
@AnalyzeClasses(packages = "com.chengwenfang", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

  // —— 平台层 ↛ 模式层 / 内容核心层（纯平台）——

  @ArchTest
  static final ArchRule commonDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.common..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule infraDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.infra..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule fileDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.file..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule jobDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.job..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule crawlerDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.crawler..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.ai..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule skillDoesNotDependOnUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.skill..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.app..");

  @ArchTest
  static final ArchRule aiDoesNotDependOnBusiness =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.ai..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode..",
              "com.chengwenfang.content..",
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.skill..",
              "com.chengwenfang.notification..",
              "com.chengwenfang.app..");

  /**
   * 通知模块可依赖他域 facade（NotificationTargetResolver 实现），不得穿透 application/domain/infrastructure。
   */
  @ArchTest
  static final ArchRule notificationDoesNotPenetrateUpperLayers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.notification..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews.application..",
              "com.chengwenfang.mode.hotnews.domain..",
              "com.chengwenfang.mode.hotnews.infrastructure..",
              "com.chengwenfang.mode.course.application..",
              "com.chengwenfang.mode.course.domain..",
              "com.chengwenfang.mode.course.infrastructure..",
              "com.chengwenfang.content.application..",
              "com.chengwenfang.content.domain..",
              "com.chengwenfang.content.infrastructure..",
              "com.chengwenfang.quality.application..",
              "com.chengwenfang.quality.domain..",
              "com.chengwenfang.quality.infrastructure..",
              "com.chengwenfang.publishing.application..",
              "com.chengwenfang.publishing.domain..",
              "com.chengwenfang.publishing.infrastructure..",
              "com.chengwenfang.sourcing.application..",
              "com.chengwenfang.sourcing.domain..",
              "com.chengwenfang.sourcing.infrastructure..",
              "com.chengwenfang.app..");

  // —— 模式层互不依赖 ——

  @ArchTest
  static final ArchRule modeCourseDoesNotDependOnModeHotnews =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.mode.course..")
          .should().dependOnClassesThat().resideInAPackage("com.chengwenfang.mode.hotnews..");

  @ArchTest
  static final ArchRule modeHotnewsDoesNotDependOnModeCourse =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.mode.hotnews..")
          .should().dependOnClassesThat().resideInAPackage("com.chengwenfang.mode.course..");

  // —— 内容核心层 ↛ 模式层（quality→mode.hotnews.facade 见 D-30）——

  @ArchTest
  static final ArchRule contentDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.content..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews..",
              "com.chengwenfang.mode.course..");

  @ArchTest
  static final ArchRule publishingDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.publishing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews..",
              "com.chengwenfang.mode.course..");

  @ArchTest
  static final ArchRule sourcingDoesNotDependOnModes =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.sourcing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews..",
              "com.chengwenfang.mode.course..");

  @ArchTest
  static final ArchRule qualityDoesNotDependOnModeCourse =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.quality..")
          .should().dependOnClassesThat().resideInAPackage("com.chengwenfang.mode.course..");

  /** D-30：quality 仅可依赖 mode.hotnews.facade，不得穿透其内部。 */
  @ArchTest
  static final ArchRule qualityDoesNotPenetrateModeHotnews =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.quality..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews.application..",
              "com.chengwenfang.mode.hotnews.domain..",
              "com.chengwenfang.mode.hotnews.infrastructure..");

  // —— 同层仅允许 quality→content、publishing→content ——

  @ArchTest
  static final ArchRule contentDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.content..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..");

  @ArchTest
  static final ArchRule publishingDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.publishing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.quality..",
              "com.chengwenfang.sourcing..");

  @ArchTest
  static final ArchRule sourcingDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.sourcing..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.quality..",
              "com.chengwenfang.publishing..",
              "com.chengwenfang.content..");

  @ArchTest
  static final ArchRule qualityDoesNotDependOnPeerCore =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.quality..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.publishing..",
              "com.chengwenfang.sourcing..");

  // —— Controller 仅在 app 适配层 ——

  @ArchTest
  static final ArchRule controllersOnlyInAppAdapterWeb =
      classes()
          .that().areAnnotatedWith(RestController.class)
          .or().areAnnotatedWith(Controller.class)
          .should().resideInAPackage("com.chengwenfang.app.adapter.web..");

  // —— Facade 边界（app 暂免见 D-21/D-24/D-28；mode.hotnews→content 见 D-32；→sourcing 见 D-31）——

  /**
   * content 内部面：mode.hotnews 因 D-32 暂可访问；其余模块（含 app）不得穿透。
   */
  @ArchTest
  static final ArchRule foreignModulesOnlyAccessContentFacade =
      noClasses()
          .that().resideOutsideOfPackages(
              "com.chengwenfang.content..",
              "com.chengwenfang.mode.hotnews..",
              "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.content.application..",
              "com.chengwenfang.content.domain..",
              "com.chengwenfang.content.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessQualityFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.chengwenfang.quality..", "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.quality.application..",
              "com.chengwenfang.quality.domain..",
              "com.chengwenfang.quality.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessPublishingFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.chengwenfang.publishing..", "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.publishing.application..",
              "com.chengwenfang.publishing.domain..",
              "com.chengwenfang.publishing.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessNotificationFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.chengwenfang.notification..", "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.notification.application..",
              "com.chengwenfang.notification.domain..",
              "com.chengwenfang.notification.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessModeCourseFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.chengwenfang.mode.course..", "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.course.application..",
              "com.chengwenfang.mode.course.domain..",
              "com.chengwenfang.mode.course.infrastructure..");

  @ArchTest
  static final ArchRule foreignModulesOnlyAccessModeHotnewsFacade =
      noClasses()
          .that().resideOutsideOfPackages("com.chengwenfang.mode.hotnews..", "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.mode.hotnews.application..",
              "com.chengwenfang.mode.hotnews.domain..",
              "com.chengwenfang.mode.hotnews.infrastructure..");

  /**
   * sourcing 内部面：mode.hotnews 因 D-23/D-31 暂可访问；其余模块（含 app）不得穿透。
   */
  @ArchTest
  static final ArchRule foreignModulesOnlyAccessSourcingFacade =
      noClasses()
          .that().resideOutsideOfPackages(
              "com.chengwenfang.sourcing..",
              "com.chengwenfang.mode.hotnews..",
              "com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.sourcing.application..",
              "com.chengwenfang.sourcing.domain..",
              "com.chengwenfang.sourcing.infrastructure..");

  // —— app 不得依赖 FacadeImpl / 根包 application / mapper / entity ——

  @ArchTest
  static final ArchRule appDoesNotDependOnFacadeImpl =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.app..")
          .should().dependOnClassesThat().haveSimpleNameEndingWith("FacadeImpl");

  /**
   * 跨域公开面：app 可依赖各域 {@code facade}；禁止依赖他域 {@code application} 根包（FacadeImpl）。
   * 子包编排暂许注入（D-21/D-24/D-28）。
   */
  @ArchTest
  static final ArchRule appDoesNotDependOnForeignApplication =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.content.application",
              "com.chengwenfang.mode.hotnews.application",
              "com.chengwenfang.mode.course.application",
              "com.chengwenfang.publishing.application",
              "com.chengwenfang.notification.application",
              "com.chengwenfang.quality.application",
              "com.chengwenfang.sourcing.application..",
              "com.chengwenfang.skill.application..",
              "com.chengwenfang.file.application..",
              "com.chengwenfang.ai.application..",
              "com.chengwenfang.job.application..",
              "com.chengwenfang.crawler.application..");

  @ArchTest
  static final ArchRule appDoesNotDependOnDomainMappers =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.content.infrastructure.mapper..",
              "com.chengwenfang.mode.hotnews.infrastructure.mapper..",
              "com.chengwenfang.mode.course.infrastructure.mapper..",
              "com.chengwenfang.publishing.infrastructure.mapper..",
              "com.chengwenfang.notification.infrastructure.mapper..",
              "com.chengwenfang.quality.infrastructure.mapper..",
              "com.chengwenfang.sourcing.infrastructure.mapper..",
              "com.chengwenfang.skill.infrastructure.mapper..",
              "com.chengwenfang.file.infrastructure.mapper..",
              "com.chengwenfang.ai.infrastructure.mapper..");

  @ArchTest
  static final ArchRule appDoesNotDependOnDomainEntities =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.app..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.content.domain.entity..",
              "com.chengwenfang.mode.hotnews.domain.entity..",
              "com.chengwenfang.mode.course.domain.entity..",
              "com.chengwenfang.publishing.domain.entity..",
              "com.chengwenfang.notification.domain.entity..",
              "com.chengwenfang.quality.domain.entity..",
              "com.chengwenfang.sourcing.domain.entity..",
              "com.chengwenfang.skill.domain.entity..",
              "com.chengwenfang.file.domain.entity..",
              "com.chengwenfang.ai.domain.entity..");

  @ArchTest
  static final ArchRule adapterWebDoesNotDependOnWorkerInternals =
      noClasses()
          .that().resideInAPackage("com.chengwenfang.app.adapter.web..")
          .should().dependOnClassesThat().resideInAnyPackage(
              "com.chengwenfang.app.adapter.worker..",
              "com.chengwenfang.mode.course.application.course..",
              "com.chengwenfang.app.application.generation..");
}
