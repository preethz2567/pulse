package dev.preethi.pulse.controller;

import dev.preethi.pulse.dto.UserResponse;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
