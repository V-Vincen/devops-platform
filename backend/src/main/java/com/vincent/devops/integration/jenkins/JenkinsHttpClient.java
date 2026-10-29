package com.vincent.devops.integration.jenkins;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Jenkins HTTP 适配器；不记录 Basic Token，失败只返回可定位的状态信息。 */
public final class JenkinsHttpClient implements JenkinsGateway {
    private static final Set<String> SENSITIVE_PARAMETER_PARTS = Set.of("token", "password", "secret", "credential", "privatekey");
    private final HttpClient httpClient;
    private final URI baseUri;
    private final String username;
    private final String apiToken;
    private final Duration requestTimeout;

    public JenkinsHttpClient(HttpClient httpClient, URI baseUri, String username, String apiToken,
                             Duration requestTimeout) {
        if (baseUri == null || username == null || username.isBlank() || apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("Jenkins 地址、用户名和 API Token 不能为空");
        }
        this.httpClient = httpClient;
        this.baseUri = baseUri.toString().endsWith("/") ? baseUri : URI.create(baseUri + "/");
        this.username = username;
        this.apiToken = apiToken;
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
    }

    @Override
    public JenkinsBuildTriggerResult triggerBuild(JenkinsBuildRequest request) {
        if (request == null || request.jobName() == null || request.jobName().isBlank()) {
            throw new IllegalArgumentException("Jenkins Job 名称不能为空");
        }
        String jobPath = request.jobName().trim().replace("/", "/job/");
        String query = form(request.parameters());
        URI uri = URI.create(baseUri + "job/" + jobPath + "/buildWithParameters" + (query.isEmpty() ? "" : "?" + query));
        String credentials = Base64.getEncoder().encodeToString((username + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200 && response.statusCode() != 201 && response.statusCode() != 202) {
                throw new IllegalStateException("Jenkins 拒绝构建请求，HTTP 状态码=" + response.statusCode());
            }
            return new JenkinsBuildTriggerResult(response.statusCode(), response.headers().firstValue("Location").orElse(null));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jenkins 构建请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Jenkins 构建请求失败", exception);
        }
    }

    private static String form(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        return parameters.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .peek(entry -> validateParameterName(entry.getKey()))
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void validateParameterName(String name) {
        String normalized = name.toLowerCase().replace("_", "");
        if (SENSITIVE_PARAMETER_PARTS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException("Jenkins 构建参数不能包含 Token、密码或凭据字段");
        }
    }
}
