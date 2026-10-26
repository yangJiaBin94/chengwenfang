package com.chengwenfang.app.bootstrap;

import com.chengwenfang.publishing.facade.PublishingFacade;
import com.chengwenfang.publishing.facade.constant.BuiltinLayoutThemeKeys;
import com.chengwenfang.publishing.facade.dto.view.LayoutThemeView;
import com.chengwenfang.skill.facade.builtin.BuiltinConfigInspector;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时检查种子排版主题是否存在；只读、不写库。
 */
@Slf4j
@Component
@Order(81)
@Profile("!bootstrap-seed")
@RequiredArgsConstructor
public class BuiltinLayoutThemeHealthChecker implements ApplicationRunner {
  private final PublishingFacade publishing;

  /**
   * 检查 mdnice 紫韵主题。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    List<LayoutThemeView> themes = publishing.listThemes();
    boolean found = false;
    for (LayoutThemeView theme : themes) {
      if (theme == null) {
        continue;
      }
      if (BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA.equals(theme.getKey())
          || BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA_NAME.equals(theme.getName())) {
        found = true;
        break;
      }
    }
    if (found) {
      log.info("排版主题体检通过 key={}", BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA);
      return;
    }
    log.error(
        "基础配置缺失：缺少排版主题 key={} 或 name={}",
        BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA,
        BuiltinLayoutThemeKeys.MDNICE_ZIYUN_OPTIMA_NAME);
    log.error("基础配置未就绪，请执行：{}", BuiltinConfigInspector.BOOTSTRAP_HINT);
  }
}
