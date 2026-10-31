package dev.preethi.pulse.config;

import dev.preethi.pulse.controller.AuthController;
import dev.preethi.pulse.service.JwtService;
import dev.preethi.pulse.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserService userService;

    @Test
    void protectedEndpoint_withoutToken_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        mockMvc.perform(get("/api/monitors"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/api/monitors"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        when(jwtService.extractAllClaims("invalid.token"))
                .thenThrow(new JwtException("Signature invalid"));

        mockMvc.perform(get("/api/monitors")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired JWT token"))
                .andExpect(jsonPath("$.path").value("/api/monitors"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        when(jwtService.extractAllClaims("expired.token"))
                .thenThrow(new JwtException("Token expired"));

        mockMvc.perform(get("/api/monitors")
                        .header("Authorization", "Bearer expired.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired JWT token"))
                .andExpect(jsonPath("$.path").value("/api/monitors"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returns401UnauthorizedWithStandardErrorResponse() throws Exception {
        when(jwtService.extractAllClaims("malformed.token"))
                .thenThrow(new IllegalArgumentException("Malformed JWT"));

        mockMvc.perform(get("/api/monitors")
                        .header("Authorization", "Bearer malformed.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid or expired JWT token"))
                .andExpect(jsonPath("$.path").value("/api/monitors"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void publicEndpoints_remainPubliclyAccessible() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actuatorHealthAndInfo_permittedWithoutAuthentication() throws Exception {
        // PermitAll in SecurityConfig allows requests to pass the security filter chain to DispatcherServlet
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("No static resource actuator/health."));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("No static resource actuator/info."));
    }

    @Test
    void sensitiveActuatorEndpoints_requireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Full authentication is required to access this resource"))
                .andExpect(jsonPath("$.path").value("/actuator/env"));

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/actuator/beans"));

        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/actuator/configprops"));
    }
}
