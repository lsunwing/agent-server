package com.david.agent.tool.discovery;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(100)
public class SimpleKeywordRetriever implements ToolRetriever {

    @Override
    public Mono<List<ToolDescriptor>> retrieve(String userInput, List<ToolDescriptor> candidates, Map<String, Object> variables) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(List.of());
        }

        String query = userInput == null ? "" : userInput.toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return Mono.just(List.of());
        }

        List<ScoredTool> matched = new ArrayList<>();
        for (ToolDescriptor candidate : candidates) {
            int score = score(query, candidate);
            if (score > 0) {
                matched.add(new ScoredTool(candidate, score));
            }
        }

        matched.sort((left, right) -> {
            int byScore = Integer.compare(right.score(), left.score());
            if (byScore != 0) {
                return byScore;
            }

            int byPriority = Integer.compare(
                    right.descriptor().metadata().priority(),
                    left.descriptor().metadata().priority());
            if (byPriority != 0) {
                return byPriority;
            }

            return left.descriptor().name().compareTo(right.descriptor().name());
        });

        return Mono.just(matched.stream().map(ScoredTool::descriptor).toList());
    }

    private int score(String query, ToolDescriptor descriptor) {
        int score = 0;

        String toolName = descriptor.name().toLowerCase(Locale.ROOT);
        if (query.contains(toolName)) {
            score += 5;
        }

        for (String keyword : descriptor.metadata().keywords()) {
            String normalized = keyword.toLowerCase(Locale.ROOT);
            if (normalized.length() >= 2 && query.contains(normalized)) {
                score += 3;
            }
        }

        String description = descriptor.definition().description();
        if (description != null) {
            String normalizedDescription = description.toLowerCase(Locale.ROOT);
            if (query.chars().anyMatch(ch -> ch > 127) && containsChineseFragment(query, normalizedDescription)) {
                score += 2;
            }
        }

        return score;
    }

    private boolean containsChineseFragment(String query, String description) {
        String[] fragments = query.split("[，。！？、\\s,!.?]+");
        for (String fragment : fragments) {
            String value = fragment.trim();
            if (value.length() >= 2 && description.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private record ScoredTool(ToolDescriptor descriptor, int score) {
    }
}
