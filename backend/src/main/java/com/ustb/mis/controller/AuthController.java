package com.ustb.mis.controller;

import com.ustb.mis.common.Result;
import com.ustb.mis.security.UserContext;
import com.ustb.mis.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.ok(authService.login(body.get("userName"), body.get("password")));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.profile(UserContext.require()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
