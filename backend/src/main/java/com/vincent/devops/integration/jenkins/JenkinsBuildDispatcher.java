package com.vincent.devops.integration.jenkins;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

/**
 * 平台到 Jenkins 的受控调度入口。
 *
 * <p>仅在显式启用 Jenkins 集成时发起网络请求；Job 名称和回调地址来自运行配置，
 * 不允许由平台接口调用方直接指定。</p>
 */
@Component
public class JenkinsBuildDispatcher {
    private final ObjectProvider<JenkinsGateway> jenkinsGatewayProvider;
    private final boolean enabled;
    private final String jobName;
    private final String callbackUrl;

    public JenkinsBuildDispatcher(
            ObjectProvider<JenkinsGateway> jenkinsGatewayProvider,
            @Value("${devops.integration.jenkins.enabled:false}") boolean enabled,
            @Value("${devops.integration.jenkins.job-name:}") String jobName,
            @Value("${devops.integration.jenkins.callback-url:}") String callbackUrl
    ) {
        this.jenkinsGatewayProvider = jenkinsGatewayProvider;
        this.enabled = enabled;
        this.jobName = jobName;
        this.callbackUrl = callbackUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public JenkinsBuildTriggerResult dispatch(Map<String, String> parameters) {
        if (!enabled) {
            throw new IllegalStateException("Jenkins 集成未启用");
        }
        JenkinsGateway gateway = jenkinsGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new IllegalStateException("Jenkins 集成已启用但客户端不可用");
        }
        return gateway.triggerBuild(new JenkinsBuildRequest(validatedJobName(), withCallbackUrl(parameters)));
    }

    public String jobName() {
        return validatedJobName();
    }

    private Map<String, String> withCallbackUrl(Map<String, String> parameters) {
        String value = callbackUrl == null ? "" : callbackUrl.trim();
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Jenkins 集成已启用但回调地址不正确");
        }
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>(parameters);
        result.put("CALLBACK_URL", value);
        validateParameters(result);
        return java.util.Map.copyOf(result);
    }

    private static void validateParameters(Map<String, String> parameters) {
        if (parameters.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Jenkins 构建参数不能为空");
        }
    }

    private String validatedJobName() {
        String value = jobName == null ? "" : jobName.trim();
        if (!value.matches("[A-Za-z0-9_. -]+(?:/[A-Za-z0-9_. -]+)*")) {
            throw new IllegalStateException("Jenkins 集成已启用但 Job 名称不正确");
        }
        return value;
    }
}
