package com.chengwenfang.mode.course.application.course;

import cn.hutool.core.bean.BeanUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborItemView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborsView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 从课程域与 Job 输入构建 {@link CourseGenerationContext}。
 * <p>负责拉壳、课程、设置、系列邻居，并把 Job 覆盖项合并进字数/配图档位。
 */
@Component
public class CourseGenerationContextBuilder {
  /** 内容域门面：课程壳、设置、系列邻居。 */
  private final ContentFacade content;

  /**
   * @param content 内容门面依赖
   */
  public CourseGenerationContextBuilder(ContentFacade content) {
    this.content = content;
  }

  /**
   * 组装正式生成所需的课程上下文。
   *
   * @param courseId 课程 ID
   * @param jobInput Job 输入 Map；可含 {@code wordCountOption}/{@code imageCountOption} 覆盖持久化设置
   * @return 已解析的生成上下文
   */
  public CourseGenerationContext build(String courseId, Map<String, Object> jobInput) {
    // 确保草稿/公众号文章壳存在，后续配图与提交依赖 articleId
    CourseShellView shell = content.ensureCourseShell(courseId);
    CourseView course = content.getCourse(courseId);
    // Job 输入优先覆盖库内生成设置
    Map<String, Object> settings = mergeSettings(content.getGenerationSettings(courseId), jobInput);
    SeriesNeighborsView neighbors = content.findSeriesNeighbors(courseId);
    // 路线列表转为 Map，供 fromMaps / renderRoute 使用
    List<Map<String, Object>> route = neighbors.getRoute() == null
        ? List.of()
        : neighbors.getRoute().stream()
            .map(item -> BeanUtil.beanToMap(item, new LinkedHashMap<>(), false, true))
            .map(entry -> (Map<String, Object>) entry)
            .toList();
    Map<String, Object> previous = neighborMap(neighbors.getPrevious());
    Map<String, Object> following = neighborMap(neighbors.getFollowing());
    Map<String, Object> courseMap = BeanUtil.beanToMap(course, new LinkedHashMap<>(), false, true);
    Map<String, Object> shellMap = BeanUtil.beanToMap(shell, new LinkedHashMap<>(), false, true);
    return CourseGenerationContext.fromMaps(courseMap, settings, shellMap, route, previous, following);
  }

  /**
   * 将系列邻居视图转为 Map；无邻居时返回 null。
   *
   * @param item 上一课或下一课视图
   * @return bean Map 或 null
   */
  private static Map<String, Object> neighborMap(SeriesNeighborItemView item) {
    if (item == null) {
      return null;
    }
    return BeanUtil.beanToMap(item, new LinkedHashMap<>(), false, true);
  }

  /**
   * 合并持久化设置与 Job 输入；缺省档位补默认值。
   *
   * @param stored   库中课程生成设置；可为 null
   * @param jobInput 本次 Job 输入；可为 null
   * @return 合并后的设置 Map（至少含字数与配图档位）
   */
  private static Map<String, Object> mergeSettings(
      CourseGenerationSettingsView stored, Map<String, Object> jobInput) {
    Map<String, Object> out = stored == null
        ? new LinkedHashMap<>()
        : BeanUtil.beanToMap(stored, new LinkedHashMap<>(), false, true);
    // 仅当 Job 显式传入非空档位时覆盖
    if (jobInput != null) {
      if (jobInput.get("wordCountOption") != null && !String.valueOf(jobInput.get("wordCountOption")).isBlank()) {
        out.put("wordCountOption", jobInput.get("wordCountOption"));
      }
      if (jobInput.get("imageCountOption") != null && !String.valueOf(jobInput.get("imageCountOption")).isBlank()) {
        out.put("imageCountOption", jobInput.get("imageCountOption"));
      }
    }
    out.putIfAbsent("wordCountOption", "2000-4000");
    out.putIfAbsent("imageCountOption", "3-5");
    return out;
  }
}
