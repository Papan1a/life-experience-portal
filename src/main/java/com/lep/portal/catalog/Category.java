package com.lep.portal.catalog;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("categories")
public class Category {

    @Id
    private UUID id;

    @Column("slug")
    private String slug;

    @Column("label_ru")
    private String labelRu;

    @Column("sort_order")
    private short sortOrder;

    @Column("is_active")
    private boolean isActive;

    public Category() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getLabelRu() { return labelRu; }
    public void setLabelRu(String labelRu) { this.labelRu = labelRu; }

    public short getSortOrder() { return sortOrder; }
    public void setSortOrder(short sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    /** Display name for use in templates. Delegates to {@code labelRu}. */
    public String getName() { return labelRu; }
}
