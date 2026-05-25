package com.lep.portal.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lep.portal.common.ForbiddenException;
import com.lep.portal.common.NotFoundException;

@Service
@Transactional
public class CatalogService {

    private final ActivityRepository activityRepository;
    private final VariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public CatalogService(ActivityRepository activityRepository,
                          VariantRepository variantRepository,
                          CategoryRepository categoryRepository,
                          TagRepository tagRepository) {
        this.activityRepository = activityRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    // ---- Categories ----

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllActive();
    }

    @Transactional(readOnly = true)
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Category", slug));
    }

    // ---- Activities ----

    @Transactional(readOnly = true)
    public List<Activity> getVisibleActivities() {
        return activityRepository.findAllVisible();
    }

    @Transactional(readOnly = true)
    public List<Activity> getVisibleActivitiesByCategory(UUID categoryId) {
        return activityRepository.findVisibleByCategory(categoryId);
    }

    @Transactional(readOnly = true)
    public List<Activity> getVisibleActivitiesByTag(UUID tagId) {
        return activityRepository.findVisibleByTag(tagId);
    }

    @Transactional(readOnly = true)
    public Activity getActivity(UUID id) {
        return activityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Activity", id.toString()));
    }

    @Transactional(readOnly = true)
    public List<Activity> getActivitiesByUser(UUID userId) {
        return activityRepository.findByCreatedBy(userId);
    }

    public Activity createActivity(Activity activity, UUID actorId) {
        activity.setCreatedBy(actorId);
        activity.setStatus("ACTIVE");
        return activityRepository.save(activity);
    }

    public Activity updateActivity(UUID activityId, Activity updates, UUID actorId) {
        Activity existing = getActivity(activityId);
        // Only creator or admin can edit
        if (!existing.getCreatedBy().equals(actorId)) {
            throw new ForbiddenException("Только автор может редактировать активность");
        }

        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setCategoryId(updates.getCategoryId());
        existing.setComplexity(updates.getComplexity());
        existing.setCostTier(updates.getCostTier());
        existing.setEstimatedDuration(updates.getEstimatedDuration());
        existing.setRequirements(updates.getRequirements());
        existing.setInterestingReason(updates.getInterestingReason());

        return activityRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<Activity> checkDuplicateTitle(String title) {
        return activityRepository.findByTitleIgnoreCase(title);
    }

    // ---- Variants ----

    @Transactional(readOnly = true)
    public List<Variant> getVisibleVariants(UUID activityId) {
        return variantRepository.findVisibleByActivity(activityId);
    }

    @Transactional(readOnly = true)
    public Variant getVariant(UUID id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Variant", id.toString()));
    }

    public Variant createVariant(Variant variant, UUID actorId) {
        variant.setCreatedBy(actorId);
        variant.setStatus("ACTIVE");
        return variantRepository.save(variant);
    }

    // ---- Tags ----

    @Transactional(readOnly = true)
    public List<Tag> getAllTags() {
        return tagRepository.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public List<Tag> searchTags(String query) {
        return tagRepository.searchByName(query);
    }

    public Tag findOrCreateTag(String name, UUID createdBy) {
        String slug = name.toLowerCase().trim();
        return tagRepository.findBySlug(slug)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setSlug(slug);
                    tag.setName(name.trim());
                    tag.setCreatedBy(createdBy);
                    return tagRepository.save(tag);
                });
    }
}
