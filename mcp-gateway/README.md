# Enterprise MCP Gateway

A production-grade Model Context Protocol (MCP) server implementation in Java that provides secure, policy-controlled access to enterprise resources.

## What is This?

The Enterprise MCP Gateway is a Java-based MCP server that acts as a secure intermediary between AI assistants and enterprise systems. It exposes tools and resources through the MCP protocol while enforcing strict security policies, maintaining audit logs, and providing safe-by-default execution.

## Why Java MCP Gateway?

- **Enterprise-Ready**: Built on Spring Boot 3 with Java 21 for production deployments
- **Policy-Driven Security**: YAML-based policy engine with default-deny, allowlists, and per-tool configurations
- **Comprehensive Auditing**: Every invocation is logged with SHA-256 hashed arguments, decision reasons, and timing
- **Safe by Default**: Strict allowlists, blocked dangerous operations, read-only database access
- **Extensible**: Easy to add new connectors for additional data sources and tools
- **Dual Transport**: Supports both stdio (for local use) and HTTP (for networked deployments)

## Features

### Core Capabilities

- **Tool Registry**: Dynamic registration of tools from connectors
- **Policy Engine**: YAML-based rules for access control, timeouts, and result size limits
- **Audit Logging**: Structured JSON audit events for all invocations
- **Schema Validation**: JSON Schema validation for tool arguments

### Built-in Connectors

| Connector | Tools | Description |
|-----------|-------|-------------|
| FileSystem | `fs.readFile`, `fs.listDir` | Read files and list directories with path allowlist |
| HttpFetch | `web.fetch` | HTTP GET requests with domain allowlist |
| PostgreSQL | `db.query`, `db.schema` | Read-only SQL queries with dangerous keyword blocking |

## Quick Start

### Prerequisites

- Java 21+
- Gradle 8+
- (Optional) PostgreSQL for database connector

### Build

```bash
cd mcp-gateway
./gradlew build
```

### Run

#### Stdio Transport (for local AI assistant integration)

```bash
./gradlew bootRun
```

The server reads JSON-RPC requests from stdin and writes responses to stdout.

#### HTTP Transport

```bash
./gradlew bootRun
```

Access the HTTP endpoint at `http://localhost:8080/mcp`

### Test

```bash
./gradlew test
```

## Configuration

Configuration is managed through `src/main/resources/application.yml`.

### Server Configuration

```yaml
mcp:
  server:
    name: mcp-gateway
    version: 1.0.0
    stdio-enabled: true
    http-enabled: true
    http-port: 8080
```

### Connector Configuration

```yaml
mcp:
  filesystem:
    enabled: true
    allowed-paths:
      - /tmp
      - /home
    max-file-size-bytes: 10485760

  http-fetch:
    enabled: true
    allowed-domains:
      - api.github.com
      - httpbin.org
    max-response-size-bytes: 5242880

  postgres:
    enabled: false
    url: jdbc:postgresql://localhost:5432/mydb
    username: readonly_user
    password: ${POSTGRES_PASSWORD:}
```

### Policy Configuration

```yaml
mcp:
  policy:
    default-deny: true
    default-timeout-ms: 30000
    default-max-result-bytes: 1048576

    allow-tools:
      - fs.readFile
      - fs.listDir
      - web.fetch

    deny-tools: []

    per-tool:
      fs.readFile:
        allow: true
        timeout-ms: 10000
        max-result-bytes: 10485760
        allowed-args:
          - path
```

## MCP Protocol

### Supported Methods

| Method | Description |
|--------|-------------|
| `initialize` | Initialize the server connection |
| `tools/list` | List all available tools |
| `tools/call` | Invoke a tool with arguments |
| `ping` | Health check |

### Example Requests

#### List Tools

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list"
}
```

#### Call Tool

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "fs.readFile",
    "arguments": {
      "path": "/tmp/example.txt"
    }
  }
}
```

## Adding a New Connector

1. Create a new class in `com.example.mcp.connectors`:

```java
@Component
public class MyConnector {

    private final McpConfig config;
    private final ToolRegistry toolRegistry;

    public MyConnector(McpConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void registerTools() {
        // Create JSON schema for tool arguments
        ObjectNode schema = new ObjectMapper().createObjectNode();
        schema.put("type", "object");
        // ... define properties

        // Register the tool
        toolRegistry.register(new ToolDefinition(
            "my.tool",
            "Description of what this tool does",
            schema,
            this::myToolImplementation
        ));
    }

    private Object myToolImplementation(Map<String, Object> args) {
        // Implement tool logic
        return Map.of("result", "value");
    }
}
```

2. Add configuration in `McpConfig.java` if needed

3. Update `application.yml` with configuration and policy rules

4. Add the tool to `allow-tools` list in policy configuration

## HTTP API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp` | POST | JSON-RPC endpoint for MCP requests |
| `/mcp/health` | GET | Health check |
| `/mcp/info` | GET | Server information |

## Audit Log Format

Every tool invocation produces a structured JSON audit event:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "toolName": "fs.readFile",
  "actor": "local-user",
  "argsHash": "a3f2...",
  "decision": "ALLOW",
  "reason": "Policy check passed",
  "durationMs": 45,
  "invocationId": "uuid-here"
}
```

## Project Structure

```
mcp-gateway/
├── src/main/java/com/example/mcp/
│   ├── core/
│   │   ├── McpServer.java          # Main server with stdio/HTTP
│   │   ├── ToolRegistry.java       # Tool registration
│   │   ├── PolicyEngine.java       # Policy evaluation
│   │   ├── AuditService.java       # Audit logging
│   │   ├── McpRequestHandler.java  # Request lifecycle
│   │   └── McpException.java       # Exception handling
│   ├── connectors/
│   │   ├── FileSystemConnector.java
│   │   ├── HttpFetchConnector.java
│   │   └── PostgresReadOnlyConnector.java
│   ├── model/
│   │   ├── ToolDefinition.java
│   │   ├── ToolInvocation.java
│   │   └── AuditEvent.java
│   ├── config/
│   │   ├── McpConfig.java
│   │   └── PolicyConfig.java
│   └── Application.java
├── src/main/resources/
│   └── application.yml
├── src/test/java/com/example/mcp/
│   ├── PolicyEngineTest.java
│   └── ToolInvocationTest.java
├── build.gradle
├── README.md
└── SECURITY.md
```

## License

MIT License
