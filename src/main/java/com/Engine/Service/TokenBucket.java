package com.Engine.Service;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Setter
@Getter
public class TokenBucket {

    private record BucketState(double tokens,Instant lastRefillTime){};

    private final long capacity;
    private final long refillRate;
    private AtomicReference<BucketState> state;

    public TokenBucket(long capacity, long refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.state  =new AtomicReference<>(new BucketState(capacity,Instant.now()));
    }



    public boolean tryConsume(){

        while(true){
            BucketState current = state.get();
            BucketState refilled = refill(current);

            if(refilled.tokens()>=1.0){
                BucketState consumed =new BucketState(refilled.tokens()-1.0,refilled.lastRefillTime());

                        if(state.compareAndSet(current,consumed)){
                            return true;
                        }
            }else {
                if(state.compareAndSet(current,refilled)){
                    return false;
                }
            }
        }
    }

    private BucketState refill(BucketState current){
        Instant now =Instant.now();
        double elapsedTime = (now.toEpochMilli()-current.lastRefillTime().toEpochMilli())/1000.0;
        double tokens =Math.min(capacity, current.tokens+(refillRate*elapsedTime));
        return new BucketState(tokens,now);
    }

    public double getTokens(){
        return state.get().tokens();
    }

    public  Instant getLastRefillTime(){
        return state.get().lastRefillTime();
    }


}
