package com.david.agent.document.schema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent.rag.schema")
public record SchemaRagProperties(
        Boolean enabled,
        String route,
        String dialect,
        Boolean emitOverview,
        Boolean emitColumns,
        Boolean emitIndexes,
        Boolean emitRelations,
        Integer maxChunkSize,
        Integer fieldsPerChunk,
        Integer maxColumnsListed
) {
    public SchemaRagProperties {
        enabled = enabled == null || enabled;
        route = route == null || route.isBlank() ? "auto" : route.trim().toLowerCase();
        dialect = dialect == null || dialect.isBlank() ? "generic" : dialect;
        emitOverview = emitOverview == null || emitOverview;
        emitColumns = emitColumns == null || emitColumns;
        emitIndexes = emitIndexes == null || emitIndexes;
        emitRelations = emitRelations == null || emitRelations;
        maxChunkSize = maxChunkSize == null || maxChunkSize < 200 ? 2500 : maxChunkSize;
        fieldsPerChunk = fieldsPerChunk == null || fieldsPerChunk < 1 ? 8 : fieldsPerChunk;
        maxColumnsListed = maxColumnsListed == null || maxColumnsListed < 1 ? 30 : maxColumnsListed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoRoute() {
        return "auto".equals(route);
    }

    public boolean isForceSchema() {
        return "force-schema".equals(route) || "force".equals(route);
    }

    public boolean isOff() {
        return "off".equals(route) || Boolean.FALSE.equals(enabled);
    }

    public List<String> enabledViews() {
        java.util.ArrayList<String> views = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(emitOverview)) {
            views.add(ChunkSections.OVERVIEW);
        }
        if (Boolean.TRUE.equals(emitColumns)) {
            views.add(ChunkSections.COLUMNS);
        }
        if (Boolean.TRUE.equals(emitIndexes)) {
            views.add(ChunkSections.INDEXES);
        }
        if (Boolean.TRUE.equals(emitRelations)) {
            views.add(ChunkSections.RELATIONS);
        }
        return views;
    }

    public static final class ChunkSections {
        public static final String OVERVIEW = "overview";
        public static final String COLUMNS = "columns";
        public static final String INDEXES = "indexes";
        public static final String RELATIONS = "relations";

        private ChunkSections() {
        }
    }
}
