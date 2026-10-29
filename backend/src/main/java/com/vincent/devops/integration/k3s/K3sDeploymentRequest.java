package com.vincent.devops.integration.k3s;

/** K3s/Helm 部署参数；镜像标签由平台执行记录提供。 */
public record K3sDeploymentRequest(
        String releaseName,
        String chartPath,
        String namespace,
        String imageRepository,
        String imageTag,
        String kubeconfigPath
) {
}
