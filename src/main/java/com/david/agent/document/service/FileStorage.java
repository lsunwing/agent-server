package com.david.agent.document.service;

import com.david.agent.document.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class FileStorage {

    private final RagProperties properties;

    public Path getUploadDir() {
        return Path.of(properties.uploadDir());
    }

    public void ensureUploadDir() throws IOException {
        Path dir = getUploadDir();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            log.info("[rag] created upload directory: {}", dir);
        }
    }

    public Mono<Path> store(FilePart file) {
        return Mono.fromCallable(() -> {
                    ensureUploadDir();
                    String originalName = file.filename();
                    String ext = "";
                    if (originalName != null && originalName.contains(".")) {
                        ext = originalName.substring(originalName.lastIndexOf('.'));
                    }
                    String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
                    return getUploadDir().resolve(storedName);
                })
                .flatMap(target -> file.transferTo(target).thenReturn(target))
                .doOnNext(target -> log.info("[rag] stored file: {} -> {}", file.filename(), target));
    }

    public void delete(Path filePath) {
        try {
            if (filePath != null && Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("[rag] deleted file: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("[rag] failed to delete file: {}", filePath, e);
        }
    }

    public String resolveExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }
}
