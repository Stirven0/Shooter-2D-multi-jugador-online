package com.aa.client.mcp;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.BiFunction;

import reactor.core.publisher.Mono;

public class McpToolRegistry {

    private final Map<String, McpSchema.Tool> toolDefs = new LinkedHashMap<>();
    private final Map<String, BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>>> handlers = new LinkedHashMap<>();

    public void registerTool(String name, String description,
                             BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
        registerTool(name, description, Map.of(), handler);
    }

    public void registerTool(String name, String description,
                             Map<String, Object> properties,
                             BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler) {
        toolDefs.put(name, buildToolDef(name, description, properties));
        handlers.put(name, handler);
    }

    public Map<String, McpSchema.Tool> getToolDefs() {
        return Collections.unmodifiableMap(toolDefs);
    }

    public BiFunction<McpAsyncServerExchange, Map<String, Object>, Mono<McpSchema.CallToolResult>> getHandler(String name) {
        return handlers.get(name);
    }

    public List<McpServerFeatures.AsyncToolSpecification> buildAsyncSpecs() {
        return toolDefs.entrySet().stream()
            .map(e -> new McpServerFeatures.AsyncToolSpecification(e.getValue(), handlers.get(e.getKey())))
            .toList();
    }

    static McpSchema.Tool buildToolDef(String name, String description, Map<String, Object> properties) {
        Map<String, Object> schemaProps = new java.util.HashMap<>();
        java.util.List<String> required = new java.util.ArrayList<>();

        for (var entry : properties.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
            schemaProps.put(entry.getKey(), propDef);
            if (Boolean.TRUE.equals(propDef.get("required"))) {
                required.add(entry.getKey());
            }
        }

        return McpSchema.Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(new McpSchema.JsonSchema("object", schemaProps, required, false, Map.of(), Map.of()))
            .build();
    }

    public static McpSchema.CallToolResult success(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }

    public static McpSchema.CallToolResult error(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("ERROR: " + text)), true);
    }
}
