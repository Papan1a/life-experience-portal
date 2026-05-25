package com.lep.portal.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends CrudRepository<User, UUID> {

    @Query("SELECT * FROM users WHERE email = :email AND deleted_at IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT * FROM users WHERE id = :id AND deleted_at IS NULL")
    Optional<User> findActiveById(@Param("id") UUID id);

    @Query("SELECT COUNT(*) > 0 FROM users WHERE email = :email AND deleted_at IS NULL")
    boolean emailExists(@Param("email") String email);
}
