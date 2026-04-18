package com.Engine.Controller;

import com.Engine.DTO.RegisterDTO;
import com.Engine.Entity.User;
import com.Engine.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserRepo userRepo;

    @PostMapping("/register")
    public Mono<?> register(@RequestBody RegisterDTO registerDTO){
        log.warn("Controller hit");
        return userRepo.findByAuthifyerId(registerDTO.getAuthifyerId())
                .switchIfEmpty(
                        Mono.defer(() -> {
                            User u = User.builder()
                                    .authifyerId(registerDTO.getAuthifyerId())
                                    .email(registerDTO.getEmail())
                                    .build();
                            return userRepo.save(u);
                        })
                )
                .onErrorResume(DuplicateKeyException.class, e ->
                        userRepo.findByAuthifyerId(registerDTO.getAuthifyerId())
                );
    }

    @PostMapping("/login/{id}")
    public Mono<?> login(@PathVariable("id")String id){
        return Mono.justOrEmpty(userRepo.findByAuthifyerId(id));
    }

}
