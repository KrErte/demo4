package com.example.mcp.connectors;

import com.example.mcp.config.McpConfig;
import com.example.mcp.core.McpException;
import com.example.mcp.core.ToolRegistry;
import com.example.mcp.model.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Connector for HTTP GET requests with domain allowlist enforcement.
 */
@Component
public class HttpFetchConnector {

    private static final Logger logger = LoggerFactory.getLogger(HttpFetchConnector.class);

    private final McpConfig config;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpFetchConnector(McpConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.getHttpFetch().getTimeoutSeconds()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @PostConstruct
    public void registerTools() {
        if (!config.getHttpFetch().isEnabled()) {
            logger.info("HttpFetch connector is disabled");
            return;
        }

        toolRegistry.register(createFetchTool());
        logger.info("HttpFetch connector registered tool: web.fetch");
    }

    private ToolDefinition createFetchTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode urlProp = properties.putObject("url");
        urlProp.put("type", "string");
        urlProp.put("description", "The URL to fetch (GET only). Must be from an allowed domain.");

        ObjectNode headersProp = properties.putObject("headers");
        headersProp.put("type", "object");
        headersProp.put("description", "Optional HTTP headers to include in the request");
        headersProp.putObject("additionalProperties").put("type", "string");

        schema.putArray("required").add("url");

        return new ToolDefinition(
            "web.fetch",
            "Fetch content from a URL using HTTP GET. Only allowed domains can be accessed.",
            schema,
            this::fetch
        );
    }

    @SuppressWarnings("unchecked")
    private Object fetch(Map<String, Object> args) {
        String urlStr = (String) args.get("url");
        if (urlStr == null || urlStr.isBlank()) {
            throw new McpException("URL is required", "INVALID_ARGUMENT");
        }

        Map<String, String> headers = (Map<String, String>) args.get("headers");

        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            throw new McpException("Invalid URL: " + e.getMessage(), "INVALID_URL");
        }

        validateDomain(uri);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .timeout(Duration.ofSeconds(config.getHttpFetch().getTimeoutSeconds()));

            // Add custom headers if provided
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    requestBuilder.header(header.getKey(), header.getValue());
                }
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // Read response with size limit
            byte[] body = readWithLimit(response.body(), config.getHttpFetch().getMaxResponseSizeBytes());

            return Map.of(
                "url", uri.toString(),
                "statusCode", response.statusCode(),
                "headers", response.headers().map(),
                "body", new String(body),
                "size", body.length
            );

        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            throw new McpException("HTTP request failed: " + e.getMessage(), "HTTP_ERROR", e);
        }
    }

    private void validateDomain(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new McpException("Invalid URL: no host specified", "INVALID_URL");
        }

        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw McpException.policyDenied("Only HTTP and HTTPS schemes are allowed");
        }

        List<String> allowedDomains = config.getHttpFetch().getAllowedDomains();
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            throw McpException.policyDenied("No domains are allowed - HTTP access is restricted");
        }

        boolean allowed = allowedDomains.stream()
            .anyMatch(domain -> host.equalsIgnoreCase(domain) || host.endsWith("." + domain));

        if (!allowed) {
            throw McpException.policyDenied("Domain not in allowlist: " + host);
        }
    }

    private byte[] readWithLimit(InputStream inputStream, long maxBytes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        long totalRead = 0;

        while ((bytesRead = inputStream.read(data)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxBytes) {
                throw new McpException(
                    String.format("Response too large: exceeds %d bytes", maxBytes),
                    "RESPONSE_TOO_LARGE"
                );
            }
            buffer.write(data, 0, bytesRead);
        }

        return buffer.toByteArray();
    }
}
