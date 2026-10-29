package com.vincent.devops.releaseexecution.event;

/**
 * 构建记录提交后的领域事件。
 *
 * <p>Jenkins 调度必须发生在构建记录提交之后，避免 Jenkins 回调到达时数据库中尚无对应记录。</p>
 */
public record PipelineRunCreatedEvent(Long projectId, Long serviceId, Long environmentId, Long pipelineRunId) {
}
