package dev.preethi.pulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.preethi.pulse.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtAuthenticationEntryPointTest {

    private ObjectMapper objectMapper;
    private JwtAuthenticationEntryPoint entryPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void commence_withoutRequestAttribute_usesAuthExceptionMessage() throws IOException {
        request.setRequestURI("/api/protected-resource");
        InsufficientAuthenticationException authException =
                new InsufficientAuthenticationException("Full authentication is required to access this resource");

        entryPoint.commence(request, response, authException);

        assertEquals(401, response.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());

        ErrorResponse errorResponse = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertEquals(401, errorResponse.status());
        assertEquals("Unauthorized", errorResponse.error());
        assertEquals("Full authentication is required to access this resource", errorResponse.message());
        assertEquals("/api/protected-resource", errorResponse.path());
        assertNotNull(errorResponse.timestamp());
    }

    @Test
    void commence_withCustomAuthErrorMessage_usesCustomMessage() throws IOException {
        request.setRequestURI("/api/protected-resource");
        request.setAttribute("auth_error_message", "Invalid or expired JWT token");

        InsufficientAuthenticationException authException =
                new InsufficientAuthenticationException("Full authentication is required to access this resource");

        entryPoint.commence(request, response, authException);

        assertEquals(401, response.getStatus());
        ErrorResponse errorResponse = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertEquals(401, errorResponse.status());
        assertEquals("Unauthorized", errorResponse.error());
        assertEquals("Invalid or expired JWT token", errorResponse.message());
        assertEquals("/api/protected-resource", errorResponse.path());
        assertNotNull(errorResponse.timestamp());
    }

    @Test
    void commence_withNullAuthExceptionAndNoAttribute_usesDefaultMessage() throws IOException {
        request.setRequestURI("/api/some-endpoint");

        entryPoint.commence(request, response, null);

        assertEquals(401, response.getStatus());
        ErrorResponse errorResponse = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
        assertEquals(401, errorResponse.status());
        assertEquals("Unauthorized", errorResponse.error());
        assertEquals("Full authentication is required to access this resource", errorResponse.message());
        assertEquals("/api/some-endpoint", errorResponse.path());
        assertNotNull(errorResponse.timestamp());
    }
}
