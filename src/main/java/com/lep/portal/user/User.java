package com.lep.portal.user;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("users")
public class User extends BaseAggregate {

    @Column("email")
    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("display_name")
    private String displayName;

    @Column("bio")
    private String bio;

    @Column("avatar_url")
    private String avatarUrl;

    @Column("is_admin")
    private boolean isAdmin;

    @Column("invited_by_user_id")
    private UUID invitedByUserId;

    @Column("invite_id")
    private UUID inviteId;

    @Column("consent_at")
    private Instant consentAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("deleted_at")
    private Instant deletedAt;

    @Transient
    private Collection<String> authorities;

    public User() {
        this.consentAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ---- Getters / Setters ----

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(UUID invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public UUID getInviteId() {
        return inviteId;
    }

    public void setInviteId(UUID inviteId) {
        this.inviteId = inviteId;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public void setConsentAt(Instant consentAt) {
        this.consentAt = consentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Transient
    public Collection<String> getAuthorities() {
        if (authorities == null) {
            authorities = isAdmin
                    ? Collections.singletonList("ROLE_ADMIN")
                    : Collections.singletonList("ROLE_USER");
        }
        return authorities;
    }
}
