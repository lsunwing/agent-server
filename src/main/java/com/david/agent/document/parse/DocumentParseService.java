package com.david.agent.document.parse;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按扩展名分发到具体解析器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseService {

    private final TextDocumentExtractor textExtractor;
    private final DocxTextExtractor docxTextExtractor;
    private final XlsxTextExtractor xlsxTextExtractor;
    private final PdfTextExtractor pdfTextExtractor;
    private final PptxTextExtractor pptxTextExtractor;

    private final Map<String, DocumentTextExtractor> byExtension = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        register(textExtractor);
        register(docxTextExtractor);
        register(xlsxTextExtractor);
        register(pdfTextExtractor);
        register(pptxTextExtractor);
        log.info("[rag] document extractors ready: {}", byExtension.keySet());
    }

    private void register(DocumentTextExtractor extractor) {
        byExtension.put(extractor.extension().toLowerCase(Locale.ROOT), extractor);
    }

    public boolean supports(String ext) {
        String key = normalize(ext);
        if (byExtension.containsKey(key)) {
            return true;
        }
        return textExtractor.supports(key);
    }

    public String extract(Path filePath, String extension) {
        String key = normalize(extension);
        DocumentTextExtractor extractor = byExtension.get(key);
        if (extractor == null) {
            if (textExtractor.supports(key)) {
                extractor = textExtractor;
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + key);
            }
        }
        try {
            String text = extractor.extract(filePath);
            return text == null ? "" : text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse document: " + e.getMessage(), e);
        }
    }

    public List<String> supportedExtensions() {
        return List.of("md", "txt", "log", "csv", "json", "yaml", "yml",
                "docx", "xlsx", "pdf", "pptx");
    }

    private String normalize(String ext) {
        if (ext == null) {
            return "";
        }
        return ext.toLowerCase(Locale.ROOT).trim();
    }
}
