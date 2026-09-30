package com.david.agent.document.parse;

import java.nio.file.Path;

/**
 * 把各类办公文档抽成可检索文本（保留标题/表格等结构语义）。
 */
public interface DocumentTextExtractor {

    /**
     * @return 支持的小写扩展名，不带点，如 docx
     */
    String extension();

    /**
     * @param filePath 已落盘的源文件
     * @return 适合切块入库的文本
     */
    String extract(Path filePath) throws Exception;
}
