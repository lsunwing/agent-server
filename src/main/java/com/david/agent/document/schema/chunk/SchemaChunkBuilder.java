package com.david.agent.document.schema.chunk;

import com.david.agent.document.schema.model.SchemaDocument;

import java.util.List;

public interface SchemaChunkBuilder {

    List<ChunkEnvelope> build(SchemaDocument document);
}
