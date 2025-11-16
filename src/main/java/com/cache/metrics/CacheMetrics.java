package com.cache.metrics;

import com.cache.core.CacheStats;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Micrometer metrics integration for cache operations.
 */
@Component
public class CacheMetrics {
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> evictionCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> loadTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Gauge> sizeGauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Gauge> memoryGauges = new ConcurrentHashMap<>();

    public CacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordHit(String cacheName) {
        Counter counter = hitCounters.computeIfAbsent(cacheName, 
            name -> Counter.builder("customcache.hits")
                .tag("cache", name)
                .register(meterRegistry));
        counter.increment();
    }

    public void recordMiss(String cacheName) {
        Counter counter = missCounters.computeIfAbsent(cacheName,
            name -> Counter.builder("customcache.misses")
                .tag("cache", name)
                .register(meterRegistry));
        counter.increment();
    }

    public void recordEviction(String cacheName, String reason) {
        Counter counter = evictionCounters.computeIfAbsent(cacheName + ":" + reason,
            key -> Counter.builder("customcache.evictions")
                .tag("cache", cacheName)
                .tag("reason", reason)
                .register(meterRegistry));
        counter.increment();
    }

    public Timer.Sample startLoadTimer(String cacheName) {
        // Get timer to ensure it's registered, but use meterRegistry directly for sample
        getLoadTimer(cacheName);
        return Timer.start(meterRegistry);
    }

    public Timer getLoadTimer(String cacheName) {
        return loadTimers.computeIfAbsent(cacheName,
            name -> Timer.builder("customcache.load.time.ms")
                .tag("cache", name)
                .register(meterRegistry));
    }

    public void updateSizeGauge(String cacheName, long size) {
        sizeGauges.computeIfAbsent(cacheName, name -> 
            Gauge.builder("customcache.size.entries", () -> size)
                .tag("cache", name)
                .register(meterRegistry));
    }

    public void updateMemoryGauge(String cacheName, long memoryBytes) {
        memoryGauges.computeIfAbsent(cacheName, name ->
            Gauge.builder("customcache.memory.bytes", () -> memoryBytes)
                .tag("cache", name)
                .register(meterRegistry));
    }

    public void updateStats(String cacheName, CacheStats stats) {
        updateSizeGauge(cacheName, stats.getSize());
        updateMemoryGauge(cacheName, stats.getMemoryBytes());
    }
}

