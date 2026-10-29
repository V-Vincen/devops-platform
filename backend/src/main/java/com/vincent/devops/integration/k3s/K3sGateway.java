package com.vincent.devops.integration.k3s;

/** K3s/Helm 外部调用端口，平台核心业务只依赖该接口。 */
public interface K3sGateway {
    K3sDeploymentResult deploy(K3sDeploymentRequest request);

    K3sDeploymentResult rollback(String releaseName, int revision, String namespace, String kubeconfigPath);
}
