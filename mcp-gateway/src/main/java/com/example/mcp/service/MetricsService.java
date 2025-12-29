package com.example.mcp.service;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.ActivityEntry;
import com.example.mcp.model.Metrics;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private static final int MAX_ACTIVITY = 100;

    private final GatewayConfig config;
    private final AtomicLong allowedCount = new AtomicLong(0);
    private final AtomicLong deniedCount = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final List<ActivityEntry> activity = Collections.synchronizedList(new ArrayList<>());

    public MetricsService(GatewayConfig config) {
        this.config = config;
    }

    public void recordAllowed(String tool, String reason, long elapsedMs) {
        allowedCount.incrementAndGet();
        totalRequests.incrementAndGet();
        addActivity(ActivityEntry.allowed(tool, reason, elapsedMs));
    }

    public void recordDenied(String tool, String reason) {
        deniedCount.incrementAndGet();
        totalRequests.incrementAndGet();
        addActivity(ActivityEntry.denied(tool, reason));
    }

    public void recordError(String tool, String reason, long elapsedMs) {
        totalRequests.incrementAndGet();
        addActivity(ActivityEntry.error(tool, reason, elapsedMs));
    }

    private void addActivity(ActivityEntry entry) {
        synchronized (activity) {
            activity.add(0, entry);
            while (activity.size() > MAX_ACTIVITY) {
                activity.remove(activity.size() - 1);
            }
        }
    }

    public List<ActivityEntry> getRecentActivity() {
        synchronized (activity) {
            return new ArrayList<>(activity);
        }
    }

    public Metrics getMetrics() {
        return new Metrics(
            3,
            allowedCount.get(),
            deniedCount.get(),
            totalRequests.get(),
            new Metrics.ConfigSummary(
                config.getPolicy().isDefaultDeny(),
                config.getPolicy().getTimeoutMs(),
                config.getPolicy().getMaxResultBytes(),
                config.getFeatures().isFsEnabled(),
                config.getFeatures().isHttpEnabled(),
                false
            )
        );
    }
}
