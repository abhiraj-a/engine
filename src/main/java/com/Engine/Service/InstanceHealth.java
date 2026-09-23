package com.Engine.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the health state of a single backend instance.
 * Updated passively based on upstream response outcomes.
 */
public class InstanceHealth {

    public enum HealthState { HEALTHY, DEGRADED, UNHEALTHY }

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<HealthState> state = new AtomicReference<>(HealthState.HEALTHY);
    private volatile Instant lastFailureTime;

    // Thresholds
    private static final int DEGRADED_THRESHOLD = 3;
    private static final int UNHEALTHY_THRESHOLD = 5;
    private static final long RECOVERY_COOLDOWN_SECONDS = 30;

    public void incrementConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementConnections() {
        activeConnections.decrementAndGet();
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(HealthState.HEALTHY);
    }

    public void recordFailure() {
        lastFailureTime = Instant.now();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= UNHEALTHY_THRESHOLD) {
            state.set(HealthState.UNHEALTHY);
        } else if (failures >= DEGRADED_THRESHOLD) {
            state.set(HealthState.DEGRADED);
        }
    }

    /**
     * Whether this instance should be considered for routing.
     * Unhealthy instances are excluded unless enough cooldown time has passed
     * (passive recovery — gives them a chance to be re-probed).
     */
    public boolean isRoutable() {
        HealthState current = state.get();
        if (current != HealthState.UNHEALTHY) {
            return true;
        }
        // Allow re-probe after cooldown
        if (lastFailureTime != null &&
                Instant.now().isAfter(lastFailureTime.plusSeconds(RECOVERY_COOLDOWN_SECONDS))) {
            state.compareAndSet(HealthState.UNHEALTHY, HealthState.DEGRADED);
            return true;
        }
        return false;
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public HealthState getState() {
        return state.get();
    }

    public Instant getLastFailureTime() {
        return lastFailureTime;
    }
}
