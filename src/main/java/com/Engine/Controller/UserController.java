package com.Engine.Controller;

import com.Engine.DTO.RegisterDTO;
import com.Engine.Entity.User;
import com.Engine.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserRepo userRepo;

    @PostMapping("/register")
    public Mono<?> register(@RequestBody RegisterDTO registerDTO){
        User u = User.builder()
                .authifyerId(registerDTO.getAuthifyerId())
                .email(registerDTO.getEmail())
                .build();
        return Mono.just(userRepo.save(u));
    }

    @PostMapping("/login/{id}")
    public Mono<?> login(@PathVariable("id")String id){
        return Mono.justOrEmpty(userRepo.findByAuthifyerId(id));
    }


}
