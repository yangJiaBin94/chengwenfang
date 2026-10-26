package com.chengwenfang.ai.application;

import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import com.chengwenfang.ai.domain.entity.ModelCallEntity;
import com.chengwenfang.ai.facade.enums.ModelCallStatus;
import com.chengwenfang.ai.infrastructure.mapper.ModelCallMapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.infra.support.JobExecutionScope;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型调用审计写入：将一次上游调用的摘要落库到 model_calls（不含 prompt / 密钥 / 完整输出正文）。
 */
@Component
@RequiredArgsConstructor
public class ModelCallRecorder {

  /** 模型调用审计表写入。 */
  private final ModelCallMapper modelCallMapper;

  /**
   * 写入一条 model_calls 审计记录（不含 prompt / 密钥 / 完整模型输出正文）。
   *
   * @param cfg         关联的提供商配置（写入 provider_config_id、model、protocol）
   * @param scene       业务场景码
   * @param status      调用结果状态
   * @param startMs     调用开始时刻（epoch millis），用于算 duration_ms
   * @param inputChars  输入字符数（近似 token 成本）
   * @param outputChars 输出字符数
   * @param errorCode   失败时的英文错误码；成功传 null
   */
  public void recordCall(
      AiProviderConfigEntity cfg,
      String scene,
      ModelCallStatus status,
      long startMs,
      int inputChars,
      int outputChars,
      String errorCode) {
    ModelCallEntity call = new ModelCallEntity();
    call.setId(IdsUtil.newId());
    // Worker 线程绑定的任务 ID；同步调用为空
    call.setJobId(JobExecutionScope.currentJobId());
    call.setScene(scene);
    call.setModel(cfg.getModel());
    call.setProtocol(cfg.getProtocol());
    call.setStatus(status.getValue());
    call.setDurationMs(System.currentTimeMillis() - startMs);
    call.setInputChars(inputChars);
    call.setOutputChars(outputChars);
    call.setErrorCode(errorCode);
    call.setProviderConfigId(cfg.getId());
    call.setCreatedAt(LocalDateTime.now());
    modelCallMapper.insert(call);
  }
}
