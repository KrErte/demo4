package com.example.mcp.connectors;

import com.example.mcp.config.McpConfig;
import com.example.mcp.core.McpException;
import com.example.mcp.core.ToolRegistry;
import com.example.mcp.model.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Connector for file system operations with path allowlist enforcement.
 */
@Component
public class FileSystemConnector {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemConnector.class);

    private final McpConfig config;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public FileSystemConnector(McpConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void registerTools() {
        if (!config.getFilesystem().isEnabled()) {
            logger.info("FileSystem connector is disabled");
            return;
        }

        toolRegistry.register(createReadFileTool());
        toolRegistry.register(createListDirTool());
        logger.info("FileSystem connector registered tools: fs.readFile, fs.listDir");
    }

    private ToolDefinition createReadFileTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "Absolute path to the file to read");
        schema.putArray("required").add("path");

        return new ToolDefinition(
            "fs.readFile",
            "Read the contents of a file from the filesystem. Only paths in the allowlist are permitted.",
            schema,
            this::readFile
        );
    }

    private ToolDefinition createListDirTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "Absolute path to the directory to list");
        schema.putArray("required").add("path");

        return new ToolDefinition(
            "fs.listDir",
            "List contents of a directory. Only paths in the allowlist are permitted.",
            schema,
            this::listDir
        );
    }

    private Object readFile(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            throw new McpException("Path is required", "INVALID_ARGUMENT");
        }

        Path path = Paths.get(pathStr).toAbsolutePath().normalize();
        validatePath(path);

        try {
            long size = Files.size(path);
            if (size > config.getFilesystem().getMaxFileSizeBytes()) {
                throw new McpException(
                    String.format("File too large: %d bytes (max: %d)", size, config.getFilesystem().getMaxFileSizeBytes()),
                    "FILE_TOO_LARGE"
                );
            }

            String content = Files.readString(path);
            return Map.of(
                "path", path.toString(),
                "content", content,
                "size", size
            );
        } catch (IOException e) {
            throw new McpException("Failed to read file: " + e.getMessage(), "IO_ERROR", e);
        }
    }

    private Object listDir(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        if (pathStr == null || pathStr.isBlank()) {
            throw new McpException("Path is required", "INVALID_ARGUMENT");
        }

        Path path = Paths.get(pathStr).toAbsolutePath().normalize();
        validatePath(path);

        if (!Files.isDirectory(path)) {
            throw new McpException("Path is not a directory: " + path, "NOT_A_DIRECTORY");
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<Map<String, Object>> entries = stream.map(p -> {
                try {
                    return Map.<String, Object>of(
                        "name", p.getFileName().toString(),
                        "path", p.toString(),
                        "isDirectory", Files.isDirectory(p),
                        "size", Files.isRegularFile(p) ? Files.size(p) : 0L
                    );
                } catch (IOException e) {
                    return Map.<String, Object>of(
                        "name", p.getFileName().toString(),
                        "path", p.toString(),
                        "error", e.getMessage()
                    );
                }
            }).toList();

            return Map.of(
                "path", path.toString(),
                "entries", entries,
                "count", entries.size()
            );
        } catch (IOException e) {
            throw new McpException("Failed to list directory: " + e.getMessage(), "IO_ERROR", e);
        }
    }

    private void validatePath(Path path) {
        List<String> allowedPaths = config.getFilesystem().getAllowedPaths();
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            throw McpException.policyDenied("No paths are allowed - filesystem access is restricted");
        }

        String normalizedPath = path.toString();
        boolean allowed = allowedPaths.stream()
            .map(p -> Paths.get(p).toAbsolutePath().normalize().toString())
            .anyMatch(allowedPath -> normalizedPath.startsWith(allowedPath));

        if (!allowed) {
            throw McpException.policyDenied("Path not in allowlist: " + path);
        }

        // Additional safety checks
        if (normalizedPath.contains("..")) {
            throw McpException.policyDenied("Path traversal detected: " + path);
        }
    }
}
