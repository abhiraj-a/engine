package com.Engine.Repository;

import com.Engine.Entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepo extends ReactiveCrudRepository<User, UUID>{
    Mono<User> findByAuthifyerId(String s);
}
