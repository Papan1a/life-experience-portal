package com.lep.portal.catalog;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

/**
 * Form binding for creating or editing an Activity.
 * Tags are submitted as comma-separated slug strings.
 */
public class CreateActivityForm implements Serializable {

    @NotBlank(message = "Название обязательно")
    private String title;

    private String description;

    @NotBlank(message = "Категория обязательна")
    private String categoryId;

    private ComplexityLevel complexity = ComplexityLevel.medium;
    private CostTier costTier = CostTier.free;
    private String estimatedDuration;
    private String requirements;
    private String interestingReason;

    /** Comma-separated tag names (e.g. "вода, спорт"). */
    private String tags;

    /** When true, bypass duplicate warning. */
    private boolean force;

    // ---- Getters / Setters ----

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public ComplexityLevel getComplexity() { return complexity; }
    public void setComplexity(ComplexityLevel complexity) { this.complexity = complexity; }

    public CostTier getCostTier() { return costTier; }
    public void setCostTier(CostTier costTier) { this.costTier = costTier; }

    public String getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getInterestingReason() { return interestingReason; }
    public void setInterestingReason(String interestingReason) { this.interestingReason = interestingReason; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isForce() { return force; }
    public void setForce(boolean force) { this.force = force; }
}
