package com.collabnotes.collabnotes.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MetricsServiceTest {

    private MeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    @Test
    void recordOperation_recordsTimerWithCorrectName() {
        metricsService.recordOperation("test.op", 150L);

        assertEquals(1, meterRegistry.timer("app.operation.test.op").count());
    }

    @Test
    void recordOperation_withSupplier_returnsValueAndRecordsTimer() {
        String result = metricsService.recordOperation("test.supplier", () -> "hello");

        assertEquals("hello", result);
        assertEquals(1, meterRegistry.timer("app.operation.test.supplier").count());
    }

    @Test
    void recordOperation_withSupplier_whenSupplierThrows_stillRecordsTimer() {
        assertThrows(RuntimeException.class,
                () -> metricsService.recordOperation("test.error", () -> {
                    throw new RuntimeException("fail");
                }));

        assertEquals(1, meterRegistry.timer("app.operation.test.error").count());
    }

    @Test
    void incrementCounter_incrementsByOne() {
        metricsService.incrementCounter("test.counter");
        metricsService.incrementCounter("test.counter");

        assertEquals(2.0, meterRegistry.counter("app.counter.test.counter").count());
    }

    @Test
    void recordGauge_registersGaugeWithValue() {
        metricsService.recordGauge("test.gauge", 42.0);

        assertEquals(42.0, meterRegistry.get("app.gauge.test.gauge").gauge().value());
    }

    @Test
    void recordUserActivity_registersGaugeWithNoteIdTag() {
        metricsService.recordUserActivity("note-1", 5);

        assertEquals(5.0, meterRegistry.get("app.notes.active_users")
                .tag("noteId", "note-1")
                .gauge().value());
    }
}
