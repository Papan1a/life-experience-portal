package com.lep.portal.invite;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface InviteRepository extends CrudRepository<Invite, UUID> {

    @Query("SELECT * FROM invites WHERE code = :code")
    Optional<Invite> findByCode(@Param("code") String code);

    @Query("SELECT * FROM invites WHERE created_by = :userId ORDER BY created_at DESC")
    Iterable<Invite> findByCreatedBy(@Param("userId") UUID userId);
}
