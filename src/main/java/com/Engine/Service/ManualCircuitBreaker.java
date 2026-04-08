package com.Engine.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j

public class ManualCircuitBreaker {

    public enum State{OPEN,CLOSED,HALF_OPEN};
    private final AtomicReference<State> state =new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failurecount =new AtomicInteger(0);
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private volatile Instant lastFailureTime;
    private final double failureThreshold;
    private  long recoveryTimeoutSeconds;
    private final int windowSize;
    private  volatile boolean[] window ;

    public ManualCircuitBreaker(double failureThreshold, long recoveryTimeoutSeconds,int windowSize) {
        this.failureThreshold = failureThreshold;
        this.recoveryTimeoutSeconds = recoveryTimeoutSeconds;
        this.windowSize=windowSize;
        this.window=new boolean[windowSize];
    }

    public boolean isAllowed(){
        if(state.get()==State.CLOSED){
            return true;
        }
        if(state.get()==State.OPEN){
            if(Instant.now().isAfter(lastFailureTime.plusSeconds(recoveryTimeoutSeconds))){
                log.warn("Entering half open state");
                if(state.compareAndSet(State.OPEN,State.HALF_OPEN)) {
                    log.warn("Circuit Breaker entering HALF_OPEN state. Allowing a single probe request.");
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public void recordSuccess(){
        failurecount.set(0);
        updateWindow(true);
        if(state.get()==State.HALF_OPEN){
            state.set(State.CLOSED);
            log.info("Circuit CLOSED: Service recovered.");
        }
    }

    public  void recordFailure(){
        lastFailureTime=Instant.now();
        updateWindow(false);
        if(state.get()==State.HALF_OPEN){
            state.set(State.OPEN);
            return;
        }
        failurecount.incrementAndGet();
        if (calculateFailurePercent() >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
            log.error("Circuit Breaker TRIPPED! State is now OPEN.");
        }
    }

    private synchronized void updateWindow(boolean cond){
        int index = currentIndex.getAndIncrement()%windowSize;
        window[index] = cond;
    }

    private synchronized  double calculateFailurePercent(){
        int cnt =0 ;
        for (boolean b : window){
            if(!b){
                cnt++;
            }
        }
        return (double) cnt/windowSize;
    }
}
