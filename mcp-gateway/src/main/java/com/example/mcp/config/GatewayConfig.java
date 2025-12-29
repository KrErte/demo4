package com.example.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "mcp")
public class GatewayConfig {

    private Policy policy = new Policy();
    private Fs fs = new Fs();
    private Web web = new Web();
    private Features features = new Features();
    private Security security = new Security();

    public Policy getPolicy() { return policy; }
    public void setPolicy(Policy policy) { this.policy = policy; }
    public Fs getFs() { return fs; }
    public void setFs(Fs fs) { this.fs = fs; }
    public Web getWeb() { return web; }
    public void setWeb(Web web) { this.web = web; }
    public Features getFeatures() { return features; }
    public void setFeatures(Features features) { this.features = features; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public static class Policy {
        private boolean defaultDeny = true;
        private long timeoutMs = 30000;
        private long maxResultBytes = 1048576;

        public boolean isDefaultDeny() { return defaultDeny; }
        public void setDefaultDeny(boolean defaultDeny) { this.defaultDeny = defaultDeny; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public long getMaxResultBytes() { return maxResultBytes; }
        public void setMaxResultBytes(long maxResultBytes) { this.maxResultBytes = maxResultBytes; }
    }

    public static class Fs {
        private List<String> allowlist = new ArrayList<>();

        public List<String> getAllowlist() { return allowlist; }
        public void setAllowlist(List<String> allowlist) { this.allowlist = allowlist; }
    }

    public static class Web {
        private List<String> allowDomains = new ArrayList<>();

        public List<String> getAllowDomains() { return allowDomains; }
        public void setAllowDomains(List<String> allowDomains) { this.allowDomains = allowDomains; }
    }

    public static class Features {
        private boolean fsEnabled = true;
        private boolean httpEnabled = true;

        public boolean isFsEnabled() { return fsEnabled; }
        public void setFsEnabled(boolean fsEnabled) { this.fsEnabled = fsEnabled; }
        public boolean isHttpEnabled() { return httpEnabled; }
        public void setHttpEnabled(boolean httpEnabled) { this.httpEnabled = httpEnabled; }
    }

    public static class Security {
        private String apiKey = "dev-key";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
