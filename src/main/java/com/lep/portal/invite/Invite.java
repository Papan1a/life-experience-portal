package com.lep.portal.invite;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("invites")
public class Invite extends BaseAggregate {

    @Column("code")
    private String code;

    @Column("created_by")
    private UUID createdBy;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("revoked_at")
    private Instant revokedAt;

    @Column("created_at")
    private Instant createdAt;

    public Invite() {
        this.createdAt = Instant.now();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }
}
