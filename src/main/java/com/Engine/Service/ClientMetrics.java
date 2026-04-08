package com.Engine.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.LongAdder;


public class ClientMetrics {

    private final LongAdder totRequest=new LongAdder();
    private final LongAdder blockedRequest=new LongAdder();
    private final LongAdder passedRequest=new LongAdder();

    public void recordSuccess(){
        totRequest.increment();
        passedRequest.increment();
    }

    public void recordFailure(){
        totRequest.increment();;
        blockedRequest.increment();
    }

    public long getBlockedRequest() {
        return blockedRequest.sum();
    }

    public long getPassedRequest() {
        return passedRequest.sum();
    }

    public long getTotRequest() {
        return totRequest.sum();
    }
}
