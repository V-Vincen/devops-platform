package com.vincent.devops.integration.jenkins;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Jenkins 客户端仅在显式启用时创建，默认不会对外发起请求。 */
@Configuration
public class JenkinsIntegrationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "devops.integration.jenkins.enabled", havingValue = "true")
    JenkinsGateway jenkinsGateway(
            org.springframework.core.env.Environment environment
    ) {
        String baseUrl = required(environment, "devops.integration.jenkins.base-url");
        String username = required(environment, "devops.integration.jenkins.username");
        String apiToken = required(environment, "devops.integration.jenkins.api-token");
        return new JenkinsHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                URI.create(baseUrl), username, apiToken, Duration.ofSeconds(15));
    }

    private static String required(org.springframework.core.env.Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Jenkins 集成已启用但缺少配置：" + key);
        }
        return value;
    }
}
