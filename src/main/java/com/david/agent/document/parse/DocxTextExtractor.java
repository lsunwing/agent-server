package com.david.agent.document.parse;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Word（docx）解析：保留标题层级与表格。
 */
@Component
public class DocxTextExtractor implements DocumentTextExtractor {

    @Override
    public String extension() {
        return "docx";
    }

    @Override
    public String extract(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath); XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(sb, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(sb, table);
                }
            }
            return sb.toString().strip();
        }
    }

    private void appendParagraph(StringBuilder sb, XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        String style = paragraph.getStyle() == null ? "" : paragraph.getStyle().toLowerCase();
        String trimmed = text.strip();
        if (style.contains("heading") || style.contains("title")) {
            int level = parseHeadingLevel(style);
            sb.append("#".repeat(Math.max(1, level))).append(' ').append(trimmed).append("\n\n");
        } else {
            sb.append(trimmed).append("\n\n");
        }
    }

    private int parseHeadingLevel(String style) {
        for (int i = 1; i <= 6; i++) {
            if (style.contains("heading " + i) || style.contains("heading" + i)) {
                return i;
            }
        }
        return 2;
    }

    private void appendTable(StringBuilder sb, XWPFTable table) {
        sb.append("\n");
        boolean first = true;
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder line = new StringBuilder("| ");
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText() == null ? "" : cell.getText().replace("\n", " ").strip();
                line.append(text).append(" | ");
            }
            sb.append(line).append('\n');
            if (first) {
                sb.append("|");
                for (int i = 0; i < row.getTableCells().size(); i++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
                first = false;
            }
        }
        sb.append('\n');
    }
}
