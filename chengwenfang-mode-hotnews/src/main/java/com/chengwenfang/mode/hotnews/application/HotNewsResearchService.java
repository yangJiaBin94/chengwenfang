package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.common.PageQuery;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.facade.dto.request.ManualResearchItemRequest;
import com.chengwenfang.mode.hotnews.facade.dto.request.VideoParseRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchCompleteView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchConflictView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点选题调研：启动/完成、资料池；视频解析入口薄委托。
 * <p>薄门面：对外保持原公开方法签名，内部委派运行/采集/写入/查询协作类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsResearchService {

  /** 调研运行。 */
  private final ResearchRunService runService;
  /** 候选采集。 */
  private final ResearchCandidateCollector collector;
  /** 资料池写入。 */
  private final ResearchPoolWriter poolWriter;
  /** 调研查询。 */
  private final ResearchQueryService query;

  /**
   * 启动选题资料补充任务。
   *
   * @param topicId 选题主键
   * @return 任务视图
   */
  public JobView startTopicResearch(String topicId) {
    return runService.startTopicResearch(topicId);
  }

  /**
   * 启动资料池视频解析任务。
   *
   * @param topicId 选题主键
   * @param request 条目与起始步骤
   * @return 任务视图
   */
  @Transactional
  public JobView startVideoParse(String topicId, VideoParseRequest request) {
    return runService.startVideoParse(topicId, request);
  }

  /**
   * 执行视频解析 Job。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入 JSON
   * @param context   进度上下文
   */
  public void executeVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    runService.executeVideoParseJob(jobId, inputJson, context);
  }

  /**
   * 为视频候选写入时长秒数。
   *
   * @param candidates 待入库候选
   */
  public void stampVideoDurations(List<ResearchCandidate> candidates) {
    collector.stampVideoDurations(candidates);
  }

  /**
   * 为待解析雷达视频入队自动解析任务。
   *
   * @param themeId 主题 ID
   * @return 任务；无需解析时 null
   */
  @Transactional
  public JobView enqueuePendingSourceVideoParse(String themeId) {
    return runService.enqueuePendingSourceVideoParse(themeId);
  }

  /**
   * 执行雷达信源视频解析 Job。
   *
   * @param jobId     任务 ID
   * @param inputJson 输入
   * @param context   进度上下文
   */
  public void executeSourceVideoParseJob(String jobId, String inputJson, JobExecutionContext context) {
    runService.executeSourceVideoParseJob(jobId, inputJson, context);
  }

  /**
   * 列出选题调研运行。
   *
   * @param topicId 选题主键
   * @return 运行视图列表
   */
  public List<HotNewsResearchRunView> listResearchRuns(String topicId) {
    return query.listResearchRuns(topicId);
  }

  /**
   * 按主键读取选题调研运行。
   *
   * @param runId 调研运行 ID
   * @return 运行视图
   */
  public HotNewsResearchRunView getResearchRun(String runId) {
    return query.getResearchRun(runId);
  }

  /**
   * 列出选题下的调研材料条目，按 score、创建时间倒序。
   *
   * @param topicId 选题主键
   * @return 调研条目视图列表
   */
  public List<HotNewsResearchItemView> listResearchItems(String topicId) {
    return query.listResearchItems(topicId);
  }

  /**
   * 分页列出调研材料：关键字与形态在 SQL 侧筛选，再 LIMIT/OFFSET。
   *
   * @param topicId 选题主键
   * @param pageQuery 分页与关键字
   * @param media   all / article / video
   * @return 当前页与选题级统计
   */
  public HotNewsResearchItemPageView pageResearchItems(
      String topicId, PageQuery pageQuery, String media) {
    return query.pageResearchItems(topicId, pageQuery, media);
  }

  /**
   * 手动添加资料：抓取页面标题/摘要，写入资料池并标注站点与内容形态。
   *
   * @param topicId 选题主键
   * @param request 含 URL
   * @return 新条目视图
   */
  @Transactional
  public HotNewsResearchItemView addManualResearchItem(
      String topicId, ManualResearchItemRequest request) {
    return poolWriter.addManualResearchItem(topicId, request);
  }

  /**
   * 列出可用于骨架/成文的参考资料。
   *
   * @param topicId 选题主键
   * @return 参考资料视图
   */
  public List<HotNewsResearchItemView> listArticleReferenceItems(String topicId) {
    return query.listArticleReferenceItems(topicId);
  }

  /**
   * 列出选题下的调研冲突提示，按创建时间倒序。
   *
   * @param topicId 选题主键
   * @return 冲突视图列表
   */
  public List<HotNewsResearchConflictView> listResearchConflicts(String topicId) {
    return query.listResearchConflicts(topicId);
  }

  /**
   * 完成选题资料补充：检索、核验、落池并确保简报。
   *
   * @param researchRunId 调研运行 ID
   * @param topicId       选题 ID；可空则用运行记录
   * @return 完成视图
   */
  public HotNewsResearchCompleteView completeTopicResearch(String researchRunId, String topicId) {
    return runService.completeTopicResearch(researchRunId, topicId);
  }
}
