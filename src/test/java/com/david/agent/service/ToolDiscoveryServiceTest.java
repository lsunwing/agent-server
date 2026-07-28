package com.david.agent.service;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.tool.ToolDefinition;
import com.david.agent.tool.discovery.ToolCatalog;
import com.david.agent.tool.discovery.ToolDescriptor;
import com.david.agent.tool.discovery.ToolMetadata;
import com.david.agent.tool.discovery.ToolRetriever;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolDiscoveryServiceTest {

    @Test
    void enrichesContextWithRetrievedTools() {
        ToolCatalog toolCatalog = mock(ToolCatalog.class);
        ToolRetriever retriever = mock(ToolRetriever.class);

        ToolDescriptor weather = new ToolDescriptor(
                new ToolDefinition("weather", "查询天气", Map.of()),
                ToolMetadata.local("weather", "查询天气", List.of("天气"))
        );
        ToolDescriptor time = new ToolDescriptor(
                new ToolDefinition("time", "查询时间", Map.of()),
                ToolMetadata.local("time", "查询时间", List.of("时间"))
        );

        when(toolCatalog.listDescriptors()).thenReturn(List.of(weather, time));
        when(retriever.retrieve("现在天气如何", List.of(weather, time), Map.of()))
                .thenReturn(Mono.just(List.of(weather)));

        ToolDiscoveryService service = new ToolDiscoveryService(toolCatalog, List.of(retriever));

        AgentContext context = AgentContext.builder()
                .conversationId("c1")
                .messages(List.of(Message.user("现在天气如何")))
                .variables(Map.of())
                .build();

        StepVerifier.create(service.discoverForContext(context))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, result.tools().size());
                    org.junit.jupiter.api.Assertions.assertEquals("weather", result.tools().get(0).name());
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToCatalogWhenNoRetrieverMatch() {
        ToolCatalog toolCatalog = mock(ToolCatalog.class);
        ToolRetriever retriever = mock(ToolRetriever.class);

        ToolDescriptor weather = new ToolDescriptor(
                new ToolDefinition("weather", "查询天气", Map.of()),
                ToolMetadata.local("weather", "查询天气", List.of("天气"))
        );
        ToolDescriptor time = new ToolDescriptor(
                new ToolDefinition("time", "查询时间", Map.of()),
                ToolMetadata.local("time", "查询时间", List.of("时间"))
        );

        when(toolCatalog.listDescriptors()).thenReturn(List.of(time, weather));
        when(retriever.retrieve("无关问题", List.of(time, weather), Map.of()))
                .thenReturn(Mono.just(List.of()));

        ToolDiscoveryService service = new ToolDiscoveryService(toolCatalog, List.of(retriever));

        StepVerifier.create(service.discoverToolDefinitions("无关问题", Map.of()))
                .assertNext(result -> org.junit.jupiter.api.Assertions.assertEquals(2, result.size()))
                .verifyComplete();
    }
}
