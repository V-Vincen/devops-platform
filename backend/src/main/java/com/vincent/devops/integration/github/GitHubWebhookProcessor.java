package com.vincent.devops.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vincent.devops.releaseconfig.dto.WebhookReleaseTarget;
import com.vincent.devops.releaseconfig.mapper.ReleaseConfigurationMapper;
import com.vincent.devops.releaseexecution.dto.CreatePipelineRunRequest;
import com.vincent.devops.releaseexecution.dto.PipelineRunResponse;
import com.vincent.devops.releaseexecution.service.ReleaseExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将已验签的 GitHub push 事件匹配到平台构建记录。 */
@Service
public class GitHubWebhookProcessor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final ReleaseConfigurationMapper releaseConfigurationMapper;
    private final ReleaseExecutionService releaseExecutionService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher("/");

    public GitHubWebhookProcessor(ReleaseConfigurationMapper releaseConfigurationMapper,
                                 ReleaseExecutionService releaseExecutionService) {
        this.releaseConfigurationMapper = releaseConfigurationMapper;
        this.releaseExecutionService = releaseExecutionService;
    }

    public GitHubWebhookProcessResult process(GitHubWebhookEvent event) {
        List<WebhookReleaseTarget> targets = releaseConfigurationMapper.selectWebhookTargets("GITHUB", event.repositoryKey());
        Map<String, WebhookReleaseTarget> oneTargetPerRepository = new LinkedHashMap<>();
        for (WebhookReleaseTarget target : targets) {
            // V7 将一次构建记录绑定一个环境且按仓库事件去重；同一仓库先创建一条构建记录，部署阶段再选择环境。
            oneTargetPerRepository.putIfAbsent(target.repositoryId(), target);
        }
        List<String> runIds = new ArrayList<>();
        for (WebhookReleaseTarget target : oneTargetPerRepository.values()) {
            if ("MANUAL_ONLY".equals(target.releasePolicy())
                    || !branchMatches(target.defaultBranch(), event.gitRef())
                    || !pathMatches(target, event.changedFiles())) {
                continue;
            }
            PipelineRunResponse run = releaseExecutionService.createPipelineRun(
                    parseId(target.projectId()), parseId(target.serviceId()),
                    new CreatePipelineRunRequest(parseId(target.environmentId()), "WEBHOOK", event.deliveryId(),
                            event.gitRef(), event.commitSha(), "{\"changedFileCount\":" + event.changedFiles().size() + "}"));
            if (!runIds.contains(run.id())) {
                runIds.add(run.id());
            }
        }
        String result = runIds.isEmpty() ? "IGNORED" : "CREATED";
        return new GitHubWebhookProcessResult(event.deliveryId(), List.copyOf(runIds), result);
    }

    private boolean pathMatches(WebhookReleaseTarget target, List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return false;
        }
        List<String> rules = parseRules(target.changePathRules());
        String root = normalizeRoot(target.sourceRoot());
        return changedFiles.stream().map(GitHubWebhookProcessor::normalizePath).anyMatch(file -> {
            String relative = root.equals("/") ? file : file.startsWith(root + "/") ? file.substring(root.length() + 1) : null;
            if (relative == null) {
                return false;
            }
            return rules.stream().anyMatch(rule -> pathMatcher.match(rule, relative));
        });
    }

    private static boolean branchMatches(String defaultBranch, String gitRef) {
        return defaultBranch != null && gitRef != null
                && ("refs/heads/" + defaultBranch).equals(gitRef);
    }

    private List<String> parseRules(String value) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(value);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<String> rules = new ArrayList<>();
            for (JsonNode rule : root) {
                if (rule.isTextual() && !rule.textValue().isBlank()) {
                    rules.add(normalizePath(rule.textValue()));
                }
            }
            return rules;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return List.of();
        }
    }

    private static String normalizeRoot(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "/";
        }
        return normalizePath(value);
    }

    private static String normalizePath(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Webhook 目标 ID 不正确");
        }
    }
}
