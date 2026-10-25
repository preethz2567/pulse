package dev.preethi.pulse.service;

import dev.preethi.pulse.dto.LoginRequest;
import dev.preethi.pulse.dto.RegisterRequest;
import dev.preethi.pulse.entity.User;
import dev.preethi.pulse.exception.DuplicateEmailException;
import dev.preethi.pulse.exception.InvalidCredentialsException;
import dev.preethi.pulse.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_withNewEmail_succeedsAndHashesPassword() {
        String email = "alice@example.com";
        String rawPassword = "securePassword123";
        String hashedPassword = "bcryptHashedPasswordValue";

        RegisterRequest request = new RegisterRequest(email, rawPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);

        User savedUserMock = new User(email, hashedPassword, "ROLE_USER");
        savedUserMock.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);

        User result = userService.register(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(email, result.getEmail());
        assertEquals("ROLE_USER", result.getRole());
        assertEquals(hashedPassword, result.getPasswordHash());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals(email, capturedUser.getEmail());
        assertEquals(hashedPassword, capturedUser.getPasswordHash());
        assertEquals("ROLE_USER", capturedUser.getRole());

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).encode(rawPassword);
    }

    @Test
    void register_withDuplicateEmail_throwsDuplicateEmailException() {
        String email = "duplicate@example.com";
        String rawPassword = "password123";
        RegisterRequest request = new RegisterRequest(email, rawPassword);

        User existingUser = new User(email, "existingHashedPassword", "ROLE_USER");
        existingUser.setId(42L);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> userService.register(request)
        );

        assertEquals("Email is already registered: " + email, exception.getMessage());

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_withValidCredentials_returnsUser() {
        String email = "alice@example.com";
        String rawPassword = "securePassword123";
        String storedHash = "bcryptHashedPasswordValue";
        LoginRequest request = new LoginRequest(email, rawPassword);

        User existingUser = new User(email, storedHash, "ROLE_USER");
        existingUser.setId(1L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(rawPassword, storedHash)).thenReturn(true);

        User authenticatedUser = userService.authenticate(request);

        assertNotNull(authenticatedUser);
        assertEquals(1L, authenticatedUser.getId());
        assertEquals(email, authenticatedUser.getEmail());
        assertEquals("ROLE_USER", authenticatedUser.getRole());
        assertEquals(storedHash, authenticatedUser.getPasswordHash());

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(rawPassword, storedHash);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_withUnknownEmail_throwsInvalidCredentialsException() {
        String email = "unknown@example.com";
        String rawPassword = "anyPassword";
        LoginRequest request = new LoginRequest(email, rawPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.authenticate(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticate_withIncorrectPassword_throwsInvalidCredentialsException() {
        String email = "alice@example.com";
        String wrongPassword = "wrongPassword";
        String storedHash = "bcryptHashedPasswordValue";
        LoginRequest request = new LoginRequest(email, wrongPassword);

        User existingUser = new User(email, storedHash, "ROLE_USER");
        existingUser.setId(1L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(wrongPassword, storedHash)).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> userService.authenticate(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(wrongPassword, storedHash);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserByEmail_withExistingEmail_returnsUser() {
        String email = "alice@example.com";
        User existingUser = new User(email, "storedHash", "ROLE_USER");
        existingUser.setId(10L);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        User result = userService.getUserByEmail(email);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(email, result.getEmail());
        assertEquals("ROLE_USER", result.getRole());
        verify(userRepository).findByEmail(email);
    }

    @Test
    void getUserByEmail_withNonExistingEmail_throwsUserNotFoundException() {
        String email = "missing@example.com";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        dev.preethi.pulse.exception.UserNotFoundException exception = assertThrows(
                dev.preethi.pulse.exception.UserNotFoundException.class,
                () -> userService.getUserByEmail(email)
        );

        assertEquals("User not found: " + email, exception.getMessage());
        verify(userRepository).findByEmail(email);
    }
}
