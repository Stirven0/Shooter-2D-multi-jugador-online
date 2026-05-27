package com.aa.client.mcp;

import com.google.gson.*;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import reactor.core.publisher.Mono;

public class McpJsonRpcHandler {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final McpToolRegistry toolRegistry;

    public McpJsonRpcHandler(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public String handle(String message) {
        try {
            JsonObject req = gson.fromJson(message, JsonObject.class);
            if (req == null || !req.has("method")) {
                return error(null, -32600, "Invalid Request");
            }

            String method = req.get("method").getAsString();
            JsonElement id = req.has("id") ? req.get("id") : null;

            return switch (method) {
                case "initialize" -> handleInitialize(id);
                case "notifications/initialized" -> "";
                case "ping" -> result(id, new JsonObject());
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, req);
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            e.printStackTrace();
            return error(null, -32700, "Parse error: " + e.getMessage());
        }
    }

    private String handleInitialize(JsonElement id) {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", "2024-11-05");
        JsonObject caps = new JsonObject();
        JsonObject tools = new JsonObject();
        tools.addProperty("listChanged", false);
        caps.add("tools", tools);
        result.add("capabilities", caps);
        JsonObject info = new JsonObject();
        info.addProperty("name", "multiplayer-client");
        info.addProperty("version", "2.0.0");
        result.add("serverInfo", info);
        return result(id, result);
    }

    private String handleToolsList(JsonElement id) {
        JsonObject result = new JsonObject();
        JsonArray toolsArr = new JsonArray();
        for (McpSchema.Tool tool : toolRegistry.getToolDefs().values()) {
            toolsArr.add(toolToJson(tool));
        }
        result.add("tools", toolsArr);
        return result(id, result);
    }

    private JsonObject toolToJson(McpSchema.Tool tool) {
        JsonObject t = new JsonObject();
        t.addProperty("name", tool.name());
        t.addProperty("description", tool.description() != null ? tool.description() : "");
        if (tool.inputSchema() != null) {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            Map<String, Object> props = tool.inputSchema().properties();
            if (props != null) {
                JsonObject pd = new JsonObject();
                for (var entry : props.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                    JsonObject p = new JsonObject();
                    p.addProperty("type", (String) propDef.get("type"));
                    if (propDef.containsKey("description"))
                        p.addProperty("description", (String) propDef.get("description"));
                    if (propDef.containsKey("default"))
                        p.add("default", gson.toJsonTree(propDef.get("default")));
                    pd.add(entry.getKey(), p);
                }
                schema.add("properties", pd);
            }
            java.util.List<String> required = tool.inputSchema().required();
            if (required != null) {
                JsonArray reqArr = new JsonArray();
                for (String r : required) reqArr.add(r);
                schema.add("required", reqArr);
            }
            t.add("inputSchema", schema);
        }
        return t;
    }

    private String handleToolsCall(JsonElement id, JsonObject req) {
        JsonObject params = req.getAsJsonObject("params");
        String toolName = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        Map<String, Object> argsMap = jsonToMap(arguments);
        BiFunction<Object, Map<String, Object>, Mono<McpSchema.CallToolResult>> handler =
            (BiFunction<Object, Map<String, Object>, Mono<McpSchema.CallToolResult>>)
                (Object) toolRegistry.getHandler(toolName);

        if (handler == null) {
            return error(id, -32601, "Tool not found: " + toolName);
        }

        McpSchema.CallToolResult result = handler.apply(null, argsMap).block(Duration.ofSeconds(30));
        return result(id, callToolResultToJson(result));
    }

    private Map<String, Object> jsonToMap(JsonObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (var entry : obj.entrySet()) {
            JsonElement val = entry.getValue();
            if (val.isJsonPrimitive()) {
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isString()) map.put(entry.getKey(), prim.getAsString());
                else if (prim.isNumber()) map.put(entry.getKey(), prim.getAsDouble());
                else if (prim.isBoolean()) map.put(entry.getKey(), prim.getAsBoolean());
            } else {
                map.put(entry.getKey(), val.toString());
            }
        }
        return map;
    }

    private static JsonObject callToolResultToJson(McpSchema.CallToolResult result) {
        JsonObject json = new JsonObject();
        JsonArray content = new JsonArray();
        if (result.content() != null) {
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent tc) {
                    JsonObject item = new JsonObject();
                    item.addProperty("type", "text");
                    item.addProperty("text", tc.text());
                    content.add(item);
                }
            }
        }
        json.add("content", content);
        if (result.isError()) json.addProperty("isError", true);
        return json;
    }

    private static String result(JsonElement id, JsonObject result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id != null ? id : JsonNull.INSTANCE);
        resp.add("result", result);
        return resp.toString();
    }

    private static String error(JsonElement id, int code, String msg) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id != null ? id : JsonNull.INSTANCE);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", msg);
        resp.add("error", err);
        return resp.toString();
    }
}
