package com.lep.portal.catalog;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

/**
 * Form binding for creating or editing a Variant.
 */
public class CreateVariantForm implements Serializable {

    @NotBlank(message = "Название обязательно")
    private String title;

    private String description;
    private String differenceReason;
    private String extraRequirements;

    /** Comma-separated tag names. */
    private String tags;

    // ---- Getters / Setters ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifferenceReason() { return differenceReason; }
    public void setDifferenceReason(String differenceReason) { this.differenceReason = differenceReason; }

    public String getExtraRequirements() { return extraRequirements; }
    public void setExtraRequirements(String extraRequirements) { this.extraRequirements = extraRequirements; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}
