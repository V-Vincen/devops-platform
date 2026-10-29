package com.vincent.devops.integration.k3s;

/** Helm 部署结果。 */
public record K3sDeploymentResult(String operation, String releaseName, int exitCode, String output) {
}
