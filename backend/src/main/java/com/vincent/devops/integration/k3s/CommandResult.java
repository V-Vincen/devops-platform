package com.vincent.devops.integration.k3s;

/** 外部命令执行结果；输出仅用于诊断，不直接写入接口响应。 */
public record CommandResult(int exitCode, String stdout, String stderr) {
}
