package com.david.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class StdioMcpClientManager implements McpClientManager {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final McpConnectionProperties properties;
    private final ObjectMapper objectMapper;

    private final Object ioLock = new Object();
    private final AtomicLong requestId = new AtomicLong(0);

    private volatile Process process;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile boolean initialized;

    private volatile List<String> discoveredTools = List.of();
    private volatile String lastError = "";
    private volatile Instant lastInitializedAt;
    private volatile Instant lastToolsRefreshAt;
    private volatile Instant lastCallAt;

    @Override
    public Mono<List<McpToolDescriptor>> listTools() {
        if (!properties.enabled()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(() -> {
                    synchronized (ioLock) {
                        ensureConnected();
                        JsonNode result = sendRequest("tools/list", Map.of());
                        List<McpToolDescriptor> tools = parseTools(result);
                        discoveredTools = tools.stream().map(McpToolDescriptor::name).toList();
                        lastToolsRefreshAt = Instant.now();
                        clearError();
                        return tools;
                    }
                })
                .doOnError(this::recordError)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Object> callTool(String name, Map<String, Object> arguments) {
        if (!properties.enabled()) {
            return Mono.error(new IllegalStateException("MCP github is disabled"));
        }

        Map<String, Object> safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        return Mono.fromCallable(() -> {
                    synchronized (ioLock) {
                        ensureConnected();
                        JsonNode result = sendRequest("tools/call", Map.of(
                                "name", name,
                                "arguments", safeArguments
                        ));
                        lastCallAt = Instant.now();
                        clearError();

                        JsonNode contentNode = result.path("content");
                        if (contentNode.isMissingNode() || contentNode.isNull()) {
                            return objectMapper.convertValue(result, Object.class);
                        }
                        return objectMapper.convertValue(contentNode, Object.class);
                    }
                })
                .doOnError(this::recordError)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public McpStatus status() {
        Process current = process;
        boolean processAlive = current != null && current.isAlive();
        return new McpStatus(
                properties.enabled(),
                initialized,
                processAlive,
                properties.command(),
                properties.args(),
                discoveredTools,
                lastError,
                lastInitializedAt,
                lastToolsRefreshAt,
                lastCallAt
        );
    }

    private void ensureConnected() throws IOException {
        if (initialized && process != null && process.isAlive()) {
            return;
        }

        startProcess();
        initializeSession();
        initialized = true;
    }

    private void startProcess() throws IOException {
        destroyProcess();

        List<String> command = new ArrayList<>();
        command.add(properties.command());
        command.addAll(properties.args());

        ProcessBuilder builder = new ProcessBuilder(command);
        String workingDirectory = properties.workingDirectory();
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            builder.directory(Path.of(workingDirectory).toFile());
        }

        String token = System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN");
        if (token != null) {
            builder.environment().put("GITHUB_PERSONAL_ACCESS_TOKEN", token);
        }

//        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        process = builder.start();
        input = new BufferedInputStream(process.getInputStream());
        output = new BufferedOutputStream(process.getOutputStream());

        log.info("MCP github process started: command={} args={}", properties.command(), properties.args());
    }

    private void initializeSession() throws IOException {
        Duration timeout = properties.startupTimeout();
        long start = System.nanoTime();

        JsonNode initializeResult = sendRequest("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", "agent-server",
                        "version", "0.0.1"
                )
        ));

        sendNotification("notifications/initialized", Map.of());
        lastInitializedAt = Instant.now();

        long costMs = (System.nanoTime() - start) / 1_000_000;
        log.info("MCP github initialized in {} ms, serverInfo={}", costMs, preview(initializeResult.path("serverInfo")));

        if (timeout != null && !timeout.isZero() && !timeout.isNegative() && costMs > timeout.toMillis()) {
            log.warn("MCP github initialization exceeded configured timeout: {} ms > {} ms", costMs, timeout.toMillis());
        }
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) throws IOException {
        long id = requestId.incrementAndGet();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params == null ? Map.of() : params);

        writeFrame(request);

        while (true) {
            JsonNode message = readFrame();
            JsonNode messageId = message.path("id");

            if (!messageId.isMissingNode() && !messageId.isNull() && Objects.equals(messageId.asLong(), id)) {
                JsonNode errorNode = message.path("error");
                if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                    throw new IllegalStateException("MCP request failed: method=" + method + ", error=" + errorNode);
                }
                return message.path("result");
            }

            if (log.isDebugEnabled()) {
                log.debug("Ignore MCP message while waiting response: {}", preview(message));
            }
        }
    }

    private void sendNotification(String method, Map<String, Object> params) throws IOException {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params == null ? Map.of() : params);
        writeFrame(notification);
    }

    private void writeFrame(Map<String, Object> payload) throws IOException {
        // 1. 直接将对象转为 JSON 字节数组
        byte[] body = objectMapper.writeValueAsBytes(payload);
        log.info("MCP SEND: {}", new String(body, StandardCharsets.UTF_8));
//        String header = "Content-Length: " + body.length + "\r\n\r\n";
//        output.write(header.getBytes(StandardCharsets.US_ASCII));

        // 2. 写入纯 JSON 内容
        output.write(body);
        // 3. ⭐ 关键：写入换行符，告诉 Go 端“这一条 JSON 结束了”
        output.write("\n".getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    private JsonNode readFrame() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            buffer.write(value);
        }
        if (buffer.size() == 0) {
            throw new EOFException("Empty MCP response");
        }
        return objectMapper.readTree(
                buffer.toByteArray()
        );
    }

    private int readContentLength(InputStream stream) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int value = stream.read();
            if (value == -1) {
                throw new EOFException("Unexpected EOF while reading MCP headers");
            }

            headerBytes.write(value);

            if ((state == 0 || state == 2) && value == '\r') {
                state++;
            } else if ((state == 1 || state == 3) && value == '\n') {
                state++;
            } else {
                state = value == '\r' ? 1 : 0;
            }
        }

        String headerText = headerBytes.toString(StandardCharsets.US_ASCII);
        log.info("MCP headerText={}", headerText);
        String[] lines = headerText.split("\\r\\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.startsWith("content-length:")) {
                return Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }

        throw new IllegalStateException("MCP frame missing Content-Length header: " + headerText);
    }

    private List<McpToolDescriptor> parseTools(JsonNode result) {
        JsonNode toolsNode = result.path("tools");
        if (!toolsNode.isArray()) {
            return List.of();
        }

        List<McpToolDescriptor> tools = new ArrayList<>();
        for (JsonNode toolNode : toolsNode) {
            String name = toolNode.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }

            String description = toolNode.path("description").asText("");
            JsonNode schemaNode = toolNode.path("inputSchema");
            if (schemaNode.isMissingNode() || schemaNode.isNull()) {
                schemaNode = toolNode.path("input_schema");
            }
            Map<String, Object> schema = schemaNode.isMissingNode() || schemaNode.isNull()
                    ? Map.of()
                    : objectMapper.convertValue(schemaNode, MAP_TYPE);

            tools.add(new McpToolDescriptor(name, description, schema));
        }

        return List.copyOf(tools);
    }

    private void clearError() {
        lastError = "";
    }

    private void recordError(Throwable error) {
        String message = error == null ? "unknown" : error.getClass().getSimpleName() + ": " + error.getMessage();
        lastError = message;
    }

    private String preview(Object value) {
        String text = String.valueOf(value);
        return text.length() <= 200 ? text : text.substring(0, 200) + "...(truncated)";
    }

    @PreDestroy
    public void shutdown() {
        synchronized (ioLock) {
            destroyProcess();
        }
    }

    private void destroyProcess() {
        initialized = false;

        closeQuietly(output);
        output = null;

        closeQuietly(input);
        input = null;

        Process current = process;
        process = null;
        if (current != null && current.isAlive()) {
            current.destroy();
            log.info("MCP github process destroyed");
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            log.debug("Close resource failed", exception);
        }
    }
}
