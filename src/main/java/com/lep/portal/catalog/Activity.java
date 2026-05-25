package com.lep.portal.catalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("activities")
public class Activity extends BaseAggregate {

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("category_id")
    private UUID categoryId;

    @Column("complexity")
    private String complexity;

    @Column("cost_tier")
    private String costTier;

    @MappedCollection(idColumn = "activity_id")
    private Set<ActivityTagRef> tags = new HashSet<>();

    @Column("estimated_duration")
    private String estimatedDuration;

    @Column("requirements")
    private String requirements;

    @Column("interesting_reason")
    private String interestingReason;

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

    public Activity() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = "ACTIVE";
    }

    // ---- Tags collection ----

    public Set<ActivityTagRef> getTags() { return tags; }
    public void setTags(Set<ActivityTagRef> tags) { this.tags = tags; }

    public void addTag(UUID tagId) {
        if (tags == null) tags = new HashSet<>();
        tags.add(new ActivityTagRef(tagId));
    }

    // ---- Getters/Setters ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public String getComplexity() { return complexity; }
    public void setComplexity(String complexity) { this.complexity = complexity; }

    public String getCostTier() { return costTier; }
    public void setCostTier(String costTier) { this.costTier = costTier; }

    public String getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getInterestingReason() { return interestingReason; }
    public void setInterestingReason(String interestingReason) { this.interestingReason = interestingReason; }

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

    public boolean isVisible() {
        return "ACTIVE".equals(status) && deletedAt == null;
    }
}
