package com.vincent.devops.integration.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubWebhookVerifierTest {
    private final GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(new ObjectMapper());
    private final String payload = """
            {"ref":"refs/heads/main","after":"abc123","repository":{"full_name":"acme/demo"},
             "commits":[{"added":["README.md"],"modified":["src/App.java","src/App.java"],"removed":["old.txt"]}]}
            """;

    @Test
    void shouldVerifySignatureAndNormalizeChangedFiles() throws Exception {
        String signature = sign(payload, "github-secret");

        GitHubWebhookEvent event = verifier.verifyAndParse("push", "delivery-1", signature, payload, "github-secret");

        assertEquals("acme/demo", event.repositoryKey());
        assertEquals("refs/heads/main", event.gitRef());
        assertEquals(3, event.changedFiles().size());
    }

    @Test
    void shouldRejectInvalidSignatureAndUnsupportedEvent() {
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndParse("push", "delivery-1", "sha256=00", payload, "github-secret"));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyAndParse("issues", "delivery-1", "sha256=00", payload, "github-secret"));
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
