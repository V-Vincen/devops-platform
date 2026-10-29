#!/bin/sh
set -eu

: "${JENKINS_URL:?必须设置 JENKINS_URL}"
: "${JENKINS_AGENT_NAME:?必须设置 JENKINS_AGENT_NAME}"
: "${JENKINS_AGENT_SECRET:?必须设置 JENKINS_AGENT_SECRET}"

agent_jar=/var/jenkins-agent/agent.jar
controller_url=${JENKINS_URL%/}
attempt=1

while [ "$attempt" -le 30 ]; do
    if curl -fsSL "${controller_url}/jnlpJars/agent.jar" -o "$agent_jar"; then
        break
    fi
    printf '%s\n' "Jenkins 控制器尚未就绪，等待 Agent Jar：第 ${attempt} 次。" >&2
    attempt=$((attempt + 1))
    sleep 2
done

if [ ! -s "$agent_jar" ]; then
    printf '%s\n' '未能下载 Jenkins Agent Jar，停止启动。' >&2
    exit 1
fi

exec java -jar "$agent_jar" \
    -url "$controller_url" \
    -secret "$JENKINS_AGENT_SECRET" \
    -name "$JENKINS_AGENT_NAME" \
    -webSocket \
    -workDir /var/jenkins-agent
