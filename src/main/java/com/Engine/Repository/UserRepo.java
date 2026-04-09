package com.Engine.Repository;

import com.Engine.Entity.User;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepo extends ReactiveCrudRepository<User,String>{
    Mono<User> findByAuthifyerId(String s);
}
