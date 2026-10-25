package dev.preethi.pulse.config;

import dev.preethi.pulse.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withValidBearerToken_authenticatesUser() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user@example.com");
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        when(jwtService.extractAllClaims(token)).thenReturn(claims);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("user@example.com", auth.getName());
        assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withoutAuthHeader_doesNotAuthenticate() throws ServletException, IOException {
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withNonBearerHeader_doesNotAuthenticate() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withExpiredOrInvalidToken_clearsContextAndDoesNotAuthenticate() throws ServletException, IOException {
        String token = "invalid.or.expired.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.extractAllClaims(token)).thenThrow(new JwtException("Token expired or signature invalid"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("Invalid or expired JWT token", request.getAttribute("auth_error_message"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withMalformedToken_clearsContextAndDoesNotAuthenticate() throws ServletException, IOException {
        String token = "malformed.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtService.extractAllClaims(token)).thenThrow(new IllegalArgumentException("JWT string is malformed"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("Invalid or expired JWT token", request.getAttribute("auth_error_message"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withMissingEmailOrRoleInClaims_doesNotAuthenticate() throws ServletException, IOException {
        String token = "incomplete.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(null);
        when(claims.get("role", String.class)).thenReturn("ROLE_USER");
        when(jwtService.extractAllClaims(token)).thenReturn(claims);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("Invalid or expired JWT token", request.getAttribute("auth_error_message"));
        verify(filterChain).doFilter(request, response);
    }
}
