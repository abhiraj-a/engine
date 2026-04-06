package com.Engine.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
public class ApiClientController {


    @PostMapping("/register-new")
    public ResponseEntity<?> registerNew(){

    }

}
