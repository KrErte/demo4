package com.example.mcp.connectors;

import com.example.mcp.config.McpConfig;
import com.example.mcp.core.McpException;
import com.example.mcp.core.ToolRegistry;
import com.example.mcp.model.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Connector for read-only PostgreSQL database access.
 * Enforces SELECT-only queries and blocks dangerous SQL keywords.
 */
@Component
public class PostgresReadOnlyConnector {

    private static final Logger logger = LoggerFactory.getLogger(PostgresReadOnlyConnector.class);

    // Dangerous SQL keywords that are blocked
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "TRUNCATE", "GRANT", "REVOKE", "EXECUTE", "CALL",
        "COPY", "LOAD", "IMPORT", "EXPORT"
    );

    // Pattern to detect dangerous keywords (case-insensitive, word boundaries)
    private static final Pattern BLOCKED_PATTERN = Pattern.compile(
        "\\b(" + String.join("|", BLOCKED_KEYWORDS) + ")\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to ensure query starts with SELECT or WITH (for CTEs)
    private static final Pattern VALID_QUERY_PATTERN = Pattern.compile(
        "^\\s*(SELECT|WITH)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final McpConfig config;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private Connection connection;

    public PostgresReadOnlyConnector(McpConfig config, ToolRegistry toolRegistry) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void registerTools() {
        if (!config.getPostgres().isEnabled()) {
            logger.info("PostgreSQL connector is disabled");
            return;
        }

        initializeConnection();
        toolRegistry.register(createQueryTool());
        toolRegistry.register(createSchemaTool());
        logger.info("PostgreSQL connector registered tools: db.query, db.schema");
    }

    @PreDestroy
    public void cleanup() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("PostgreSQL connection closed");
            } catch (SQLException e) {
                logger.warn("Error closing PostgreSQL connection", e);
            }
        }
    }

    private void initializeConnection() {
        try {
            McpConfig.PostgresConfig pgConfig = config.getPostgres();
            connection = DriverManager.getConnection(
                pgConfig.getUrl(),
                pgConfig.getUsername(),
                pgConfig.getPassword()
            );
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            logger.info("PostgreSQL read-only connection established");
        } catch (SQLException e) {
            logger.error("Failed to initialize PostgreSQL connection", e);
            throw new McpException("Database connection failed: " + e.getMessage(), "DB_CONNECTION_ERROR", e);
        }
    }

    private ToolDefinition createQueryTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode sqlProp = properties.putObject("sql");
        sqlProp.put("type", "string");
        sqlProp.put("description", "SELECT query to execute. Only SELECT statements are allowed.");

        schema.putArray("required").add("sql");

        return new ToolDefinition(
            "db.query",
            "Execute a read-only SQL query against the PostgreSQL database. Only SELECT statements are permitted.",
            schema,
            this::executeQuery
        );
    }

    private ToolDefinition createSchemaTool() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");

        return new ToolDefinition(
            "db.schema",
            "Get the database schema information including tables, columns, and types.",
            schema,
            this::getSchema
        );
    }

    private Object executeQuery(Map<String, Object> args) {
        String sql = (String) args.get("sql");
        if (sql == null || sql.isBlank()) {
            throw new McpException("SQL query is required", "INVALID_ARGUMENT");
        }

        validateSql(sql);
        ensureConnection();

        try {
            Statement stmt = connection.createStatement();
            stmt.setQueryTimeout(config.getPostgres().getQueryTimeoutSeconds());
            stmt.setMaxRows(config.getPostgres().getMaxQueryRows());

            ResultSet rs = stmt.executeQuery(sql);
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }

            rs.close();
            stmt.close();

            return Map.of(
                "columns", columns,
                "rows", rows,
                "rowCount", rows.size()
            );

        } catch (SQLException e) {
            // Rollback any partial transaction
            try {
                connection.rollback();
            } catch (SQLException ex) {
                logger.warn("Failed to rollback transaction", ex);
            }
            throw new McpException("Query execution failed: " + e.getMessage(), "QUERY_ERROR", e);
        }
    }

    private Object getSchema(Map<String, Object> args) {
        ensureConnection();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();

            // Get tables
            ResultSet tableRs = metaData.getTables(null, "public", "%", new String[]{"TABLE", "VIEW"});
            while (tableRs.next()) {
                String tableName = tableRs.getString("TABLE_NAME");
                String tableType = tableRs.getString("TABLE_TYPE");

                List<Map<String, Object>> columns = new ArrayList<>();
                ResultSet columnRs = metaData.getColumns(null, "public", tableName, "%");
                while (columnRs.next()) {
                    columns.add(Map.of(
                        "name", columnRs.getString("COLUMN_NAME"),
                        "type", columnRs.getString("TYPE_NAME"),
                        "nullable", columnRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        "size", columnRs.getInt("COLUMN_SIZE")
                    ));
                }
                columnRs.close();

                tables.add(Map.of(
                    "name", tableName,
                    "type", tableType,
                    "columns", columns
                ));
            }
            tableRs.close();

            return Map.of(
                "database", config.getPostgres().getUrl(),
                "tables", tables,
                "tableCount", tables.size()
            );

        } catch (SQLException e) {
            throw new McpException("Failed to retrieve schema: " + e.getMessage(), "SCHEMA_ERROR", e);
        }
    }

    private void validateSql(String sql) {
        // Check for dangerous keywords
        if (BLOCKED_PATTERN.matcher(sql).find()) {
            throw McpException.policyDenied("SQL contains blocked keywords. Only SELECT queries are allowed.");
        }

        // Ensure query starts with SELECT or WITH
        if (!VALID_QUERY_PATTERN.matcher(sql).find()) {
            throw McpException.policyDenied("Query must start with SELECT or WITH. Only read operations are allowed.");
        }

        // Check for multiple statements (basic check for semicolons not in strings)
        String stripped = sql.replaceAll("'[^']*'", ""); // Remove string literals
        stripped = stripped.replaceAll("\"[^\"]*\"", ""); // Remove quoted identifiers
        if (stripped.contains(";") && stripped.indexOf(";") < stripped.length() - 1) {
            throw McpException.policyDenied("Multiple SQL statements are not allowed.");
        }
    }

    private void ensureConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initializeConnection();
            }
        } catch (SQLException e) {
            initializeConnection();
        }
    }
}
