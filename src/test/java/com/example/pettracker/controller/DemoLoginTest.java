package com.example.pettracker.controller;

import com.example.pettracker.dto.AuthDTOs.LoginRequest;
import com.example.pettracker.entity.User;
import com.example.pettracker.security.JwtProvider;
import com.example.pettracker.service.LoginDemoDataService;
import com.example.pettracker.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DemoLoginTest {
    private final UserService users = mock(UserService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtProvider jwt = mock(JwtProvider.class);
    private final LoginDemoDataService demo = mock(LoginDemoDataService.class);
    private final AuthController controller = new AuthController(users, passwords, jwt, demo);

    @Test
    void seedsAfterPasswordVerificationAndBeforeReturningTheToken() {
        User owner = User.builder().id(42L).email("danico411@gmail.com").password("stored-hash").build();
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(passwords.matches("correct-password", "stored-hash")).thenReturn(true);
        when(jwt.generateToken(owner.getEmail())).thenReturn("token");

        assertThat(controller.login(request()).getToken()).isEqualTo("token");

        InOrder order = inOrder(passwords, demo, jwt);
        order.verify(passwords).matches("correct-password", "stored-hash");
        order.verify(demo).seedForLogin(owner);
        order.verify(jwt).generateToken(owner.getEmail());
    }

    @Test
    void invalidPasswordNeverSeedsData() {
        when(users.findByEmail("danico411@gmail.com")).thenReturn(Optional.of(
                User.builder().id(42L).email("danico411@gmail.com").password("stored-hash").build()));

        assertThatThrownBy(() -> controller.login(request())).hasMessage("Invalid credentials");

        verifyNoInteractions(demo, jwt);
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setEmail(" DANICO411@GMAIL.COM ");
        request.setPassword("correct-password");
        return request;
    }
}
