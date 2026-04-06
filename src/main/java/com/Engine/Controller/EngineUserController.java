package com.Engine.Controller;


import com.Engine.DTO.RegisterDTO;
import com.Engine.Entity.User;
import com.Engine.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class EngineUserController {


    private final UserRepo userRepo;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO registerDTO){
        User user  = User.builder()
                .authifyerId(registerDTO.getAuthifyerId())
                .email(registerDTO.getEmail())
                .name(registerDTO.getName())
                .createdAt(Instant.now())
                .build();
        return ResponseEntity.ok(userRepo.save(user));
    }

    @GetMapping
}
