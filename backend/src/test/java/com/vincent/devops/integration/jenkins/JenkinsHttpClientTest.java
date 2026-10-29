package com.vincent.devops.integration.jenkins;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JenkinsHttpClientTest {
    private HttpServer server;
    private AtomicReference<String> authorization;
    private AtomicReference<String> query;

    @BeforeEach
    void setUp() throws Exception {
        authorization = new AtomicReference<>();
        query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/job/devops-platform/buildWithParameters", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldTriggerParameterizedBuildWithoutLoggingToken() {
        JenkinsHttpClient client = new JenkinsHttpClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "jenkins", "test-token", Duration.ofSeconds(3));

        JenkinsBuildTriggerResult result = client.triggerBuild(new JenkinsBuildRequest("devops-platform", Map.of("GIT_REF", "main")));

        assertEquals(201, result.statusCode());
        assertEquals("http://queue/item/1", result.queueUrl());
        assertEquals("Basic amVua2luczp0ZXN0LXRva2Vu", authorization.get());
        assertTrue(query.get().contains("GIT_REF=main"));
    }

    @Test
    void shouldRejectSensitiveBuildParameter() {
        JenkinsHttpClient client = new JenkinsHttpClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "jenkins", "test-token", Duration.ofSeconds(3));

        assertThrows(IllegalArgumentException.class,
                () -> client.triggerBuild(new JenkinsBuildRequest("devops-platform", Map.of("API_TOKEN", "secret"))));
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        query.set(exchange.getRequestURI().getRawQuery());
        exchange.getResponseHeaders().add("Location", "http://queue/item/1");
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }
}
