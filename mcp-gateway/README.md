# MCP Gateway

A minimal, production-grade MCP tool server in Java with a web dashboard.

## Features

- **3 Built-in Tools**: `fs.listDir`, `fs.readFile`, `web.fetch`
- **Web Dashboard**: Test tools, view metrics, monitor activity
- **Policy Engine**: Default-deny with path/domain allowlists
- **API Key Security**: Protect API endpoints
- **In-Memory Metrics**: Track allowed/denied requests

## Quick Start

```bash
./gradlew bootRun
```

Open **http://localhost:8080** in your browser.

## API Endpoints

All `/api/*` endpoints require header: `X-API-Key: dev-key`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/tools` | GET | List registered tools |
| `/api/call` | POST | Call a tool |
| `/api/metrics` | GET | Get counters and config |
| `/api/activity` | GET | Recent activity log |
| `/api/config` | GET | Get API key (for dashboard) |

## Example curl Commands

### List Tools

```bash
curl -H "X-API-Key: dev-key" http://localhost:8080/api/tools
```

### List Directory

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "fs.listDir", "arguments": {"path": "/tmp"}}'
```

### Read File

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "fs.readFile", "arguments": {"path": "/tmp/test.txt"}}'
```

### Fetch URL

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "web.fetch", "arguments": {"url": "https://httpbin.org/get"}}'
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
mcp:
  policy:
    default-deny: true          # Deny paths/domains not in allowlist
    timeout-ms: 30000           # Request timeout
    max-result-bytes: 1048576   # 1MB max result size

  fs:
    allowlist:                  # Allowed filesystem paths
      - /tmp
      - ./sandbox

  web:
    allow-domains:              # Allowed HTTP domains
      - httpbin.org
      - api.github.com

  features:
    fs-enabled: true            # Enable filesystem tools
    http-enabled: true          # Enable HTTP fetch tool

  security:
    api-key: dev-key            # API key for authentication
```

## Response Format

### Success

```json
{
  "ok": true,
  "result": { ... },
  "elapsedMs": 45,
  "truncated": false
}
```

### Error

```json
{
  "ok": false,
  "error": "Path not in allowlist: /etc",
  "code": "DENIED"
}
```

Error codes: `DENIED`, `INVALID`, `ERROR`, `TIMEOUT`

## Security Warning

⚠️ **File Access**: Only paths in the allowlist can be accessed. Configure carefully!

⚠️ **API Key**: Change `dev-key` to a strong secret in production.

⚠️ **Domain Allowlist**: Only allowed domains can be fetched.

## Tests

```bash
./gradlew test
```

## Project Structure

```
mcp-gateway/
├── src/main/java/com/example/mcp/
│   ├── config/GatewayConfig.java    # Configuration
│   ├── controller/ApiController.java # REST API
│   ├── filter/ApiKeyFilter.java     # API key auth
│   ├── model/                       # DTOs
│   ├── service/
│   │   ├── ToolService.java         # Tool implementations
│   │   └── MetricsService.java      # Metrics tracking
│   └── Application.java
├── src/main/resources/
│   ├── static/index.html            # Dashboard
│   └── application.yml
└── src/test/java/
    └── ToolServiceTest.java
```

## License

MIT
