package com.example.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for MCP connectors and server.
 */
@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpConfig {

    private ServerConfig server = new ServerConfig();
    private FileSystemConfig filesystem = new FileSystemConfig();
    private HttpFetchConfig httpFetch = new HttpFetchConfig();
    private PostgresConfig postgres = new PostgresConfig();

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public FileSystemConfig getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(FileSystemConfig filesystem) {
        this.filesystem = filesystem;
    }

    public HttpFetchConfig getHttpFetch() {
        return httpFetch;
    }

    public void setHttpFetch(HttpFetchConfig httpFetch) {
        this.httpFetch = httpFetch;
    }

    public PostgresConfig getPostgres() {
        return postgres;
    }

    public void setPostgres(PostgresConfig postgres) {
        this.postgres = postgres;
    }

    public static class ServerConfig {
        private String name = "mcp-gateway";
        private String version = "1.0.0";
        private boolean stdioEnabled = true;
        private boolean httpEnabled = true;
        private int httpPort = 8080;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public boolean isStdioEnabled() {
            return stdioEnabled;
        }

        public void setStdioEnabled(boolean stdioEnabled) {
            this.stdioEnabled = stdioEnabled;
        }

        public boolean isHttpEnabled() {
            return httpEnabled;
        }

        public void setHttpEnabled(boolean httpEnabled) {
            this.httpEnabled = httpEnabled;
        }

        public int getHttpPort() {
            return httpPort;
        }

        public void setHttpPort(int httpPort) {
            this.httpPort = httpPort;
        }
    }

    public static class FileSystemConfig {
        private boolean enabled = true;
        private List<String> allowedPaths;
        private long maxFileSizeBytes = 10485760; // 10MB

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }
    }

    public static class HttpFetchConfig {
        private boolean enabled = true;
        private List<String> allowedDomains;
        private long maxResponseSizeBytes = 5242880; // 5MB
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowedDomains() {
            return allowedDomains;
        }

        public void setAllowedDomains(List<String> allowedDomains) {
            this.allowedDomains = allowedDomains;
        }

        public long getMaxResponseSizeBytes() {
            return maxResponseSizeBytes;
        }

        public void setMaxResponseSizeBytes(long maxResponseSizeBytes) {
            this.maxResponseSizeBytes = maxResponseSizeBytes;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class PostgresConfig {
        private boolean enabled = false;
        private String url;
        private String username;
        private String password;
        private int maxQueryRows = 1000;
        private int queryTimeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getMaxQueryRows() {
            return maxQueryRows;
        }

        public void setMaxQueryRows(int maxQueryRows) {
            this.maxQueryRows = maxQueryRows;
        }

        public int getQueryTimeoutSeconds() {
            return queryTimeoutSeconds;
        }

        public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
            this.queryTimeoutSeconds = queryTimeoutSeconds;
        }
    }
}
