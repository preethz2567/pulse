package dev.preethi.pulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.preethi.pulse.config.JwtAuthenticationEntryPoint;
import dev.preethi.pulse.config.SecurityConfig;
import dev.preethi.pulse.dto.LoginRequest;
import dev.preethi.pulse.dto.RegisterRequest;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.DuplicateEmailException;
import dev.preethi.pulse.exception.GlobalExceptionHandler;
import dev.preethi.pulse.exception.InvalidCredentialsException;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    void register_withValidData_returns201CreatedAndUserSummary() throws Exception {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123");

        User createdUser = new User("alice@example.com", "hashedPassword", "ROLE_USER");
        createdUser.setId(1L);

        when(userService.register(any(RegisterRequest.class))).thenReturn(createdUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withDuplicateEmail_returns409ConflictWithStandardErrorResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicate@example.com", "password123");

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("Email is already registered: duplicate@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email is already registered: duplicate@example.com"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withInvalidEmail_returns400BadRequestWithStandardErrorResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService, never()).register(any());
    }

    @Test
    void register_withShortPassword_returns400BadRequestWithStandardErrorResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("bob@example.com", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService, never()).register(any());
    }

    @Test
    void login_withValidCredentials_returns200OkAndLoginResponseWithToken() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");

        User user = new User("alice@example.com", "hashedPassword", "ROLE_USER");
        user.setId(1L);

        when(userService.authenticate(any(LoginRequest.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("mocked.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.id").value(1L))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        verify(userService).authenticate(any(LoginRequest.class));
        verify(jwtService).generateToken(user);
    }

    @Test
    void login_withWrongPassword_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongPassword");

        when(userService.authenticate(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService).authenticate(any(LoginRequest.class));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_withUnknownEmail_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");

        when(userService.authenticate(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService).authenticate(any(LoginRequest.class));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_withInvalidRequestData_returns400BadRequestWithStandardErrorResponse() throws Exception {
        LoginRequest request = new LoginRequest("not-an-email", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService, never()).authenticate(any());
        verify(jwtService, never()).generateToken(any());
    }
}
