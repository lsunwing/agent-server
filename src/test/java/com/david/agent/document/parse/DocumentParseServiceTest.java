package com.david.agent.document.parse;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParseServiceTest {

    private final DocumentParseService service = new DocumentParseService(
            new TextDocumentExtractor(),
            new DocxTextExtractor(),
            new XlsxTextExtractor(),
            new PdfTextExtractor(),
            new PptxTextExtractor()
    );

    {
        // 模拟 @PostConstruct
        try {
            var init = DocumentParseService.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(service);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void extractsDocxHeadingsAndText() throws Exception {
        Path file = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("员工手册");
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("本手册适用于全体正式员工。");
        }
        try (var out = Files.newOutputStream(file);
             XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("员工手册");
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("本手册适用于全体正式员工。");
            doc.write(out);
        }

        String text = service.extract(file, "docx");
        assertTrue(text.contains("员工手册"));
        assertTrue(text.contains("正式员工"));
    }

    @Test
    void extractsXlsxTableText() throws Exception {
        Path file = tempDir.resolve("sample.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("部门预算");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("部门");
            row.createCell(1).setCellValue("预算");
            var row2 = sheet.createRow(1);
            row2.createCell(0).setCellValue("研发");
            row2.createCell(1).setCellValue(100);
            try (var out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        String text = service.extract(file, "xlsx");
        assertTrue(text.contains("部门预算"));
        assertTrue(text.contains("研发"));
        assertTrue(text.contains("100"));
    }

    @Test
    void extractsPlainText() throws Exception {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello rag");
        assertTrue(service.extract(file, "txt").contains("hello rag"));
    }
}
