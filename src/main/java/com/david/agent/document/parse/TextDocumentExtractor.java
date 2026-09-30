package com.david.agent.document.parse;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 纯文本类：md / txt / log / csv / json 等。
 */
@Component
public class TextDocumentExtractor implements DocumentTextExtractor {

    private final List<String> extensions = List.of("md", "txt", "log", "csv", "json", "yaml", "yml");

    @Override
    public String extension() {
        return "*";
    }

    public boolean supports(String ext) {
        return extensions.contains(ext.toLowerCase(Locale.ROOT));
    }

    @Override
    public String extract(Path filePath) throws Exception {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    public List<String> supportedExtensions() {
        return extensions;
    }
}
