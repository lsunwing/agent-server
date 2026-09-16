package com.david.agent.document.vo;

import java.util.List;

public record RagDocumentDetailVO(
        RagDocumentVO document,
        List<RagChunkVO> chunks
) {
}
