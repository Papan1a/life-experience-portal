package com.lep.portal.experience;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("bookmarks")
public class Bookmark extends BaseAggregate {

    @Column("user_id")
    private UUID userId;

    @Column("activity_id")
    private UUID activityId;

    @Column("variant_id")
    private UUID variantId;

    @Column("created_at")
    private Instant createdAt;

    public Bookmark() {
        this.createdAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID activityId) { this.activityId = activityId; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
