package com.david.agent.service;

import com.david.agent.skill.SkillDefinition;
import com.david.agent.skill.SkillMatch;
import com.david.agent.skill.SkillProperties;
import com.david.agent.skill.SkillRegistry;
import com.david.agent.skill.SkillTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.skills.enabled", havingValue = "true", matchIfMissing = true)
public class SkillDiscoveryService {

    private final SkillRegistry registry;
    private final SkillProperties properties;

    public Mono<SkillDefinition> discover(String userQuery) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        if (userQuery == null || userQuery.isBlank()) {
            return Mono.empty();
        }

        String query = userQuery.toLowerCase(Locale.ROOT);
        List<SkillDefinition> skills = registry.getAll();
        log.info("[skill] discovery started, query='{}', skills={}", abbreviate(userQuery, 60), skills.size());

        List<SkillMatch> matched = skills.stream()
                .map(skill -> match(query, skill))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(SkillMatch::score).reversed())
                .limit(properties.maxResults())
                .toList();

        if (matched.isEmpty()) {
            log.info("[skill] no candidate skill matched");
            return Mono.empty();
        }

        for (SkillMatch match : matched) {
            log.info("[skill] candidate: name={}, score={}, reason={}",
                    match.skill().name(), match.score(), match.reason());
        }

        SkillMatch best = matched.get(0);
        if (best.score() < properties.minScore()) {
            log.info("[skill] best score {} below threshold {}, no skill selected", best.score(), properties.minScore());
            return Mono.empty();
        }

        log.info("[skill] selected: name={}, score={}", best.skill().name(), best.score());
        return Mono.just(best.skill());
    }

    private SkillMatch match(String query, SkillDefinition skill) {
        int score = 0;
        StringBuilder reason = new StringBuilder();

        for (String keyword : skill.keywords()) {
            if (keyword != null && !keyword.isBlank() && query.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 3;
                reason.append("keyword:").append(keyword).append(' ');
            }
        }

        Set<String> nameTokens = SkillTokenizer.tokenize(skill.name());
        if (nameTokens.stream().anyMatch(query::contains)) {
            score += 2;
            reason.append("name ");
        }

        Set<String> queryTokens = SkillTokenizer.tokenize(query);
        Set<String> descTokens = SkillTokenizer.tokenize(skill.description());
        long overlap = queryTokens.stream().filter(descTokens::contains).count();
        if (overlap > 0) {
            score += (int) overlap;
            reason.append("description:").append(overlap);
        }

        return new SkillMatch(skill, score, reason.toString().trim());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
