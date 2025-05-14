package com.collabnotes.CollabNotes.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class MetricsService {
    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordOperation(String name, long timeInMs) {
        Timer timer = meterRegistry.timer("app.operation." + name);
        timer.record(timeInMs, TimeUnit.MILLISECONDS);
    }

    public <T> T recordOperation(String name, Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        try {
            return operation.get();
        } finally {
            recordOperation(name, System.currentTimeMillis() - startTime);
        }
    }

    public void incrementCounter(String name) {
        meterRegistry.counter("app.counter." + name).increment();
    }

    public void recordGauge(String name, double value) {
        Gauge.builder("app.gauge." + name, () -> value)
             .register(meterRegistry);
    }

    public void recordUserActivity(String noteId, int activeUsers) {
        Gauge.builder("app.notes.active_users", () -> activeUsers)
             .tag("noteId", noteId)
             .register(meterRegistry);
    }
}