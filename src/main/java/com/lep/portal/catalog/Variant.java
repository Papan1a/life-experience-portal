package com.lep.portal.catalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("variants")
public class Variant extends BaseAggregate {

    @Column("activity_id")
    private UUID activityId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("difference_reason")
    private String differenceReason;

    @Column("extra_requirements")
    private String extraRequirements;

    @Column("created_by")
    private UUID createdBy;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("deleted_at")
    private Instant deletedAt;

    @MappedCollection(idColumn = "variant_id")
    private Set<VariantTagRef> tags = new HashSet<>();

    public Variant() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "ACTIVE";
    }

    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID activityId) { this.activityId = activityId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifferenceReason() { return differenceReason; }
    public void setDifferenceReason(String differenceReason) { this.differenceReason = differenceReason; }

    public String getExtraRequirements() { return extraRequirements; }
    public void setExtraRequirements(String extraRequirements) { this.extraRequirements = extraRequirements; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    // ---- Tags collection ----

    public Set<VariantTagRef> getTags() { return tags; }
    public void setTags(Set<VariantTagRef> tags) { this.tags = tags; }

    public void addTag(UUID tagId) {
        if (tags == null) tags = new HashSet<>();
        tags.add(new VariantTagRef(tagId));
    }
}
