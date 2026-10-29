package com.vincent.devops.integration.k3s;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelmK3sClientTest {

    @Test
    void shouldBuildSafeHelmDeployCommand() {
        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        HelmK3sClient client = new HelmK3sClient((command, timeout) -> {
            commandRef.set(command);
            assertEquals(Duration.ofSeconds(2), timeout);
            return new CommandResult(0, "deployed", "");
        }, Duration.ofSeconds(2));

        K3sDeploymentResult result = client.deploy(new K3sDeploymentRequest(
                "demo-service", "./chart", "test", "nexus.local/demo", "v1", "/tmp/kubeconfig"
        ));

        assertEquals("DEPLOY", result.operation());
        assertEquals(List.of("helm", "upgrade", "--install", "demo-service", "./chart", "--namespace", "test",
                "--create-namespace", "--wait", "--set", "image.repository=nexus.local/demo", "--set", "image.tag=v1",
                "--kubeconfig", "/tmp/kubeconfig"), commandRef.get());
    }

    @Test
    void shouldRejectUnsafeReleaseNameBeforeRunningCommand() {
        HelmK3sClient client = new HelmK3sClient((command, timeout) -> new CommandResult(0, "", ""), Duration.ofSeconds(2));

        assertThrows(IllegalArgumentException.class, () -> client.deploy(new K3sDeploymentRequest(
                "demo;rm", "./chart", "test", "nexus.local/demo", "v1", null
        )));
    }
}
