package com.david.agent.document.retrieval;

import reactor.core.publisher.Mono;

import java.util.List;

public interface QueryAnalyzer {

    Mono<List<String>> extractKeywords(String userQuery);
}
