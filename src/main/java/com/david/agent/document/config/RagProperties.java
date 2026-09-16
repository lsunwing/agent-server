package com.david.agent.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent.rag")
public record RagProperties(
        Boolean enabled,
        String uploadDir,
        String maxFileSize,
        Integer maxResults,
        Integer chunkSize,
        Integer chunkOverlap,
        List<String> supportedTypes
) {
    public RagProperties {
        enabled = enabled == null || enabled;
        uploadDir = uploadDir == null || uploadDir.isBlank() ? "./uploads/rag" : uploadDir;
        maxFileSize = maxFileSize == null || maxFileSize.isBlank() ? "5MB" : maxFileSize;
        maxResults = maxResults == null || maxResults < 1 ? 5 : maxResults;
        chunkSize = chunkSize == null || chunkSize < 100 ? 500 : chunkSize;
        chunkOverlap = chunkOverlap == null || chunkOverlap < 0 ? 50 : chunkOverlap;
        supportedTypes = supportedTypes == null || supportedTypes.isEmpty()
                ? List.of("md", "txt", "log")
                : List.copyOf(supportedTypes);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
