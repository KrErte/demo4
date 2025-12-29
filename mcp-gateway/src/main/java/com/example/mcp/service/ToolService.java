package com.example.mcp.service;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.CallRequest;
import com.example.mcp.model.CallResponse;
import com.example.mcp.model.ToolDef;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ToolService {

    private final GatewayConfig config;
    private final MetricsService metrics;
    private final HttpClient httpClient;
    private final List<ToolDef> tools;

    public ToolService(GatewayConfig config, MetricsService metrics) {
        this.config = config;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.tools = List.of(
            new ToolDef("fs.listDir", "List directory contents. Returns entries with name, type, size, lastModified.",
                Map.of("type", "object",
                    "properties", Map.of("path", Map.of("type", "string", "description", "Directory path to list")),
                    "required", List.of("path"))),
            new ToolDef("fs.readFile", "Read file contents. Returns text or base64 for binary files.",
                Map.of("type", "object",
                    "properties", Map.of("path", Map.of("type", "string", "description", "File path to read")),
                    "required", List.of("path"))),
            new ToolDef("web.fetch", "Fetch URL via HTTP GET. Returns status, headers, and body.",
                Map.of("type", "object",
                    "properties", Map.of("url", Map.of("type", "string", "description", "URL to fetch")),
                    "required", List.of("url")))
        );
    }

    public List<ToolDef> getTools() {
        return tools;
    }

    public CallResponse call(CallRequest request) {
        String tool = request.tool();
        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();

        long start = System.currentTimeMillis();

        try {
            Object result = switch (tool) {
                case "fs.listDir" -> executeListDir(args);
                case "fs.readFile" -> executeReadFile(args);
                case "web.fetch" -> executeWebFetch(args);
                default -> {
                    metrics.recordDenied(tool, "Unknown tool");
                    yield null;
                }
            };

            if (result == null && !"fs.listDir".equals(tool) && !"fs.readFile".equals(tool) && !"web.fetch".equals(tool)) {
                return CallResponse.error("Unknown tool: " + tool, "INVALID");
            }

            long elapsed = System.currentTimeMillis() - start;

            if (result instanceof ErrorResult err) {
                if ("DENIED".equals(err.code)) {
                    metrics.recordDenied(tool, err.message);
                } else {
                    metrics.recordError(tool, err.message, elapsed);
                }
                return CallResponse.error(err.message, err.code);
            }

            boolean truncated = result instanceof TruncatedResult;
            Object actualResult = truncated ? ((TruncatedResult) result).data : result;

            metrics.recordAllowed(tool, "Success", elapsed);
            return CallResponse.success(actualResult, elapsed, truncated);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            metrics.recordError(tool, e.getMessage(), elapsed);
            return CallResponse.error(e.getMessage(), "ERROR");
        }
    }

    private Object executeListDir(Map<String, Object> args) {
        if (!config.getFeatures().isFsEnabled()) {
            return new ErrorResult("Filesystem access is disabled", "DENIED");
        }

        String pathStr = (String) args.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return new ErrorResult("path is required", "INVALID");
        }

        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!isPathAllowed(path)) {
            return new ErrorResult("Path not in allowlist: " + path, "DENIED");
        }

        if (!Files.isDirectory(path)) {
            return new ErrorResult("Not a directory: " + path, "INVALID");
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<Map<String, Object>> entries = stream.map(p -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", p.getFileName().toString());
                try {
                    entry.put("type", Files.isDirectory(p) ? "dir" : "file");
                    if (Files.isRegularFile(p)) {
                        entry.put("size", Files.size(p));
                    }
                    entry.put("lastModified", Files.getLastModifiedTime(p).toInstant().toString());
                } catch (IOException e) {
                    entry.put("error", e.getMessage());
                }
                return entry;
            }).toList();

            return Map.of("path", path.toString(), "entries", entries, "count", entries.size());
        } catch (IOException e) {
            return new ErrorResult("Failed to list directory: " + e.getMessage(), "ERROR");
        }
    }

    private Object executeReadFile(Map<String, Object> args) {
        if (!config.getFeatures().isFsEnabled()) {
            return new ErrorResult("Filesystem access is disabled", "DENIED");
        }

        String pathStr = (String) args.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            return new ErrorResult("path is required", "INVALID");
        }

        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!isPathAllowed(path)) {
            return new ErrorResult("Path not in allowlist: " + path, "DENIED");
        }

        if (!Files.isRegularFile(path)) {
            return new ErrorResult("Not a file: " + path, "INVALID");
        }

        try {
            long size = Files.size(path);
            long maxSize = config.getPolicy().getMaxResultBytes();
            boolean truncated = size > maxSize;

            byte[] bytes;
            if (truncated) {
                bytes = new byte[(int) maxSize];
                try (var in = Files.newInputStream(path)) {
                    in.read(bytes);
                }
            } else {
                bytes = Files.readAllBytes(path);
            }

            boolean isBinary = isBinary(bytes);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path.toString());
            result.put("size", size);

            if (isBinary) {
                result.put("encoding", "base64");
                result.put("content", Base64.getEncoder().encodeToString(bytes));
            } else {
                result.put("encoding", "utf-8");
                result.put("content", new String(bytes, StandardCharsets.UTF_8));
            }

            if (truncated) {
                result.put("truncated", true);
                return new TruncatedResult(result);
            }
            return result;

        } catch (IOException e) {
            return new ErrorResult("Failed to read file: " + e.getMessage(), "ERROR");
        }
    }

    private Object executeWebFetch(Map<String, Object> args) {
        if (!config.getFeatures().isHttpEnabled()) {
            return new ErrorResult("HTTP fetch is disabled", "DENIED");
        }

        String urlStr = (String) args.get("url");
        if (urlStr == null || urlStr.isBlank()) {
            return new ErrorResult("url is required", "INVALID");
        }

        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            return new ErrorResult("Invalid URL: " + e.getMessage(), "INVALID");
        }

        String host = uri.getHost();
        if (host == null) {
            return new ErrorResult("Invalid URL: no host", "INVALID");
        }

        if (!isDomainAllowed(host)) {
            return new ErrorResult("Domain not in allowlist: " + host, "DENIED");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .timeout(Duration.ofMillis(config.getPolicy().getTimeoutMs()))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = response.body();
            long maxSize = config.getPolicy().getMaxResultBytes();
            boolean truncated = body.length > maxSize;

            if (truncated) {
                body = Arrays.copyOf(body, (int) maxSize);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("url", urlStr);
            result.put("status", response.statusCode());

            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    headers.put(k, v.get(0));
                }
            });
            result.put("headers", headers);

            boolean isBinary = isBinary(body);
            if (isBinary) {
                result.put("bodyEncoding", "base64");
                result.put("body", Base64.getEncoder().encodeToString(body));
            } else {
                result.put("bodyEncoding", "utf-8");
                result.put("body", new String(body, StandardCharsets.UTF_8));
            }

            result.put("bodySize", response.body().length);

            if (truncated) {
                result.put("truncated", true);
                return new TruncatedResult(result);
            }
            return result;

        } catch (IOException e) {
            return new ErrorResult("HTTP request failed: " + e.getMessage(), "ERROR");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ErrorResult("Request interrupted", "TIMEOUT");
        }
    }

    private boolean isPathAllowed(Path path) {
        if (!config.getPolicy().isDefaultDeny()) {
            return true;
        }

        String normalizedPath = path.toString();
        for (String allowed : config.getFs().getAllowlist()) {
            Path allowedPath = Paths.get(allowed).toAbsolutePath().normalize();
            if (normalizedPath.startsWith(allowedPath.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDomainAllowed(String host) {
        if (!config.getPolicy().isDefaultDeny()) {
            return true;
        }

        for (String allowed : config.getWeb().getAllowDomains()) {
            if (host.equalsIgnoreCase(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBinary(byte[] data) {
        if (data.length == 0) return false;
        int checkLen = Math.min(data.length, 8000);
        int nonPrintable = 0;
        for (int i = 0; i < checkLen; i++) {
            byte b = data[i];
            if (b == 0) return true;
            if (b < 32 && b != 9 && b != 10 && b != 13) {
                nonPrintable++;
            }
        }
        return (nonPrintable * 100 / checkLen) > 10;
    }

    private record ErrorResult(String message, String code) {}
    private record TruncatedResult(Object data) {}
}
