package com.vincent.devops.integration.jenkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JenkinsCallbackVerifierTest {
    @Test
    void shouldVerifyCallbackAndMapBuildFields() throws Exception {
        String payload = """
                {"projectId":1,"serviceId":2,"pipelineRunId":3,"status":"SUCCESS",
                 "jobName":"devops-platform","buildNumber":8,"buildUrl":"http://jenkins/8",
                 "imageRepository":"nexus.local/demo","imageTag":"abc123","imageDigest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """;
        JenkinsCallbackEvent event = new JenkinsCallbackVerifier(new ObjectMapper()).verifyAndParse(
                sign(payload, "callback-secret"), payload, "callback-secret");

        assertEquals(3L, event.pipelineRunId());
        assertEquals("SUCCESS", event.status());
        assertEquals(8L, event.buildNumber());
        assertEquals("abc123", event.imageTag());
    }

    @Test
    void shouldRejectUnknownCallbackStatus() throws Exception {
        String payload = "{\"projectId\":1,\"serviceId\":2,\"pipelineRunId\":3,\"status\":\"DONE\"}";
        assertThrows(IllegalArgumentException.class, () -> new JenkinsCallbackVerifier(new ObjectMapper()).verifyAndParse(
                sign(payload, "callback-secret"), payload, "callback-secret"));
    }

    @Test
    void shouldRejectPartialImageArtifactInCallback() throws Exception {
        String payload = "{\"projectId\":1,\"serviceId\":2,\"pipelineRunId\":3,\"status\":\"SUCCESS\",\"imageTag\":\"abc\"}";
        assertThrows(IllegalArgumentException.class, () -> new JenkinsCallbackVerifier(new ObjectMapper()).verifyAndParse(
                sign(payload, "callback-secret"), payload, "callback-secret"));
    }

    @Test
    void shouldRejectMalformedImageDigestInCallback() throws Exception {
        String payload = "{\"projectId\":1,\"serviceId\":2,\"pipelineRunId\":3,\"status\":\"SUCCESS\","
                + "\"imageRepository\":\"nexus.local/demo\",\"imageTag\":\"abc\",\"imageDigest\":\"sha256:bad\"}";
        assertThrows(IllegalArgumentException.class, () -> new JenkinsCallbackVerifier(new ObjectMapper()).verifyAndParse(
                sign(payload, "callback-secret"), payload, "callback-secret"));
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
