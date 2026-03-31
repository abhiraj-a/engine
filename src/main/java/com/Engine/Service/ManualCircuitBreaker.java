package com.Engine.Service;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ManualCircuitBreaker {

    public enum State{OPEN,CLOSED,HALF_OPEN};

    private final AtomicReference<State> state =new AtomicReference<>();
    private final AtomicInteger failurecount =new AtomicInteger(0);
    private volatile Instant lastFailureTime;
    private final int failureThreshold;
    private final long recoveryTimeoutSeconds;

    public ManualCircuitBreaker(int failureThreshold, long recoveryTimeoutSeconds) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeoutSeconds = recoveryTimeoutSeconds;
    }

    public boolean isAllowed(){
        if(state.get()==State.CLOSED){
            return true;
        }

        if(state.get()==State.OPEN){
            if(Instant.now().isAfter(lastFailureTime.plusSeconds(recoveryTimeoutSeconds))){
                log.warn("Entering half open state");
                state.set(State.HALF_OPEN);
                return true;
            }
            return false;
        }
        return state.get() == State.HALF_OPEN;
    }

    public void recordSuccess(){
        failurecount.set(0);
        if(state.get()==State.HALF_OPEN){
            state.set(State.CLOSED);
        }
    }

    public  void recordFailure(){
        lastFailureTime=Instant.now();
        if(state.get()==State.HALF_OPEN){
            state.set(State.OPEN);
            return;
        }
        int currentFailures = failurecount.incrementAndGet();
        if (currentFailures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            log.error("Circuit Breaker TRIPPED! State is now OPEN.");
        }
    }
}
