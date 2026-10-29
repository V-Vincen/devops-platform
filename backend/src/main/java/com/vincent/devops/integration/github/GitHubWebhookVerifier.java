package com.vincent.devops.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 校验 GitHub push Webhook 签名并转换为平台可消费的事件。 */
@Component
public class GitHubWebhookVerifier {
    private static final String EVENT_PUSH = "push";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private final ObjectMapper objectMapper;

    public GitHubWebhookVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GitHubWebhookEvent verifyAndParse(String eventName, String deliveryId, String signature,
                                             String payload, String secret) {
        if (!EVENT_PUSH.equalsIgnoreCase(eventName)) {
            throw new IllegalArgumentException("当前只支持 GitHub push 事件");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("GitHub Webhook 密钥未配置");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new IllegalArgumentException("GitHub Webhook 缺少事件 ID");
        }
        verifySignature(signature, payload, secret);
        try {
            JsonNode root = objectMapper.readTree(payload);
            String repositoryKey = text(root.at("/repository/full_name"));
            String gitRef = text(root.at("/ref"));
            String commitSha = text(root.at("/after"));
            if (repositoryKey == null || gitRef == null || commitSha == null) {
                throw new IllegalArgumentException("GitHub push 事件缺少仓库、分支或提交号");
            }
            Set<String> changedFiles = new LinkedHashSet<>();
            for (JsonNode commit : root.path("commits")) {
                addTextArray(commit.path("added"), changedFiles);
                addTextArray(commit.path("modified"), changedFiles);
                addTextArray(commit.path("removed"), changedFiles);
            }
            return new GitHubWebhookEvent(deliveryId.trim(), repositoryKey, gitRef, commitSha,
                    List.copyOf(changedFiles));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("GitHub Webhook 请求体不是合法 JSON");
        }
    }

    private static void verifySignature(String signature, String payload, String secret) {
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX) || payload == null) {
            throw new IllegalArgumentException("GitHub Webhook 签名缺失或格式不正确");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("GitHub Webhook 签名校验失败");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("GitHub Webhook 签名校验失败");
        }
    }

    private static void addTextArray(JsonNode array, Set<String> target) {
        if (array.isArray()) {
            for (JsonNode item : array) {
                if (item.isTextual() && !item.textValue().isBlank()) {
                    target.add(item.textValue());
                }
            }
        }
    }

    private static String text(JsonNode node) {
        return node.isTextual() && !node.textValue().isBlank() ? node.textValue() : null;
    }
}
