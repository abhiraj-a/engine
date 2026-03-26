package com.Engine.ControlPane.Service;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class TokenBucket {
    
    private final long capacity;
    private final long refillRate;
    private double tokens;
    private Instant lastRefillTimestamp;

    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.lastRefillTimestamp = Instant.now();
        this.tokens = tokens;
    }

    public synchronized boolean tryConsume(){

        if(tokens>=1.0){
            tokens-=1.0;
            return true;
        }
        return false;
    }

    private void refill(){
        Instant now =Instant.now();
        double elapsedTime = (now.toEpochMilli()-lastRefillTimestamp.toEpochMilli())/1000.0;
        tokens =Math.min(capacity, tokens+(refillRate*elapsedTime));
        lastRefillTimestamp =now;
    }
}
