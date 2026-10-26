package com.chengwenfang.content.application;

import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.CourseContentView;
import com.chengwenfang.content.facade.dto.view.CourseLifecycleView;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborsView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangDataView;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程壳与成文坊聚合：列表、正文、媒资、壳创建、生命周期与系列邻居（门面，委派到课次/正文协作类）。
 */
@Service
@RequiredArgsConstructor
public class CourseShellService {
  /** 课次与壳。 */
  private final CourseLessonService lessons;
  /** 正文与媒资视图。 */
  private final CourseContentViewService contentViews;

  /**
   * 列出指定主题下的课程，按系列 sortOrder 再按 lessonNo 排序。
   *
   * @param themeId 主题 id；空则列全部
   * @return 课程视图列表
   */
  public List<CourseView> listCourses(String themeId) {
    return lessons.listCourses(themeId);
  }

  /**
   * 列出全部课程，按系列 sortOrder 再按 lessonNo 排序。
   *
   * @return 课程视图列表
   */
  public List<CourseView> listCourses() {
    return lessons.listCourses();
  }

  /**
   * 组装成文坊首页数据：主题、课程列表与进度指标。
   *
   * @param themeId 主题 id/code；空则用默认课程体系主题
   * @return 含 workspace、courses、metrics 的聚合视图
   */
  public ChengwenfangDataView chengwenfangData(String themeId) {
    return contentViews.chengwenfangData(themeId);
  }

  /**
   * 列出可用教学模式（课程体系 / 热点资讯）。
   *
   * @return 模式视图列表（目前固定两项，含各自主题）
   */
  public List<ContentModeView> listModes() {
    return contentViews.listModes();
  }

  /**
   * 获取单课概要视图。
   *
   * @param courseId 课程 id
   * @return 课程视图
   */
  public CourseView getCourse(String courseId) {
    return lessons.getCourse(courseId);
  }

  /**
   * 获取课程正文与媒资：原稿、公众号稿，并将历史 public 路径重写为可访问 URL。
   *
   * @param courseId 课程 id
   * @return 正文与媒资视图；课程缺失时正文为空串
   */
  public CourseContentView getCourseContent(String courseId) {
    return contentViews.getCourseContent(courseId);
  }

  /**
   * 列出课程媒资（配图等），按序号与别名排序。
   *
   * @param courseId 课程 id；空则返回空列表
   * @return 媒资视图列表
   */
  public List<CourseMediaView> listCourseMedia(String courseId) {
    return contentViews.listCourseMedia(courseId);
  }

  /**
   * 获取单个媒资元数据。
   *
   * @param mediaId 媒资 id
   * @return 媒资视图
   */
  public CourseMediaView getMediaAsset(String mediaId) {
    return contentViews.getMediaAsset(mediaId);
  }

  /**
   * 解析媒资本地文件路径。
   *
   * @param mediaId 媒资 id
   * @return 本地磁盘路径
   */
  public Path getMediaFile(String mediaId) {
    return contentViews.getMediaFile(mediaId);
  }

  /**
   * 读取媒资二进制内容。
   *
   * @param mediaId 媒资 id
   * @return 文件字节
   */
  public byte[] getMediaBytes(String mediaId) {
    return contentViews.getMediaBytes(mediaId);
  }

  /**
   * 更新课程发布生命周期（published / draft），必要时先建课程壳。
   *
   * @param courseId  课程 id
   * @param published true 记为 published，否则 draft
   * @return 生命周期视图
   */
  @Transactional
  public CourseLifecycleView patchLifecycle(String courseId, boolean published) {
    return lessons.patchLifecycle(courseId, published);
  }

  /**
   * 确保课程壳存在：缺失时创建原稿与公众号文章占位并插入课程行。
   *
   * @param courseId 课程 id
   * @return 壳视图（courseId、草稿/公众号文章 id、状态）
   */
  @Transactional
  public CourseShellView ensureCourseShell(String courseId) {
    return lessons.ensureCourseShell(courseId);
  }

  /**
   * 查询同系列前后课与完整课程序列，供正式生成上下文使用。
   *
   * @param courseId 当前课程 id
   * @return 含 previous/following/route；无系列时三者为空
   */
  public SeriesNeighborsView findSeriesNeighbors(String courseId) {
    return lessons.findSeriesNeighbors(courseId);
  }

  /**
   * 将上一课正文中的「下节课预告」段落同步为当前课标题。
   *
   * @param courseId 当前课程 id
   */
  @Transactional
  public void syncPreviousLessonPreview(String courseId) {
    lessons.syncPreviousLessonPreview(courseId);
  }
}
