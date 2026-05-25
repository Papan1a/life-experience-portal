package com.lep.portal.catalog;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Owned collection row in Activity aggregate — maps activity_tags join table (D6).
 */
@Table("activity_tags")
public class ActivityTagRef {

    @Column("activity_id")
    private UUID activityId;

    @Column("tag_id")
    private UUID tagId;

    public ActivityTagRef() {
    }

    public ActivityTagRef(UUID tagId) {
        this.tagId = tagId;
    }

    public UUID getActivityId() { return activityId; }
    public void setActivityId(UUID activityId) { this.activityId = activityId; }

    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
}
