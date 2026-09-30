package com.david.agent.document.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * PDF 解析：按页抽取文本层（扫描件需另做 OCR）。
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public String extract(Path filePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder sb = new StringBuilder();
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    sb.append("## 第 ").append(page).append(" 页\n\n").append(text.strip()).append("\n\n");
                }
            }
            return sb.toString().strip();
        }
    }
}
