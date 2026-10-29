package com.vincent.devops.integration.k3s;

import java.time.Duration;
import java.util.List;

/** Helm/kubectl 命令执行端口，测试时使用内存替身避免触碰真实集群。 */
@FunctionalInterface
public interface CommandRunner {
    CommandResult run(List<String> command, Duration timeout);
}
