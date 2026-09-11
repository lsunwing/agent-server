package com.david.agent.service;

import com.david.agent.skill.DefaultSkillRegistry;
import com.david.agent.skill.SkillDefinition;
import com.david.agent.skill.SkillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkillDiscoveryServiceTest {

    private DefaultSkillRegistry registry;
    private SkillDiscoveryService service;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        SkillProperties properties = new SkillProperties(true, "./skills", 5, 2);
        service = new SkillDiscoveryService(registry, properties);
    }

    private void registerStockSkill() {
        registry.register(new SkillDefinition(
                "stock-analysis",
                "分析一只股票的历史走势、成交量与相关新闻",
                null,
                null,
                "workflow...",
                List.of("股票", "行情", "走势", "股价", "stock")
        ));
    }

    @Test
    void discoversSkillByKeyword() {
        registerStockSkill();

        StepVerifier.create(service.discover("帮我分析一下这只股票的走势"))
                .assertNext(skill -> assertEquals("stock-analysis", skill.name()))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenNoSkillMatches() {
        registerStockSkill();

        StepVerifier.create(service.discover("现在几点了"))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenScoreBelowThreshold() {
        registerStockSkill();

        StepVerifier.create(service.discover("帮我看看天气"))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenRegistryEmpty() {
        StepVerifier.create(service.discover("分析股票"))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenDisabled() {
        registerStockSkill();
        SkillProperties disabled = new SkillProperties(false, "./skills", 5, 2);
        SkillDiscoveryService disabledService = new SkillDiscoveryService(registry, disabled);

        StepVerifier.create(disabledService.discover("分析股票走势"))
                .verifyComplete();
    }
}
