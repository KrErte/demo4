# MCP Gateway Demo

**A simple tool gateway that lets AI assistants safely access your business data.**

---

## What Is This?

Imagine you have an AI assistant (like ChatGPT or Claude) that needs to look up information from your company's systems - invoices, employees, inventory, etc. But you can't just give the AI full access to everything!

**MCP Gateway** acts as a secure middleman:

```
   AI Assistant  ──>  MCP Gateway  ──>  Your Business Systems
                     (security)         (invoices, HR, etc.)
```

The gateway:
- Controls **what** the AI can access
- Logs **every** request for auditing
- Blocks unauthorized requests automatically

---

## Live Demo

### Start the Server

```bash
cd mcp-gateway
./gradlew bootRun
```

### Open the Dashboard

Go to **http://localhost:8080** in your browser.

You'll see a dashboard where you can test all available tools!

---

## Available Demo Tools

### Business Tools (Mock Data)

| Tool | What It Does | Try It! |
|------|--------------|---------|
| `accounting.getInvoices` | View customer invoices | Filter by status: paid, unpaid, overdue |
| `accounting.getBalance` | Check account balances | See bank accounts, receivables, payables |
| `accounting.getReport` | Generate financial reports | Profit/Loss, Balance Sheet, Cash Flow |
| `inventory.getProducts` | View product inventory | Check stock levels, low stock alerts |
| `inventory.getOrders` | View recent orders | Track order status |
| `hr.getEmployees` | View employee directory | Filter by department |
| `hr.getPayroll` | View payroll summary | Monthly salary data with taxes |

### System Tools

| Tool | What It Does |
|------|--------------|
| `fs.listDir` | List files in a directory |
| `fs.readFile` | Read a file's contents |
| `web.fetch` | Fetch data from a website |

---

## How to Use

### From the Dashboard

1. Open **http://localhost:8080**
2. Select a tool from the dropdown
3. Click a quick example or enter your own parameters
4. Click **Execute** and see the results!

### From the Command Line

```bash
# Get a list of invoices
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "accounting.getInvoices", "arguments": {"status": "unpaid"}}'

# Check account balances
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "accounting.getBalance", "arguments": {}}'

# View employees in Engineering
curl -X POST http://localhost:8080/api/call \
  -H "X-API-Key: dev-key" \
  -H "Content-Type: application/json" \
  -d '{"tool": "hr.getEmployees", "arguments": {"department": "Engineering"}}'
```

---

## Example Responses

### Invoices
```json
{
  "invoices": [
    {
      "id": "INV-2024-1000",
      "customer": "TechStart OU",
      "amount": 2450.00,
      "status": "unpaid",
      "dueDate": "2024-12-15"
    }
  ],
  "totalAmount": 12500.00
}
```

### Employee List
```json
{
  "employees": [
    {
      "name": "Kristjan Tamm",
      "department": "Engineering",
      "position": "Senior Developer",
      "salary": 4500
    }
  ],
  "count": 3
}
```

### Financial Report
```json
{
  "report": "Kasumiaruanne",
  "revenue": { "total": 121000.00 },
  "expenses": { "total": 60450.00 },
  "netProfit": 60550.00,
  "profitMargin": "50.0%"
}
```

---

## Security Features

| Feature | Description |
|---------|-------------|
| **API Key Required** | Every request needs a valid API key |
| **Default Deny** | Only whitelisted paths/domains are accessible |
| **Activity Logging** | Every tool call is logged with timestamp |
| **Request Limits** | Timeouts and size limits prevent abuse |

---

## Project Structure

```
mcp-gateway/
├── src/main/java/           # Java source code
│   └── com/example/mcp/
│       ├── service/         # Business logic & mock data
│       ├── controller/      # REST API endpoints
│       └── config/          # Configuration
├── src/main/resources/
│   ├── static/index.html    # Web dashboard
│   └── application.yml      # Settings
└── build.gradle             # Build configuration
```

---

## Configuration

Edit `mcp-gateway/src/main/resources/application.yml` to:

- Change the API key
- Allow/block specific directories or websites
- Enable/disable features

---

## Requirements

- Java 17 or higher
- No database required (uses mock data)

---

## License

MIT
