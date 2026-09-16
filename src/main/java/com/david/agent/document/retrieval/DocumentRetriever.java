package com.david.agent.document.retrieval;

import com.david.agent.document.model.RagChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DocumentRetriever {

    Mono<List<RagChunk>> retrieve(String userQuery);
}
