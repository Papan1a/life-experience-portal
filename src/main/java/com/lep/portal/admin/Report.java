package com.lep.portal.admin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("reports")
public class Report {

    @Id
    private UUID id;

    @Column("reporter_user_id")
    private UUID reporterUserId;

    @Column("target_type")
    private String targetType;

    @Column("target_id")
    private UUID targetId;

    @Column("reason")
    private String reason;

    @Column("comment")
    private String comment;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("resolved_at")
    private Instant resolvedAt;

    @Column("resolved_by")
    private UUID resolvedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(UUID reporterUserId) { this.reporterUserId = reporterUserId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }
}