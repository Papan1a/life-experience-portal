package com.lep.portal.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lep.portal.invite.InviteService;

class UserServiceTest {

    private UserRepository userRepository;
    private InviteService inviteService;
    private Argon2PasswordEncoder passwordEncoder;
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        inviteService = mock(InviteService.class);
        passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        userService = new UserService(userRepository, inviteService, passwordEncoder);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setPasswordHash(passwordEncoder.encode("oldPassword"));
    }

    @Test
    void changePasswordShouldSucceedWithValidInput() {
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.changePassword(userId, "oldPassword", "newPassword123");

        assertThat(passwordEncoder.matches("newPassword123", user.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordShouldFailWhenCurrentPasswordIsWrong() {
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                userService.changePassword(userId, "wrongPassword", "newPassword123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Неверный текущий пароль");
    }

    @Test
    void changePasswordShouldFailWhenNewPasswordEqualsCurrent() {
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                userService.changePassword(userId, "oldPassword", "oldPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Новый пароль должен отличаться от текущего");
    }

    @Test
    void changeEmailShouldFailWhenEmailIsTakenByAnotherUser() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setEmail("other@example.com");

        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() ->
                userService.changeEmail(userId, "other@example.com", "oldPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Этот email уже занят");
    }

    @Test
    void changeEmailShouldSucceedWhenUserKeepsOwnEmail() {
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.changeEmail(userId, "test@example.com", "oldPassword");

        assertThat(user.getEmail()).isEqualTo("test@example.com");
    }
}