package com.lep.portal.catalog;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Owned collection row in Variant aggregate — maps variant_tags join table (D6).
 */
@Table("variant_tags")
public class VariantTagRef {

    @Column("variant_id")
    private UUID variantId;

    @Column("tag_id")
    private UUID tagId;

    public VariantTagRef() {
    }

    public VariantTagRef(UUID tagId) {
        this.tagId = tagId;
    }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
}
