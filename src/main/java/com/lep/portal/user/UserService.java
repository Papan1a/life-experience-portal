package com.lep.portal.user;

import com.lep.portal.common.NotFoundException;
import com.lep.portal.invite.Invite;
import com.lep.portal.invite.InviteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       InviteRepository inviteRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegistrationForm form) {
        // Check email uniqueness for non-deleted users
        if (userRepository.emailExists(form.getEmail())) {
            throw new IllegalArgumentException("Email уже используется");
        }

        // Validate invite code
        Invite invite = inviteRepository.findByCode(form.getInviteCode())
                .orElseThrow(() -> new IllegalArgumentException("Неверный код приглашения"));

        if (invite.isRevoked()) {
            throw new IllegalArgumentException("Код приглашения отозван");
        }
        if (invite.isExpired()) {
            throw new IllegalArgumentException("Срок действия кода приглашения истёк");
        }

        User user = new User();
        user.setEmail(form.getEmail().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setDisplayName(form.getDisplayName().trim());
        user.setInvitedByUserId(invite.getCreatedBy());
        user.setInviteId(invite.getId());
        user.setConsentAt(Instant.now());

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findActiveById(UUID id) {
        return userRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("User", id.toString()));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void softDelete(UUID userId) {
        User user = findActiveById(userId);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    public void updateAvatar(UUID userId, String avatarUrl) {
        User user = findActiveById(userId);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    public void updateProfile(UUID userId, String displayName, String bio) {
        User user = findActiveById(userId);
        user.setDisplayName(displayName);
        user.setBio(bio);
        userRepository.save(user);
    }

    public void resetPassword(UUID userId, String newPassword) {
        User user = findActiveById(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
