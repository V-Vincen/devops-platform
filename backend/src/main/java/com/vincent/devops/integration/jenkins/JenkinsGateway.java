package com.vincent.devops.integration.jenkins;

/** Jenkins 外部调用端口，核心业务只依赖该接口。 */
public interface JenkinsGateway {
    JenkinsBuildTriggerResult triggerBuild(JenkinsBuildRequest request);
}
