package com.vincent.devops.integration.jenkins;

import java.util.Map;

/** Jenkins 参数化构建请求；参数中不得放入 Token 或密码。 */
public record JenkinsBuildRequest(String jobName, Map<String, String> parameters) {
}
