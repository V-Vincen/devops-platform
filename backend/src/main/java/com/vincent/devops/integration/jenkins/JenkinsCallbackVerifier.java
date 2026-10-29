package com.vincent.devops.integration.jenkins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** 校验 Jenkins 回调签名，避免未授权请求修改平台执行状态。 */
@Component
public class JenkinsCallbackVerifier {
    private static final Set<String> STATUSES = Set.of("PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELED");
    private final ObjectMapper objectMapper;

    public JenkinsCallbackVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JenkinsCallbackEvent verifyAndParse(String signature, String payload, String secret) {
        verifySignature(signature, payload, secret);
        try {
            JsonNode root = objectMapper.readTree(payload);
            Long projectId = positiveLong(root.path("projectId"));
            Long serviceId = positiveLong(root.path("serviceId"));
            Long pipelineRunId = positiveLong(root.path("pipelineRunId"));
            String status = text(root.path("status"));
            if (projectId == null || serviceId == null || pipelineRunId == null || status == null
                    || !STATUSES.contains(status)) {
                throw new IllegalArgumentException("Jenkins 回调缺少合法的执行记录标识或状态");
            }
            String imageRepository = text(root.path("imageRepository"));
            String imageTag = text(root.path("imageTag"));
            String imageDigest = text(root.path("imageDigest"));
            boolean containsImageArtifact = imageRepository != null || imageTag != null || imageDigest != null;
            if (containsImageArtifact && (!"SUCCESS".equals(status)
                    || imageRepository == null || imageTag == null || imageDigest == null)) {
                throw new IllegalArgumentException("Jenkins 镜像回调只能在构建成功时提供完整镜像信息");
            }
            if (containsImageArtifact && (!imageDigest.matches("^sha256:[0-9a-fA-F]{64}$")
                    || imageRepository.length() > 500 || imageTag.length() > 255)) {
                throw new IllegalArgumentException("Jenkins 镜像回调中的镜像信息格式不正确");
            }
            return new JenkinsCallbackEvent(projectId, serviceId, pipelineRunId, status,
                    text(root.path("jobName")), positiveLong(root.path("buildNumber")),
                    text(root.path("buildUrl")), text(root.path("failureSummary")),
                    imageRepository, imageTag, imageDigest);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("Jenkins 回调请求体不是合法 JSON");
        }
    }

    private static void verifySignature(String signature, String payload, String secret) {
        if (secret == null || secret.isBlank() || signature == null || payload == null || !signature.startsWith("sha256=")) {
            throw new IllegalArgumentException("Jenkins 回调签名缺失或格式不正确");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature.substring("sha256=".length()));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("Jenkins 回调签名校验失败");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Jenkins 回调签名校验失败");
        }
    }

    private static Long positiveLong(JsonNode node) {
        return node.isIntegralNumber() && node.longValue() > 0 ? node.longValue() : null;
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.textValue().isBlank() ? node.textValue() : null;
    }
}
