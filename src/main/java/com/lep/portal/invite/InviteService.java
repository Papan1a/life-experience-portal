package com.lep.portal.invite;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InviteService {

    private final InviteRepository inviteRepository;
    private final int ttlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteService(InviteRepository inviteRepository,
                         @Value("${app.invite.ttl-days:7}") int ttlDays) {
        this.inviteRepository = inviteRepository;
        this.ttlDays = ttlDays;
    }

    public Invite createInvite(UUID createdBy) {
        Invite invite = new Invite();
        invite.setCode(generateCode());
        invite.setCreatedBy(createdBy);
        invite.setExpiresAt(Instant.now().plus(ttlDays, ChronoUnit.DAYS));
        return inviteRepository.save(invite);
    }

    public void revokeInvite(UUID inviteId, UUID actorId) {
        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));
        invite.setRevokedAt(Instant.now());
        inviteRepository.save(invite);
    }

    public Invite getInvite(UUID inviteId) {
        return inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));
    }

    public Invite validateAndUse(String code) {
        Invite invite = inviteRepository.findByCode(code)
                .orElseThrow(() -> new InvalidInviteException("Неверный код приглашения"));

        if (invite.isRevoked()) {
            throw new InvalidInviteException("Код приглашения отозван");
        }
        if (invite.isExpired()) {
            throw new InvalidInviteException("Срок действия кода приглашения истёк");
        }
        return invite;
    }

    public Iterable<Invite> getMyInvites(UUID userId) {
        return inviteRepository.findByCreatedBy(userId);
    }

    private String generateCode() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
