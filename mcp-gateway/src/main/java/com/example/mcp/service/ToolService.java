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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ToolService {

    private final GatewayConfig config;
    private final MetricsService metrics;
    private final HttpClient httpClient;
    private final List<ToolDef> tools;
    private final Random random = new Random(42);

    public ToolService(GatewayConfig config, MetricsService metrics) {
        this.config = config;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.tools = List.of(
            // Real tools
            new ToolDef("fs.listDir", "📁 List directory contents",
                Map.of("type", "object",
                    "properties", Map.of("path", Map.of("type", "string")),
                    "required", List.of("path"))),
            new ToolDef("fs.readFile", "📄 Read file contents",
                Map.of("type", "object",
                    "properties", Map.of("path", Map.of("type", "string")),
                    "required", List.of("path"))),
            new ToolDef("web.fetch", "🌐 Fetch URL via HTTP GET",
                Map.of("type", "object",
                    "properties", Map.of("url", Map.of("type", "string")),
                    "required", List.of("url"))),
            // Demo tools - Accounting
            new ToolDef("accounting.getInvoices", "🧾 Get recent invoices",
                Map.of("type", "object",
                    "properties", Map.of(
                        "status", Map.of("type", "string", "enum", List.of("all", "paid", "unpaid", "overdue")),
                        "limit", Map.of("type", "integer")
                    ))),
            new ToolDef("accounting.getBalance", "💰 Get account balances",
                Map.of("type", "object",
                    "properties", Map.of("account", Map.of("type", "string")))),
            new ToolDef("accounting.getReport", "📊 Generate financial report",
                Map.of("type", "object",
                    "properties", Map.of(
                        "type", Map.of("type", "string", "enum", List.of("profit_loss", "balance_sheet", "cashflow")),
                        "period", Map.of("type", "string", "enum", List.of("month", "quarter", "year"))
                    ))),
            // Demo tools - Inventory
            new ToolDef("inventory.getProducts", "📦 Get product inventory",
                Map.of("type", "object",
                    "properties", Map.of(
                        "category", Map.of("type", "string"),
                        "lowStock", Map.of("type", "boolean")
                    ))),
            new ToolDef("inventory.getOrders", "🛒 Get recent orders",
                Map.of("type", "object",
                    "properties", Map.of(
                        "status", Map.of("type", "string", "enum", List.of("all", "pending", "shipped", "delivered"))
                    ))),
            // Demo tools - HR
            new ToolDef("hr.getEmployees", "👥 Get employee list",
                Map.of("type", "object",
                    "properties", Map.of(
                        "department", Map.of("type", "string"),
                        "active", Map.of("type", "boolean")
                    ))),
            new ToolDef("hr.getPayroll", "💵 Get payroll summary",
                Map.of("type", "object",
                    "properties", Map.of("month", Map.of("type", "string"))))
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
                // Real tools
                case "fs.listDir" -> executeListDir(args);
                case "fs.readFile" -> executeReadFile(args);
                case "web.fetch" -> executeWebFetch(args);
                // Demo tools
                case "accounting.getInvoices" -> mockGetInvoices(args);
                case "accounting.getBalance" -> mockGetBalance(args);
                case "accounting.getReport" -> mockGetReport(args);
                case "inventory.getProducts" -> mockGetProducts(args);
                case "inventory.getOrders" -> mockGetOrders(args);
                case "hr.getEmployees" -> mockGetEmployees(args);
                case "hr.getPayroll" -> mockGetPayroll(args);
                default -> {
                    metrics.recordDenied(tool, "Unknown tool");
                    yield null;
                }
            };

            if (result == null) {
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

    // ==================== MOCK DATA GENERATORS ====================

    private Object mockGetInvoices(Map<String, Object> args) {
        String status = (String) args.getOrDefault("status", "all");
        int limit = ((Number) args.getOrDefault("limit", 10)).intValue();

        List<String> customers = List.of("Acme Corp", "TechStart OÜ", "Nordic Solutions", "Balti Grupp AS", "CloudNine SIA");
        List<Map<String, Object>> invoices = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, 20); i++) {
            String invStatus = List.of("paid", "unpaid", "overdue").get(random.nextInt(3));
            if (!"all".equals(status) && !status.equals(invStatus)) continue;

            Map<String, Object> inv = new LinkedHashMap<>();
            inv.put("id", "INV-2024-" + String.format("%04d", 1000 + i));
            inv.put("customer", customers.get(random.nextInt(customers.size())));
            inv.put("amount", Math.round(random.nextDouble() * 5000 * 100) / 100.0);
            inv.put("currency", "EUR");
            inv.put("date", LocalDate.now().minusDays(random.nextInt(60)).toString());
            inv.put("dueDate", LocalDate.now().plusDays(random.nextInt(30) - 15).toString());
            inv.put("status", invStatus);
            invoices.add(inv);
        }

        double total = invoices.stream().mapToDouble(i -> (Double) i.get("amount")).sum();

        return Map.of(
            "invoices", invoices,
            "count", invoices.size(),
            "totalAmount", Math.round(total * 100) / 100.0,
            "currency", "EUR"
        );
    }

    private Object mockGetBalance(Map<String, Object> args) {
        Map<String, Object> accounts = new LinkedHashMap<>();

        accounts.put("checking", Map.of(
            "name", "Arvelduskonto",
            "balance", 45892.50,
            "currency", "EUR",
            "bank", "Swedbank"
        ));
        accounts.put("savings", Map.of(
            "name", "Hoiukonto",
            "balance", 125000.00,
            "currency", "EUR",
            "bank", "LHV"
        ));
        accounts.put("receivables", Map.of(
            "name", "Nõuded ostjatele",
            "balance", 18450.00,
            "currency", "EUR"
        ));
        accounts.put("payables", Map.of(
            "name", "Võlad tarnijatele",
            "balance", -8920.00,
            "currency", "EUR"
        ));

        String account = (String) args.get("account");
        if (account != null && accounts.containsKey(account)) {
            return accounts.get(account);
        }

        return Map.of(
            "accounts", accounts,
            "totalAssets", 189342.50,
            "totalLiabilities", 8920.00,
            "netWorth", 180422.50,
            "currency", "EUR",
            "asOf", LocalDate.now().toString()
        );
    }

    private Object mockGetReport(Map<String, Object> args) {
        String type = (String) args.getOrDefault("type", "profit_loss");
        String period = (String) args.getOrDefault("period", "month");

        return switch (type) {
            case "profit_loss" -> Map.of(
                "report", "Kasumiaruanne",
                "period", period,
                "startDate", LocalDate.now().withDayOfMonth(1).toString(),
                "endDate", LocalDate.now().toString(),
                "revenue", Map.of(
                    "sales", 87500.00,
                    "services", 32000.00,
                    "other", 1500.00,
                    "total", 121000.00
                ),
                "expenses", Map.of(
                    "salaries", 45000.00,
                    "rent", 3500.00,
                    "utilities", 850.00,
                    "marketing", 5200.00,
                    "software", 2100.00,
                    "other", 3800.00,
                    "total", 60450.00
                ),
                "netProfit", 60550.00,
                "profitMargin", "50.0%",
                "currency", "EUR"
            );
            case "balance_sheet" -> Map.of(
                "report", "Bilanss",
                "asOf", LocalDate.now().toString(),
                "assets", Map.of(
                    "cash", 45892.50,
                    "receivables", 18450.00,
                    "inventory", 32000.00,
                    "equipment", 85000.00,
                    "total", 181342.50
                ),
                "liabilities", Map.of(
                    "payables", 8920.00,
                    "loans", 25000.00,
                    "total", 33920.00
                ),
                "equity", 147422.50,
                "currency", "EUR"
            );
            case "cashflow" -> Map.of(
                "report", "Rahavoogude aruanne",
                "period", period,
                "operating", Map.of(
                    "inflows", 115000.00,
                    "outflows", -58000.00,
                    "net", 57000.00
                ),
                "investing", Map.of(
                    "equipmentPurchase", -15000.00,
                    "net", -15000.00
                ),
                "financing", Map.of(
                    "loanRepayment", -5000.00,
                    "net", -5000.00
                ),
                "netCashflow", 37000.00,
                "currency", "EUR"
            );
            default -> new ErrorResult("Unknown report type", "INVALID");
        };
    }

    private Object mockGetProducts(Map<String, Object> args) {
        String category = (String) args.get("category");
        boolean lowStockOnly = Boolean.TRUE.equals(args.get("lowStock"));

        List<Map<String, Object>> products = List.of(
            Map.of("sku", "LAPTOP-001", "name", "ThinkPad X1 Carbon", "category", "electronics", "stock", 12, "minStock", 5, "price", 1299.00),
            Map.of("sku", "LAPTOP-002", "name", "MacBook Pro 14\"", "category", "electronics", "stock", 8, "minStock", 5, "price", 2199.00),
            Map.of("sku", "MONITOR-001", "name", "Dell U2723QE 27\"", "category", "electronics", "stock", 3, "minStock", 10, "price", 649.00),
            Map.of("sku", "CHAIR-001", "name", "Herman Miller Aeron", "category", "furniture", "stock", 15, "minStock", 5, "price", 1395.00),
            Map.of("sku", "DESK-001", "name", "Standing Desk Pro", "category", "furniture", "stock", 2, "minStock", 5, "price", 899.00),
            Map.of("sku", "KEYBOARD-001", "name", "Keychron Q1 Pro", "category", "accessories", "stock", 25, "minStock", 10, "price", 199.00),
            Map.of("sku", "MOUSE-001", "name", "Logitech MX Master 3S", "category", "accessories", "stock", 4, "minStock", 15, "price", 99.00)
        );

        List<Map<String, Object>> filtered = products.stream()
            .filter(p -> category == null || p.get("category").equals(category))
            .filter(p -> !lowStockOnly || (Integer) p.get("stock") < (Integer) p.get("minStock"))
            .map(p -> {
                Map<String, Object> copy = new LinkedHashMap<>(p);
                copy.put("lowStock", (Integer) p.get("stock") < (Integer) p.get("minStock"));
                return copy;
            })
            .toList();

        return Map.of(
            "products", filtered,
            "count", filtered.size(),
            "lowStockCount", filtered.stream().filter(p -> (Boolean) p.get("lowStock")).count()
        );
    }

    private Object mockGetOrders(Map<String, Object> args) {
        String status = (String) args.getOrDefault("status", "all");
        List<String> customers = List.of("Jüri Tamm", "Mari Mets", "Andres Kask", "Liina Põld", "Peeter Vaher");
        List<String> statuses = List.of("pending", "shipped", "delivered");

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String orderStatus = statuses.get(i % 3);
            if (!"all".equals(status) && !status.equals(orderStatus)) continue;

            orders.add(Map.of(
                "id", "ORD-" + String.format("%06d", 100000 + i),
                "customer", customers.get(i % customers.size()),
                "items", random.nextInt(5) + 1,
                "total", Math.round(random.nextDouble() * 500 * 100) / 100.0,
                "currency", "EUR",
                "status", orderStatus,
                "date", LocalDate.now().minusDays(i).toString()
            ));
        }

        return Map.of(
            "orders", orders,
            "count", orders.size()
        );
    }

    private Object mockGetEmployees(Map<String, Object> args) {
        String department = (String) args.get("department");
        boolean activeOnly = args.get("active") == null || Boolean.TRUE.equals(args.get("active"));

        List<Map<String, Object>> employees = List.of(
            Map.of("id", "EMP001", "name", "Kristjan Tamm", "department", "Engineering", "position", "Senior Developer", "salary", 4500, "active", true),
            Map.of("id", "EMP002", "name", "Kadri Lepp", "department", "Engineering", "position", "Tech Lead", "salary", 5500, "active", true),
            Map.of("id", "EMP003", "name", "Martin Vaher", "department", "Sales", "position", "Account Manager", "salary", 3800, "active", true),
            Map.of("id", "EMP004", "name", "Liis Sepp", "department", "Sales", "position", "Sales Director", "salary", 6000, "active", true),
            Map.of("id", "EMP005", "name", "Peeter Kask", "department", "Finance", "position", "CFO", "salary", 7000, "active", true),
            Map.of("id", "EMP006", "name", "Anna Rebane", "department", "HR", "position", "HR Manager", "salary", 4000, "active", true),
            Map.of("id", "EMP007", "name", "Toomas Mets", "department", "Engineering", "position", "Junior Developer", "salary", 2500, "active", false)
        );

        List<Map<String, Object>> filtered = employees.stream()
            .filter(e -> department == null || e.get("department").equals(department))
            .filter(e -> !activeOnly || Boolean.TRUE.equals(e.get("active")))
            .toList();

        return Map.of(
            "employees", filtered,
            "count", filtered.size(),
            "departments", employees.stream().map(e -> e.get("department")).distinct().toList()
        );
    }

    private Object mockGetPayroll(Map<String, Object> args) {
        String month = (String) args.getOrDefault("month", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));

        return Map.of(
            "month", month,
            "summary", Map.of(
                "grossSalaries", 33300.00,
                "socialTax", 10989.00,
                "incomeTax", 6660.00,
                "unemploymentInsurance", 532.80,
                "netSalaries", 26107.20,
                "totalCost", 44289.00
            ),
            "byDepartment", Map.of(
                "Engineering", Map.of("employees", 3, "gross", 12500.00, "cost", 16625.00),
                "Sales", Map.of("employees", 2, "gross", 9800.00, "cost", 13034.00),
                "Finance", Map.of("employees", 1, "gross", 7000.00, "cost", 9310.00),
                "HR", Map.of("employees", 1, "gross", 4000.00, "cost", 5320.00)
            ),
            "currency", "EUR",
            "paymentDate", month + "-25"
        );
    }

    // ==================== REAL TOOL IMPLEMENTATIONS ====================

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
