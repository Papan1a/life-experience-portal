package com.lep.portal.invite;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends CrudRepository<Invite, UUID> {

    @Query("SELECT * FROM invites WHERE code = :code")
    Optional<Invite> findByCode(@Param("code") String code);

    @Query("SELECT * FROM invites WHERE created_by = :userId ORDER BY created_at DESC")
    Iterable<Invite> findByCreatedBy(@Param("userId") UUID userId);

    /**
     * Count active invites for limit check.
     * Active = not used, not revoked, not expired.
     */
    @Query("SELECT COUNT(*) FROM invites WHERE created_by = :userId " +
           "AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()")
    int countActiveByCreator(@Param("userId") UUID userId);

    /**
     * Atomic mark-as-used. Returns 1 if marked, 0 if already used/revoked/expired.
     * CRITICAL: must be atomic to prevent race condition.
     */
    @Modifying
    @Query("UPDATE invites SET used_at = now(), used_by_user_id = :userId " +
           "WHERE id = :inviteId " +
           "AND used_at IS NULL AND revoked_at IS NULL AND expires_at > now()")
    int markUsed(@Param("inviteId") UUID inviteId, @Param("userId") UUID userId);
}
