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

    // ==================== PILVETARK OÜ - MOCK COMPANY DATA ====================
    //
    // Pilvetark OÜ - Estonian cloud software company
    // Reg: 14892367 | VAT: EE102345678
    // Address: Tartu mnt 83, 10115 Tallinn
    // Founded: 2019 | Employees: 12

    private static final Map<String, Object> COMPANY_INFO = Map.of(
        "name", "Pilvetark OÜ",
        "registryCode", "14892367",
        "vatNumber", "EE102345678",
        "address", "Tartu mnt 83, 10115 Tallinn",
        "email", "info@pilvetark.ee",
        "phone", "+372 5123 4567",
        "founded", "2019-03-15",
        "sector", "IT teenused ja tarkvara"
    );

    // Our clients - realistic Estonian companies
    private static final List<Map<String, Object>> CLIENTS = List.of(
        Map.of("id", "C001", "name", "Eesti Energia AS", "regCode", "10421629", "contact", "Maarika Teder", "email", "maarika.teder@energia.ee"),
        Map.of("id", "C002", "name", "Tallinna Sadam AS", "regCode", "10137319", "contact", "Priit Kuusk", "email", "priit.kuusk@ts.ee"),
        Map.of("id", "C003", "name", "Cleveron AS", "regCode", "11545025", "contact", "Kadri Kütt", "email", "kadri@cleveron.com"),
        Map.of("id", "C004", "name", "Bolt Technology OÜ", "regCode", "14532901", "contact", "Martin Villig", "email", "martin@bolt.eu"),
        Map.of("id", "C005", "name", "Veriff OÜ", "regCode", "14038369", "contact", "Liis Narusk", "email", "liis@veriff.com"),
        Map.of("id", "C006", "name", "Starship Technologies OÜ", "regCode", "14052706", "contact", "Ahti Heinla", "email", "ahti@starship.xyz"),
        Map.of("id", "C007", "name", "Skeleton Technologies OÜ", "regCode", "11678156", "contact", "Taavi Madiberk", "email", "taavi@skeleton.tech")
    );

    // Our team
    private static final List<Map<String, Object>> EMPLOYEES = List.of(
        Map.of("id", "T001", "name", "Kristjan Jõgi", "email", "kristjan@pilvetark.ee", "phone", "+372 5234 5678",
               "department", "Juhatus", "position", "Tegevjuht / CEO", "startDate", "2019-03-15",
               "grossSalary", 6500, "active", true),
        Map.of("id", "T002", "name", "Marika Tamm", "email", "marika@pilvetark.ee", "phone", "+372 5345 6789",
               "department", "Arendus", "position", "Tehniline juht / CTO", "startDate", "2019-03-15",
               "grossSalary", 5800, "active", true),
        Map.of("id", "T003", "name", "Andres Rebane", "email", "andres@pilvetark.ee", "phone", "+372 5456 7890",
               "department", "Arendus", "position", "Senior tarkvaraarendaja", "startDate", "2019-06-01",
               "grossSalary", 4800, "active", true),
        Map.of("id", "T004", "name", "Liina Kask", "email", "liina@pilvetark.ee", "phone", "+372 5567 8901",
               "department", "Arendus", "position", "Senior tarkvaraarendaja", "startDate", "2020-02-01",
               "grossSalary", 4600, "active", true),
        Map.of("id", "T005", "name", "Priit Sepp", "email", "priit@pilvetark.ee", "phone", "+372 5678 9012",
               "department", "Arendus", "position", "Tarkvaraarendaja", "startDate", "2021-09-01",
               "grossSalary", 3800, "active", true),
        Map.of("id", "T006", "name", "Kätlin Mets", "email", "katlin@pilvetark.ee", "phone", "+372 5789 0123",
               "department", "Arendus", "position", "Junior tarkvaraarendaja", "startDate", "2024-01-15",
               "grossSalary", 2400, "active", true),
        Map.of("id", "T007", "name", "Raivo Kõiv", "email", "raivo@pilvetark.ee", "phone", "+372 5890 1234",
               "department", "Müük", "position", "Müügijuht", "startDate", "2020-05-01",
               "grossSalary", 4200, "active", true),
        Map.of("id", "T008", "name", "Helen Vaht", "email", "helen@pilvetark.ee", "phone", "+372 5901 2345",
               "department", "Müük", "position", "Kliendihaldur", "startDate", "2021-03-01",
               "grossSalary", 3200, "active", true),
        Map.of("id", "T009", "name", "Margit Lepp", "email", "margit@pilvetark.ee", "phone", "+372 5012 3456",
               "department", "Finants", "position", "Finantsjuht / CFO", "startDate", "2019-06-01",
               "grossSalary", 5200, "active", true),
        Map.of("id", "T010", "name", "Siim Oja", "email", "siim@pilvetark.ee", "phone", "+372 5123 4560",
               "department", "Finants", "position", "Raamatupidaja", "startDate", "2020-01-01",
               "grossSalary", 2800, "active", true),
        Map.of("id", "T011", "name", "Kaisa Pärn", "email", "kaisa@pilvetark.ee", "phone", "+372 5234 5670",
               "department", "Tugi", "position", "Klienditoe spetsialist", "startDate", "2022-06-01",
               "grossSalary", 2600, "active", true),
        Map.of("id", "T012", "name", "Tõnis Laur", "email", "tonis@pilvetark.ee", "phone", "+372 5345 6780",
               "department", "Arendus", "position", "DevOps insener", "startDate", "2023-02-01",
               "grossSalary", 4400, "active", true)
    );

    // Our services/products
    private static final List<Map<String, Object>> SERVICES = List.of(
        Map.of("code", "SAAS-01", "name", "PilveCRM Pro", "category", "SaaS", "monthlyPrice", 89.00, "activeClients", 34,
               "description", "Kliendihalduse tarkvara väikeettevõtetele"),
        Map.of("code", "SAAS-02", "name", "PilveCRM Enterprise", "category", "SaaS", "monthlyPrice", 249.00, "activeClients", 12,
               "description", "Kliendihalduse tarkvara suurtele organisatsioonidele"),
        Map.of("code", "SAAS-03", "name", "PilveDoc", "category", "SaaS", "monthlyPrice", 49.00, "activeClients", 67,
               "description", "Dokumendihalduse lahendus"),
        Map.of("code", "DEV-01", "name", "Tarkvaraarendus", "category", "Teenus", "hourlyRate", 95.00, "activeClients", 8,
               "description", "Eritarkvara arendus tellimuse alusel"),
        Map.of("code", "DEV-02", "name", "Integratsioonid", "category", "Teenus", "hourlyRate", 85.00, "activeClients", 15,
               "description", "API integratsioonid ja andmevahetus"),
        Map.of("code", "SUP-01", "name", "Haldusteenus Basic", "category", "Tugi", "monthlyPrice", 299.00, "activeClients", 23,
               "description", "IT infrastruktuuri haldus - baaspakett"),
        Map.of("code", "SUP-02", "name", "Haldusteenus Premium", "category", "Tugi", "monthlyPrice", 799.00, "activeClients", 9,
               "description", "IT infrastruktuuri haldus - täisteenindus 24/7")
    );

    private Object mockGetInvoices(Map<String, Object> args) {
        String status = (String) args.getOrDefault("status", "all");
        int limit = ((Number) args.getOrDefault("limit", 10)).intValue();

        // Realistic invoices based on our services and clients
        List<Map<String, Object>> allInvoices = List.of(
            createInvoice("ARV-2024-0156", "C001", "Eesti Energia AS", "2024-12-01", "2024-12-21",
                List.of(Map.of("desc", "PilveCRM Enterprise - detsember 2024", "qty", 1, "price", 249.00),
                        Map.of("desc", "Integratsioonitööd - 12h", "qty", 12, "price", 85.00)), "paid", "2024-12-18"),
            createInvoice("ARV-2024-0157", "C002", "Tallinna Sadam AS", "2024-12-01", "2024-12-21",
                List.of(Map.of("desc", "Haldusteenus Premium - detsember 2024", "qty", 1, "price", 799.00),
                        Map.of("desc", "PilveDoc - 15 kasutajat", "qty", 15, "price", 49.00)), "paid", "2024-12-15"),
            createInvoice("ARV-2024-0158", "C003", "Cleveron AS", "2024-12-05", "2024-12-25",
                List.of(Map.of("desc", "Tarkvaraarendus - 40h", "qty", 40, "price", 95.00)), "unpaid", null),
            createInvoice("ARV-2024-0159", "C004", "Bolt Technology OÜ", "2024-12-10", "2024-12-30",
                List.of(Map.of("desc", "API integratsioon - 24h", "qty", 24, "price", 85.00),
                        Map.of("desc", "Dokumentatsioon", "qty", 1, "price", 450.00)), "unpaid", null),
            createInvoice("ARV-2024-0160", "C005", "Veriff OÜ", "2024-11-15", "2024-12-05",
                List.of(Map.of("desc", "PilveCRM Enterprise - november 2024", "qty", 1, "price", 249.00)), "overdue", null),
            createInvoice("ARV-2024-0161", "C006", "Starship Technologies OÜ", "2024-12-15", "2025-01-04",
                List.of(Map.of("desc", "Haldusteenus Basic - detsember 2024", "qty", 1, "price", 299.00),
                        Map.of("desc", "Lisatöötunnid tugi - 8h", "qty", 8, "price", 75.00)), "unpaid", null),
            createInvoice("ARV-2024-0162", "C007", "Skeleton Technologies OÜ", "2024-12-18", "2025-01-07",
                List.of(Map.of("desc", "Tarkvaraarendus - 16h", "qty", 16, "price", 95.00)), "unpaid", null),
            createInvoice("ARV-2024-0153", "C001", "Eesti Energia AS", "2024-11-01", "2024-11-21",
                List.of(Map.of("desc", "PilveCRM Enterprise - november 2024", "qty", 1, "price", 249.00)), "paid", "2024-11-19"),
            createInvoice("ARV-2024-0154", "C003", "Cleveron AS", "2024-11-10", "2024-11-30",
                List.of(Map.of("desc", "Tarkvaraarendus - 32h", "qty", 32, "price", 95.00)), "paid", "2024-11-28"),
            createInvoice("ARV-2024-0155", "C002", "Tallinna Sadam AS", "2024-11-01", "2024-11-21",
                List.of(Map.of("desc", "Haldusteenus Premium - november 2024", "qty", 1, "price", 799.00)), "paid", "2024-11-18")
        );

        List<Map<String, Object>> filtered = allInvoices.stream()
            .filter(inv -> "all".equals(status) || status.equals(inv.get("status")))
            .limit(limit)
            .toList();

        double totalAmount = filtered.stream().mapToDouble(i -> (Double) i.get("totalWithVat")).sum();
        double unpaidAmount = filtered.stream()
            .filter(i -> !"paid".equals(i.get("status")))
            .mapToDouble(i -> (Double) i.get("totalWithVat"))
            .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("invoices", filtered);
        result.put("summary", Map.of(
            "count", filtered.size(),
            "totalAmount", Math.round(totalAmount * 100) / 100.0,
            "unpaidAmount", Math.round(unpaidAmount * 100) / 100.0,
            "currency", "EUR"
        ));
        return result;
    }

    private Map<String, Object> createInvoice(String number, String clientId, String clientName,
            String invoiceDate, String dueDate, List<Map<String, Object>> lines, String status, String paidDate) {
        double subtotal = lines.stream()
            .mapToDouble(l -> ((Number)l.get("qty")).doubleValue() * ((Number)l.get("price")).doubleValue())
            .sum();
        double vat = subtotal * 0.22;
        double total = subtotal + vat;

        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("number", number);
        invoice.put("clientId", clientId);
        invoice.put("clientName", clientName);
        invoice.put("invoiceDate", invoiceDate);
        invoice.put("dueDate", dueDate);
        invoice.put("lines", lines.stream().map(l -> {
            Map<String, Object> line = new LinkedHashMap<>(l);
            line.put("total", ((Number)l.get("qty")).doubleValue() * ((Number)l.get("price")).doubleValue());
            return line;
        }).toList());
        invoice.put("subtotal", Math.round(subtotal * 100) / 100.0);
        invoice.put("vatRate", "22%");
        invoice.put("vatAmount", Math.round(vat * 100) / 100.0);
        invoice.put("totalWithVat", Math.round(total * 100) / 100.0);
        invoice.put("currency", "EUR");
        invoice.put("status", status);
        if (paidDate != null) {
            invoice.put("paidDate", paidDate);
        }
        return invoice;
    }

    private Object mockGetBalance(Map<String, Object> args) {
        Map<String, Object> accounts = new LinkedHashMap<>();

        accounts.put("arvelduskonto", Map.of(
            "name", "Swedbank arvelduskonto",
            "iban", "EE382200221234567890",
            "balance", 47823.45,
            "currency", "EUR"
        ));
        accounts.put("reserv", Map.of(
            "name", "LHV reservkonto",
            "iban", "EE867700771234567891",
            "balance", 85000.00,
            "currency", "EUR"
        ));
        accounts.put("nouded", Map.of(
            "name", "Nõuded ostjate vastu",
            "balance", 12847.60,
            "details", "5 maksmata arvet",
            "currency", "EUR"
        ));
        accounts.put("volad", Map.of(
            "name", "Võlad hankijatele",
            "balance", -4230.00,
            "details", "AWS, Google Cloud, kontor",
            "currency", "EUR"
        ));
        accounts.put("maksuvold", Map.of(
            "name", "Maksuvõlad",
            "balance", -8945.20,
            "details", "Detsembri palgamaksud",
            "currency", "EUR"
        ));

        String account = (String) args.get("account");
        if (account != null && accounts.containsKey(account)) {
            return accounts.get(account);
        }

        double assets = 47823.45 + 85000.00 + 12847.60;
        double liabilities = 4230.00 + 8945.20;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("accounts", accounts);
        result.put("summary", Map.of(
            "totalAssets", Math.round(assets * 100) / 100.0,
            "totalLiabilities", Math.round(liabilities * 100) / 100.0,
            "netPosition", Math.round((assets - liabilities) * 100) / 100.0,
            "currency", "EUR",
            "asOf", LocalDate.now().toString()
        ));
        return result;
    }

    private Object mockGetReport(Map<String, Object> args) {
        String type = (String) args.getOrDefault("type", "profit_loss");
        String period = (String) args.getOrDefault("period", "month");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("period", period);
        result.put("generatedAt", LocalDate.now().toString());

        return switch (type) {
            case "profit_loss" -> {
                result.put("reportType", "Kasumiaruanne");
                result.put("periodStart", "2024-12-01");
                result.put("periodEnd", "2024-12-31");
                result.put("revenue", Map.of(
                    "saasSubscriptions", 18456.00,
                    "developmentServices", 28540.00,
                    "supportContracts", 9876.00,
                    "total", 56872.00
                ));
                result.put("expenses", Map.of(
                    "salariesGross", 44600.00,
                    "socialTax33", 14718.00,
                    "officeRent", 2800.00,
                    "cloudServices", 3420.00,
                    "marketing", 1850.00,
                    "other", 890.00,
                    "total", 68278.00
                ));
                result.put("operatingProfit", -11406.00);
                result.put("note", "Detsember on tavaliselt nõrgem kuu - pühad ja vähem töötunde");
                yield result;
            }
            case "balance_sheet" -> {
                result.put("reportType", "Bilanss");
                result.put("asOf", "2024-12-31");
                result.put("assets", Map.of(
                    "currentAssets", Map.of(
                        "cash", 132823.45,
                        "receivables", 12847.60,
                        "prepaidExpenses", 4200.00,
                        "total", 149871.05
                    ),
                    "fixedAssets", Map.of(
                        "equipment", 24500.00,
                        "depreciation", -8700.00,
                        "total", 15800.00
                    ),
                    "totalAssets", 165671.05
                ));
                result.put("liabilities", Map.of(
                    "shortTerm", Map.of(
                        "payables", 4230.00,
                        "taxLiabilities", 8945.20,
                        "deferredRevenue", 12400.00,
                        "total", 25575.20
                    ),
                    "totalLiabilities", 25575.20
                ));
                result.put("equity", Map.of(
                    "shareCapital", 2500.00,
                    "retainedEarnings", 137595.85,
                    "totalEquity", 140095.85
                ));
                yield result;
            }
            case "cashflow" -> {
                result.put("reportType", "Rahavoogude aruanne");
                result.put("periodStart", "2024-12-01");
                result.put("periodEnd", "2024-12-31");
                result.put("operating", Map.of(
                    "collectionsFromClients", 62340.00,
                    "paymentsToSuppliers", -7120.00,
                    "salaryPayments", -52480.00,
                    "taxPayments", -14200.00,
                    "netOperating", -11460.00
                ));
                result.put("investing", Map.of(
                    "equipmentPurchase", -2400.00,
                    "netInvesting", -2400.00
                ));
                result.put("financing", Map.of(
                    "netFinancing", 0.00
                ));
                result.put("netCashFlow", -13860.00);
                result.put("openingBalance", 146683.45);
                result.put("closingBalance", 132823.45);
                yield result;
            }
            default -> new ErrorResult("Tundmatu aruande tüüp: " + type, "INVALID");
        };
    }

    private Object mockGetProducts(Map<String, Object> args) {
        String category = (String) args.get("category");
        boolean lowStockOnly = Boolean.TRUE.equals(args.get("lowStock"));

        // For IT company - licenses and service capacity
        List<Map<String, Object>> inventory = List.of(
            Map.of("code", "LIC-MS365", "name", "Microsoft 365 Business litsentsid", "category", "Litsentsid",
                   "available", 5, "reserved", 47, "minStock", 10, "unitCost", 12.50),
            Map.of("code", "LIC-ADOBE", "name", "Adobe Creative Cloud litsentsid", "category", "Litsentsid",
                   "available", 2, "reserved", 8, "minStock", 3, "unitCost", 54.99),
            Map.of("code", "LIC-JIRA", "name", "Jira Software litsentsid", "category", "Litsentsid",
                   "available", 15, "reserved", 35, "minStock", 5, "unitCost", 7.75),
            Map.of("code", "HW-LAPTOP", "name", "ThinkPad X1 Carbon (laos)", "category", "Riistvara",
                   "available", 2, "reserved", 0, "minStock", 3, "unitCost", 1450.00),
            Map.of("code", "HW-MONITOR", "name", "Dell 27\" monitorid", "category", "Riistvara",
                   "available", 4, "reserved", 0, "minStock", 2, "unitCost", 420.00),
            Map.of("code", "SRV-AWS", "name", "AWS reserveeritud võimsus", "category", "Pilveteenus",
                   "available", 80, "reserved", 65, "minStock", 20, "unitCost", 0.00),
            Map.of("code", "SRV-DEV", "name", "Arendaja töötunnid (kuu)", "category", "Ressurss",
                   "available", 120, "reserved", 340, "minStock", 100, "unitCost", 0.00)
        );

        List<Map<String, Object>> filtered = inventory.stream()
            .filter(p -> category == null || ((String)p.get("category")).toLowerCase().contains(category.toLowerCase()))
            .filter(p -> !lowStockOnly || (Integer) p.get("available") < (Integer) p.get("minStock"))
            .map(p -> {
                Map<String, Object> copy = new LinkedHashMap<>(p);
                copy.put("lowStock", (Integer) p.get("available") < (Integer) p.get("minStock"));
                return copy;
            })
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("inventory", filtered);
        result.put("summary", Map.of(
            "totalItems", filtered.size(),
            "lowStockItems", filtered.stream().filter(p -> (Boolean) p.get("lowStock")).count(),
            "note", "Litsentside 'reserved' näitab aktiivseid tellimusi klientidelt"
        ));
        return result;
    }

    private Object mockGetOrders(Map<String, Object> args) {
        String status = (String) args.getOrDefault("status", "all");

        // Service orders / projects
        List<Map<String, Object>> orders = List.of(
            Map.of("id", "PRJ-2024-089", "client", "Cleveron AS", "project", "Laohaldustarkvara API",
                   "type", "Arendus", "status", "progress", "startDate", "2024-11-15", "deadline", "2025-01-31",
                   "budget", 15200.00, "spent", 7600.00, "completion", "50%"),
            Map.of("id", "PRJ-2024-092", "client", "Bolt Technology OÜ", "project", "Maksesüsteemi integratsioon",
                   "type", "Integratsioon", "status", "progress", "startDate", "2024-12-01", "deadline", "2025-01-15",
                   "budget", 8500.00, "spent", 4080.00, "completion", "48%"),
            Map.of("id", "PRJ-2024-094", "client", "Skeleton Technologies OÜ", "project", "CRM juurutamine",
                   "type", "Juurutus", "status", "pending", "startDate", "2025-01-06", "deadline", "2025-02-28",
                   "budget", 12000.00, "spent", 0.00, "completion", "0%"),
            Map.of("id", "PRJ-2024-088", "client", "Eesti Energia AS", "project", "Raportimoodul",
                   "type", "Arendus", "status", "delivered", "startDate", "2024-10-01", "deadline", "2024-12-15",
                   "budget", 22400.00, "spent", 21850.00, "completion", "100%"),
            Map.of("id", "PRJ-2024-085", "client", "Tallinna Sadam AS", "project", "Infrastruktuuri audit",
                   "type", "Konsultatsioon", "status", "delivered", "startDate", "2024-09-15", "deadline", "2024-10-31",
                   "budget", 4800.00, "spent", 4800.00, "completion", "100%"),
            Map.of("id", "SUP-2024-DEC", "client", "Mitmed kliendid", "project", "Detsembri tugilepingud",
                   "type", "Tugi", "status", "progress", "startDate", "2024-12-01", "deadline", "2024-12-31",
                   "budget", 9800.00, "spent", 7350.00, "completion", "75%")
        );

        String mappedStatus = switch (status) {
            case "pending" -> "pending";
            case "shipped" -> "progress";
            case "delivered" -> "delivered";
            default -> "all";
        };

        List<Map<String, Object>> filtered = orders.stream()
            .filter(o -> "all".equals(mappedStatus) || mappedStatus.equals(o.get("status")))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("projects", filtered);
        result.put("summary", Map.of(
            "total", filtered.size(),
            "inProgress", filtered.stream().filter(o -> "progress".equals(o.get("status"))).count(),
            "pending", filtered.stream().filter(o -> "pending".equals(o.get("status"))).count(),
            "delivered", filtered.stream().filter(o -> "delivered".equals(o.get("status"))).count(),
            "totalBudget", filtered.stream().mapToDouble(o -> (Double) o.get("budget")).sum(),
            "totalSpent", filtered.stream().mapToDouble(o -> (Double) o.get("spent")).sum()
        ));
        return result;
    }

    private Object mockGetEmployees(Map<String, Object> args) {
        String department = (String) args.get("department");
        boolean activeOnly = args.get("active") == null || Boolean.TRUE.equals(args.get("active"));

        List<Map<String, Object>> filtered = EMPLOYEES.stream()
            .filter(e -> department == null || ((String)e.get("department")).equalsIgnoreCase(department))
            .filter(e -> !activeOnly || Boolean.TRUE.equals(e.get("active")))
            .toList();

        List<String> departments = EMPLOYEES.stream()
            .map(e -> (String) e.get("department"))
            .distinct()
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("employees", filtered);
        result.put("summary", Map.of(
            "totalEmployees", filtered.size(),
            "departments", departments,
            "avgSalary", Math.round(filtered.stream()
                .mapToInt(e -> (Integer) e.get("grossSalary"))
                .average()
                .orElse(0) * 100) / 100.0
        ));
        return result;
    }

    private Object mockGetPayroll(Map<String, Object> args) {
        String month = (String) args.getOrDefault("month", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));

        // Calculate real payroll from employee data
        double totalGross = EMPLOYEES.stream()
            .filter(e -> Boolean.TRUE.equals(e.get("active")))
            .mapToInt(e -> (Integer) e.get("grossSalary"))
            .sum();

        // Estonian tax rates 2024
        double socialTax = totalGross * 0.33;           // Sotsiaalmaks 33%
        double unemploymentEmployer = totalGross * 0.008; // Töötuskindlustus tööandja 0.8%
        double unemploymentEmployee = totalGross * 0.016; // Töötuskindlustus töötaja 1.6%
        double pensionII = totalGross * 0.02;           // II sammas 2%
        double incomeTax = (totalGross - 654 * 12) * 0.20 / 12; // Tulumaks ~20% (lihtsustatud)
        double netSalaries = totalGross - unemploymentEmployee - pensionII - incomeTax;
        double employerCost = totalGross + socialTax + unemploymentEmployer;

        // By department
        Map<String, Map<String, Object>> byDept = new LinkedHashMap<>();
        EMPLOYEES.stream()
            .filter(e -> Boolean.TRUE.equals(e.get("active")))
            .forEach(e -> {
                String dept = (String) e.get("department");
                byDept.computeIfAbsent(dept, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("employees", 0);
                    m.put("grossTotal", 0.0);
                    return m;
                });
                Map<String, Object> d = byDept.get(dept);
                d.put("employees", (Integer) d.get("employees") + 1);
                d.put("grossTotal", (Double) d.get("grossTotal") + (Integer) e.get("grossSalary"));
            });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("company", COMPANY_INFO);
        result.put("month", month);
        result.put("paymentDate", month + "-10");
        result.put("summary", Map.of(
            "employeeCount", EMPLOYEES.stream().filter(e -> Boolean.TRUE.equals(e.get("active"))).count(),
            "grossSalaries", totalGross,
            "socialTax33", Math.round(socialTax * 100) / 100.0,
            "unemploymentInsuranceEmployer08", Math.round(unemploymentEmployer * 100) / 100.0,
            "unemploymentInsuranceEmployee16", Math.round(unemploymentEmployee * 100) / 100.0,
            "pensionII", Math.round(pensionII * 100) / 100.0,
            "incomeTax20", Math.round(incomeTax * 100) / 100.0,
            "netSalaries", Math.round(netSalaries * 100) / 100.0,
            "totalEmployerCost", Math.round(employerCost * 100) / 100.0
        ));
        result.put("byDepartment", byDept);
        result.put("currency", "EUR");
        result.put("note", "Maksumäärad: sotsiaalmaks 33%, töötuskindlustus 0.8%+1.6%, tulumaks 20%");
        return result;
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
