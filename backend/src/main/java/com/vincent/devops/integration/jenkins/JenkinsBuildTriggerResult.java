package com.vincent.devops.integration.jenkins;

/** Jenkins 接受构建请求后的队列信息。 */
public record JenkinsBuildTriggerResult(int statusCode, String queueUrl) {
}
