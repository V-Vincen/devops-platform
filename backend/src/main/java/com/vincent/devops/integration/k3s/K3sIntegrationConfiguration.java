package com.vincent.devops.integration.k3s;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** K3s Helm 客户端仅在显式启用时创建，默认不执行任何集群命令。 */
@Configuration
public class K3sIntegrationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "devops.integration.k3s.enabled", havingValue = "true")
    K3sGateway k3sGateway(org.springframework.core.env.Environment environment) {
        long timeoutSeconds = environment.getProperty("devops.integration.k3s.timeout-seconds", Long.class, 300L);
        if (timeoutSeconds <= 0 || timeoutSeconds > 1800) {
            throw new IllegalStateException("K3s 命令超时时间必须在1到1800秒之间");
        }
        return new HelmK3sClient(new ProcessCommandRunner(), Duration.ofSeconds(timeoutSeconds));
    }
}
