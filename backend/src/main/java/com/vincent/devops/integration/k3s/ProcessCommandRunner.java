package com.vincent.devops.integration.k3s;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 以参数数组启动 Helm，禁止通过 Shell 拼接命令，降低命令注入风险。 */
public final class ProcessCommandRunner implements CommandRunner {
    @Override
    public CommandResult run(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("外部命令不能为空");
        }
        try {
            // 合并标准错误输出，避免先读取 stdout、再读取 stderr 时因任一管道写满而死锁。
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IllegalStateException("Helm 命令执行超时");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output, "");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Helm 命令执行被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Helm 命令", exception);
        }
    }
}
