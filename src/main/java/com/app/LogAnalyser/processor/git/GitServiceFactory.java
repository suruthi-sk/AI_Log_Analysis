package com.app.LogAnalyser.processor.git;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GitServiceFactory {

    @Value("${git.provider:github}")
    private String provider;

    private final GitHubService gitHubService;
    private final GitLabService gitLabService;

    public GitServiceFactory(GitHubService gitHubService, GitLabService gitLabService) {
        this.gitHubService = gitHubService;
        this.gitLabService = gitLabService;
    }

    public GitProviderService getProvider() {
        switch (provider.toLowerCase().trim()) {
            case "gitlab":
                log.info("Git provider: GitLab");
                return gitLabService;
            case "github":
            default:
                log.info("Git provider: GitHub");
                return gitHubService;
        }
    }
}