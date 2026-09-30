package com.david.agent.document.parse;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PowerPoint（pptx）解析：每页标题 + 文本框内容。
 */
@Component
public class PptxTextExtractor implements DocumentTextExtractor {

    @Override
    public String extension() {
        return "pptx";
    }

    @Override
    public String extract(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath); XMLSlideShow ppt = new XMLSlideShow(in)) {
            StringBuilder sb = new StringBuilder();
            int page = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("## 第 ").append(page).append(" 页\n\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.strip()).append("\n\n");
                        }
                    }
                }
                page++;
            }
            return sb.toString().strip();
        }
    }
}
