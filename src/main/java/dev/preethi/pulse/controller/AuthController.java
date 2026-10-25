package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.LoginRequest;
import dev.preethi.pulse.dto.LoginResponse;
import dev.preethi.pulse.dto.RegisterRequest;
import dev.preethi.pulse.dto.UserResponse;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User registeredUser = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User authenticatedUser = userService.authenticate(request);
        String token = jwtService.generateToken(authenticatedUser);
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", UserResponse.from(authenticatedUser)));
    }
}
