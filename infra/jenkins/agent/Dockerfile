# 构建工具固定版本，避免 Agent 每次构建时下载浮动版本。
FROM docker:29.2.1-cli AS docker-cli

FROM maven:3.9.12-eclipse-temurin-25 AS tool-fetch

ARG KUBECTL_VERSION=v1.36.1
ARG HELM_VERSION=v4.3.0
ARG TARGETARCH

RUN case "${TARGETARCH}" in \
        amd64|arm64) tool_arch="${TARGETARCH}" ;; \
        *) echo "不支持的 Agent 构建架构：${TARGETARCH}" >&2; exit 1 ;; \
    esac \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl tar \
    && apt-get clean \
    && curl -fsSL -o /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${tool_arch}/kubectl" \
    && kubectl_sha256="$(curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${tool_arch}/kubectl.sha256")" \
    && echo "${kubectl_sha256}  /usr/local/bin/kubectl" | sha256sum -c - \
    && chmod 0755 /usr/local/bin/kubectl \
    && curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-${tool_arch}.tar.gz" \
    && curl -fsSLO "https://get.helm.sh/helm-${HELM_VERSION}-linux-${tool_arch}.tar.gz.sha256sum" \
    && grep "helm-${HELM_VERSION}-linux-${tool_arch}.tar.gz$" "helm-${HELM_VERSION}-linux-${tool_arch}.tar.gz.sha256sum" | sha256sum -c - \
    && tar -xzf "helm-${HELM_VERSION}-linux-${tool_arch}.tar.gz" -C /usr/local/bin --strip-components=1 "linux-${tool_arch}/helm"

FROM maven:3.9.12-eclipse-temurin-25

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && apt-get clean

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=tool-fetch /usr/local/bin/kubectl /usr/local/bin/kubectl
COPY --from=tool-fetch /usr/local/bin/helm /usr/local/bin/helm
COPY agent-entrypoint.sh /usr/local/bin/agent-entrypoint

RUN chmod 0755 /usr/local/bin/docker /usr/local/bin/kubectl /usr/local/bin/helm /usr/local/bin/agent-entrypoint \
    && mkdir -p /var/jenkins-agent

ENTRYPOINT ["/usr/local/bin/agent-entrypoint"]
