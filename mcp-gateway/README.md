# MCP Gateway

A minimal, production-grade tool gateway in Java with a web dashboard for testing.

## What Is This?

MCP Gateway is a secure "middleman" between AI assistants and your business systems. It:

- **Controls access** - Only allowed operations can be performed
- **Logs everything** - Every request is tracked for auditing
- **Provides demo data** - Mock accounting, inventory, and HR data for testing

## Quick Start

```bash
./gradlew bootRun
```

Open **http://localhost:8080** in your browser.

### Setting Up API Key in the UI

1. Click **"Täpsem"** (Advanced) button in the top-right of the chat interface
2. Enter your API key in the **"API võti"** field
3. The key is saved in your browser's localStorage and persists across sessions

For development, the default API key is `dev-key`.

## Available Tools

### Business Tools (Mock Demo Data)

| Tool | Description | Example Parameters |
|------|-------------|-------------------|
| `accounting.getInvoices` | Get customer invoices | `{"status": "unpaid"}` |
| `accounting.getBalance` | View account balances | `{}` or `{"account": "checking"}` |
| `accounting.getReport` | Generate financial reports | `{"type": "profit_loss"}` |
| `inventory.getProducts` | View product inventory | `{"lowStock": true}` |
| `inventory.getOrders` | View recent orders | `{"status": "pending"}` |
| `hr.getEmployees` | View employee directory | `{"department": "Engineering"}` |
| `hr.getPayroll` | View payroll summary | `{"month": "2024-12"}` |

### System Tools

| Tool | Description | Example Parameters |
|------|-------------|-------------------|
| `fs.listDir` | List directory contents | `{"path": "/tmp"}` |
| `fs.readFile` | Read a file | `{"path": "/tmp/test.txt"}` |
| `web.fetch` | Fetch a URL | `{"url": "https://httpbin.org/get"}` |

## API Endpoints

All `/api/*` endpoints require header: `X-API-Key: <your-key>`

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/tools` | GET | List all available tools |
| `/api/call` | POST | Execute a tool |
| `/api/metrics` | GET | Get request statistics |
| `/api/activity` | GET | Recent activity log |

## Example API Calls

### Get Unpaid Invoices

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "accounting.getInvoices", "arguments": {"status": "unpaid"}}'
```

### View Engineering Team

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "hr.getEmployees", "arguments": {"department": "Engineering"}}'
```

### Generate Profit & Loss Report

```bash
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "accounting.getReport", "arguments": {"type": "profit_loss"}}'
```

## Example Responses

### Invoice List
```json
{
  "ok": true,
  "result": {
    "invoices": [
      {
        "id": "INV-2024-1001",
        "customer": "TechStart OU",
        "amount": 2450.00,
        "status": "unpaid",
        "dueDate": "2024-12-25"
      }
    ],
    "count": 5,
    "totalAmount": 12500.00
  }
}
```

### Financial Report
```json
{
  "ok": true,
  "result": {
    "report": "Kasumiaruanne",
    "revenue": { "total": 121000.00 },
    "expenses": { "total": 60450.00 },
    "netProfit": 60550.00,
    "profitMargin": "50.0%"
  }
}
```

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
mcp:
  policy:
    default-deny: true        # Block by default
    timeout-ms: 30000         # Request timeout
    max-result-bytes: 1048576 # 1MB max result

  fs:
    allowlist:                # Allowed directories
      - /tmp
      - ./sandbox

  web:
    allow-domains:            # Allowed HTTP domains
      - httpbin.org
      - api.github.com

  security:
    api-key: ${MCP_SECURITY_API_KEY:dev-key}
```

## Production Deployment

### API Key Configuration

**IMPORTANT:** Never use `dev-key` in production!

Set the API key via environment variable:

```bash
export MCP_SECURITY_API_KEY=your-secure-random-key-here
./gradlew bootRun
```

Or via JVM argument:

```bash
java -jar mcp-gateway.jar --mcp.security.api-key=your-secure-key
```

### Production Checklist

- [ ] Generate a secure random API key (min 32 characters)
- [ ] Set `MCP_SECURITY_API_KEY` environment variable
- [ ] Review and restrict `fs.allowlist` paths
- [ ] Review and restrict `web.allow-domains`
- [ ] Enable HTTPS (use a reverse proxy like nginx)
- [ ] Set up log aggregation and monitoring
- [ ] Configure firewall rules to restrict access

### Generating a Secure API Key

```bash
# Linux/macOS
openssl rand -base64 32

# Or using /dev/urandom
head -c 32 /dev/urandom | base64
```

### Docker Deployment

```bash
docker run -d \
  -p 8080:8080 \
  -e MCP_SECURITY_API_KEY=your-secure-key \
  mcp-gateway:latest
```

## Security Features

- **API Key Authentication** - All requests require valid API key
- **Default Deny Policy** - Only whitelisted resources accessible
- **Request Logging** - Every call logged with timestamp
- **Size Limits** - Prevents large result attacks
- **Timeout Protection** - Prevents hanging requests
- **LocalStorage Key Storage** - API key stored client-side, never exposed in URLs

## Project Structure

```
mcp-gateway/
├── src/main/java/com/example/mcp/
│   ├── config/           # Configuration classes
│   ├── controller/       # REST API endpoints
│   ├── filter/           # API key authentication
│   ├── model/            # Data models
│   └── service/          # Business logic & mock data
├── src/main/resources/
│   ├── static/           # Web dashboard
│   └── application.yml   # Configuration
└── build.gradle          # Build file
```

## Requirements

- Java 17+
- No database needed (uses mock data)

## Tests

```bash
./gradlew test
```

## License

MIT
