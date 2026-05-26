package com.lep.portal.catalog;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lep.portal.common.ForbiddenException;
import com.lep.portal.common.NotFoundException;

@Service
@Transactional
public class CatalogService {

    private static final int PAGE_SIZE = 20;

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

    @Transactional(readOnly = true)
    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category", id.toString()));
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

    /**
     * Returns an activity only if visible (via visible_activities view).
     * Used by the catalog detail pages (Block 3).
     */
    @Transactional(readOnly = true)
    public Activity getVisibleActivity(UUID id) {
        return activityRepository.findByIdFromVisible(id)
                .orElseThrow(() -> new NotFoundException("Activity", id.toString()));
    }

    /**
     * Returns similar activities in the same category (excluding the current one).
     */
    @Transactional(readOnly = true)
    public List<Activity> getSimilarActivities(UUID excludeId, UUID categoryId, int limit) {
        return activityRepository.findSimilar(excludeId, categoryId, limit);
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

    public Activity updateActivity(UUID activityId, Activity updates, UUID actorId, boolean isAdmin) {
        Activity existing = getActivity(activityId);
        // Only creator or admin can edit
        if (!existing.getCreatedBy().equals(actorId) && !isAdmin) {
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

    // ---- Variants (continued) ----

    public Variant updateVariant(UUID variantId, Variant updates, UUID actorId, boolean isAdmin) {
        Variant existing = getVariant(variantId);
        // Only creator or admin can edit
        if (!existing.getCreatedBy().equals(actorId) && !isAdmin) {
            throw new ForbiddenException("Только автор может редактировать вариант");
        }

        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setDifferenceReason(updates.getDifferenceReason());
        existing.setExtraRequirements(updates.getExtraRequirements());

        return variantRepository.save(existing);
    }

    // ---- Tags (continued) ----

    @Transactional(readOnly = true)
    public List<Tag> getTagsByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Collections.emptyList();
        }
        return tagRepository.findBySlugPrefix(prefix.toLowerCase().trim());
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

    /**
     * Returns a variant only if visible (via visible_variants view).
     * Used by the catalog detail pages (Block 3).
     */
    @Transactional(readOnly = true)
    public Variant getVisibleVariant(UUID id) {
        return variantRepository.findByIdFromVisible(id)
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

    // ---- Pagination support (Block 3) ----

    @Transactional(readOnly = true)
    public CatalogPage getCatalogPage(String categorySlug, List<String> tagSlugs, int page) {
        int limit = PAGE_SIZE;
        long offset = (long) (page - 1) * PAGE_SIZE;

        // Resolve category
        UUID categoryId = null;
        if (categorySlug != null && !categorySlug.isBlank()) {
            Category cat = getCategoryBySlug(categorySlug);
            categoryId = cat.getId();
        }

        // Resolve tags
        List<UUID> tagIds = Collections.emptyList();
        if (tagSlugs != null && !tagSlugs.isEmpty()) {
            List<Tag> tags = tagRepository.findBySlugIn(tagSlugs);
            tagIds = tags.stream().map(Tag::getId).toList();
        }

        List<Activity> activities;
        long total;

        if (!tagIds.isEmpty()) {
            activities = activityRepository.findVisibleByTags(tagIds, tagIds.size(), limit, offset);
            total = activityRepository.countVisibleByTags(tagIds, tagIds.size());
        } else if (categoryId != null) {
            activities = activityRepository.findVisibleByCategoryPaginated(categoryId, limit, offset);
            total = activityRepository.countVisibleByCategory(categoryId);
        } else {
            activities = activityRepository.findAllVisiblePaginated(limit, offset);
            total = activityRepository.countVisible();
        }

        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;

        return new CatalogPage(activities, page, totalPages, total);
    }

    /**
     * Simple DTO for paginated catalog results.
     */
    public record CatalogPage(List<Activity> activities, int currentPage, int totalPages, long totalCount) {
        public boolean hasNext() { return currentPage < totalPages; }
        public boolean hasPrev() { return currentPage > 1; }
    }
}
