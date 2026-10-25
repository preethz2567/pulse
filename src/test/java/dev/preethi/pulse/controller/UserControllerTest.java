package dev.preethi.pulse.controller;

import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.JwtAuthenticationFilter;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.UserNotFoundException;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getCurrentUser_withValidToken_returns200AndUserResponse() throws Exception {
        String token = "valid.jwt.token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("alice@example.com");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        when(jwtService.extractAllClaims(token)).thenReturn(claims);

        User user = new User("alice@example.com", "hashedSecretPassword", "ROLE_USER");
        user.setId(42L);
        when(userService.getUserByEmail("alice@example.com")).thenReturn(user);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42L))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).getUserByEmail("alice@example.com");
    }

    @Test
    void getCurrentUser_withoutToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void getCurrentUser_withInvalidOrExpiredToken_returns401Unauthorized() throws Exception {
        String token = "expired.or.invalid.token";
        when(jwtService.extractAllClaims(token)).thenThrow(new JwtException("Token expired or invalid"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired JWT token"))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void getCurrentUser_whenUserNotFoundInDatabase_returns404NotFound() throws Exception {
        String token = "valid.jwt.token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("ghost@example.com");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        when(jwtService.extractAllClaims(token)).thenReturn(claims);

        when(userService.getUserByEmail("ghost@example.com"))
                .thenThrow(new UserNotFoundException("User not found: ghost@example.com"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found: ghost@example.com"))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService).getUserByEmail("ghost@example.com");
    }
}
