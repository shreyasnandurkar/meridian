package com.meridian.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meridian.auth.AuthDtos.AuthResponse;
import com.meridian.auth.AuthDtos.LoginRequest;
import com.meridian.auth.AuthDtos.SignupRequest;
import com.meridian.common.ApiException;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwtService);
        when(jwtService.issue(any())).thenReturn("token");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void signupNormalizesEmailAndHashesPassword() {
        AuthResponse res = service.signup(new SignupRequest("  Asha@Example.COM ", "password123", " Asha "));

        assertThat(res.token()).isEqualTo("token");
        assertThat(res.user().email()).isEqualTo("asha@example.com");
        assertThat(res.user().displayName()).isEqualTo("Asha");
        verify(users).save(any(User.class));
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(new SignupRequest("asha@example.com", "password123", "Asha")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User user = new User("asha@example.com", encoder.encode("password123"), "Asha");
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));

        AuthResponse res = service.login(new LoginRequest("Asha@example.com", "password123"));

        assertThat(res.user().id()).isEqualTo(user.getId());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("asha@example.com", encoder.encode("password123"), "Asha");
        when(users.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", "wrong-password")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginRejectsUnknownEmailWithSameError() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Invalid email or password"));
    }
}
