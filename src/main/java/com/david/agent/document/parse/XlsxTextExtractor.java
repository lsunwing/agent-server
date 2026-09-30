package com.david.agent.document.parse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Excel（xlsx）解析：每个 sheet 输出为表格文本，保留表头语义。
 */
@Component
public class XlsxTextExtractor implements DocumentTextExtractor {

    private static final int MAX_ROWS_PER_SHEET = 2000;
    private static final int MAX_COLS = 40;

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public String extract(Path filePath) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = Files.newInputStream(filePath); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            StringBuilder sb = new StringBuilder();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("## 工作表：").append(sheet.getSheetName()).append("\n\n");
                int rowIndex = 0;
                for (Row row : sheet) {
                    if (rowIndex >= MAX_ROWS_PER_SHEET) {
                        sb.append("（该表超过 ").append(MAX_ROWS_PER_SHEET).append(" 行，已截断）\n");
                        break;
                    }
                    sb.append(renderRow(row, formatter)).append('\n');
                    rowIndex++;
                }
                sb.append('\n');
            }
            return sb.toString().strip();
        }
    }

    private String renderRow(Row row, DataFormatter formatter) {
        StringBuilder line = new StringBuilder("| ");
        int lastCell = Math.min(row.getLastCellNum(), MAX_COLS);
        for (int i = 0; i < lastCell; i++) {
            Cell cell = row.getCell(i);
            String text = cell == null ? "" : formatter.formatCellValue(cell).replace("\n", " ").strip();
            line.append(text).append(" | ");
        }
        return line.toString();
    }
}
