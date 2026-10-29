package com.vincent.devops.integration.k3s;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Helm 部署和回滚适配器；所有参数作为独立进程参数传递，不经过 Shell。 */
public final class HelmK3sClient implements K3sGateway {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private final CommandRunner commandRunner;
    private final Duration timeout;

    public HelmK3sClient(CommandRunner commandRunner, Duration timeout) {
        this.commandRunner = commandRunner;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public K3sDeploymentResult deploy(K3sDeploymentRequest request) {
        validateRequest(request);
        List<String> command = new ArrayList<>(List.of(
                "helm", "upgrade", "--install", request.releaseName(), request.chartPath(),
                "--namespace", request.namespace(), "--create-namespace", "--wait",
                "--set", "image.repository=" + request.imageRepository(),
                "--set", "image.tag=" + request.imageTag()
        ));
        addKubeconfig(command, request.kubeconfigPath());
        CommandResult result = commandRunner.run(List.copyOf(command), timeout);
        ensureSuccess(result, "部署");
        return new K3sDeploymentResult("DEPLOY", request.releaseName(), result.exitCode(), result.stdout());
    }

    @Override
    public K3sDeploymentResult rollback(String releaseName, int revision, String namespace, String kubeconfigPath) {
        validateName(releaseName, "Helm Release");
        validateName(namespace, "Kubernetes Namespace");
        if (revision <= 0) {
            throw new IllegalArgumentException("Helm Revision 必须是正整数");
        }
        List<String> command = new ArrayList<>(List.of(
                "helm", "rollback", releaseName, String.valueOf(revision),
                "--namespace", namespace, "--wait"
        ));
        addKubeconfig(command, kubeconfigPath);
        CommandResult result = commandRunner.run(List.copyOf(command), timeout);
        ensureSuccess(result, "回滚");
        return new K3sDeploymentResult("ROLLBACK", releaseName, result.exitCode(), result.stdout());
    }

    private static void validateRequest(K3sDeploymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("K3s 部署参数不能为空");
        }
        validateName(request.releaseName(), "Helm Release");
        validateName(request.namespace(), "Kubernetes Namespace");
        validateImageTag(request.imageTag());
        if (request.chartPath() == null || request.chartPath().isBlank()
                || request.imageRepository() == null || request.imageRepository().isBlank()) {
            throw new IllegalArgumentException("Helm Chart 和镜像仓库不能为空");
        }
    }

    private static void validateName(String value, String label) {
        if (value == null || !value.matches("[a-z0-9](?:[a-z0-9.-]{0,61}[a-z0-9])?")) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    private static void validateImageTag(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("镜像标签格式不正确");
        }
    }

    private static void addKubeconfig(List<String> command, String kubeconfigPath) {
        if (kubeconfigPath != null && !kubeconfigPath.isBlank()) {
            command.add("--kubeconfig");
            command.add(kubeconfigPath);
        }
    }

    private static void ensureSuccess(CommandResult result, String operation) {
        if (result == null || result.exitCode() != 0) {
            throw new IllegalStateException("K3s " + operation + "失败");
        }
    }
}
