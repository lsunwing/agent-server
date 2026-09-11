package com.david.agent.tool.websearch.model;

import java.util.List;

public record WebSearchResult(
        String query,
        String answer,
        List<SearchItem> results
) {
    public record SearchItem(
            String title,
            String url,
            String content
    ) {
    }
}
