package com.lep.portal.catalog;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.lep.portal.common.BaseAggregate;

@Table("tags")
public class Tag extends BaseAggregate {

    @Column("slug")
    private String slug;

    @Column("name")
    private String name;

    @Column("created_by")
    private UUID createdBy;

    @Column("created_at")
    private Instant createdAt;

    public Tag() {
        this.createdAt = Instant.now();
    }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
