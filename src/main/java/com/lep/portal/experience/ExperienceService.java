package com.lep.portal.experience;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.ActivityRepository;
import com.lep.portal.catalog.Variant;
import com.lep.portal.catalog.VariantRepository;
import com.lep.portal.user.UserRepository;

@Service
@Transactional
public class ExperienceService {

    private final UserExperienceRepository userExperienceRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ActivityRepository activityRepository;
    private final VariantRepository variantRepository;
    private final UserRepository userRepository;

    public ExperienceService(UserExperienceRepository userExperienceRepository,
                             BookmarkRepository bookmarkRepository,
                             ActivityRepository activityRepository,
                             VariantRepository variantRepository,
                             UserRepository userRepository) {
        this.userExperienceRepository = userExperienceRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.activityRepository = activityRepository;
        this.variantRepository = variantRepository;
        this.userRepository = userRepository;
    }

    /**
     * Sets or updates the user's experience status for an activity (and optionally variant).
     * If status is null/blank, the record is deleted (status cleared).
     *
     * @return the current (possibly null) UserExperience after the operation
     */
    public UserExperience setStatus(UUID userId, UUID activityId, UUID variantId, ExperienceStatus status) {
        Optional<UserExperience> existing = userExperienceRepository
                .findByUserActivityVariant(userId, activityId, variantId);

        // Clear: remove the record
        if (status == null) {
            existing.ifPresent(ux -> userExperienceRepository
                    .deleteByUserActivityVariant(userId, activityId, variantId));
            return null;
        }

        if (existing.isPresent()) {
            // Update existing
            UserExperience ux = existing.get();
            ux.setStatus(status);
            return userExperienceRepository.save(ux);
        } else {
            // Create new
            UserExperience ux = new UserExperience();
            ux.setUserId(userId);
            ux.setActivityId(activityId);
            ux.setVariantId(variantId);
            ux.setStatus(status);
            return userExperienceRepository.save(ux);
        }
    }

    /**
     * Toggles a bookmark: creates if not present, deletes if present.
     *
     * @return true if bookmark was created (now bookmarked), false if deleted (unbookmarked)
     */
    public boolean toggleBookmark(UUID userId, UUID activityId, UUID variantId) {
        Optional<Bookmark> existing = bookmarkRepository
                .findByUserActivityVariant(userId, activityId, variantId);

        if (existing.isPresent()) {
            bookmarkRepository.deleteByUserActivityVariant(userId, activityId, variantId);
            return false;
        } else {
            Bookmark bm = new Bookmark();
            bm.setUserId(userId);
            bm.setActivityId(activityId);
            bm.setVariantId(variantId);
            bookmarkRepository.save(bm);
            return true;
        }
    }

    /**
     * Returns all bookmarks for the given user, joined with activity/variant/status info.
     */
    @Transactional(readOnly = true)
    public List<SavedItemDTO> getSavedActivities(UUID userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(userId);
        List<SavedItemDTO> result = new ArrayList<>();

        for (Bookmark bm : bookmarks) {
            Optional<Activity> activity = activityRepository.findById(bm.getActivityId());
            if (activity.isEmpty()) continue;

            Variant variant = null;
            if (bm.getVariantId() != null) {
                variant = variantRepository.findById(bm.getVariantId()).orElse(null);
            }

            Optional<UserExperience> ux = userExperienceRepository
                    .findByUserActivityVariant(userId, bm.getActivityId(), bm.getVariantId());

            result.add(new SavedItemDTO(bm, activity.get(), variant, ux.orElse(null)));
        }

        return result;
    }

    /**
     * Returns all user experiences for the given user, grouped by status.
     * Uses LinkedHashMap to preserve the display order:
     * INTERESTING, WANT_TO_TRY, TRIED, WANT_REPEAT, and then any others.
     */
    @Transactional(readOnly = true)
    public Map<String, List<UserExperience>> getMyExperiences(UUID userId) {
        List<UserExperience> all = userExperienceRepository.findByUserId(userId);

        // Define display order
        List<ExperienceStatus> displayOrder = List.of(
                ExperienceStatus.INTERESTING, ExperienceStatus.WANT_TO_TRY,
                ExperienceStatus.TRIED, ExperienceStatus.WANT_REPEAT);
        Map<ExperienceStatus, List<UserExperience>> grouped = new LinkedHashMap<>();

        // Initialize ordered buckets
        for (ExperienceStatus status : displayOrder) {
            grouped.put(status, new ArrayList<>());
        }

        // Distribute
        for (UserExperience ux : all) {
            ExperienceStatus key = ux.getStatus() != null ? ux.getStatus() : ExperienceStatus.INTERESTING;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ux);
        }

        // Remove empty buckets (but keep the order), key as name() for Thymeleaf
        Map<String, List<UserExperience>> result = new LinkedHashMap<>();
        for (ExperienceStatus status : displayOrder) {
            List<UserExperience> list = grouped.get(status);
            if (list != null && !list.isEmpty()) {
                result.put(status.name(), list);
            }
        }
        // Add any non-standard statuses at the end
        for (var entry : grouped.entrySet()) {
            if (!displayOrder.contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                result.put(entry.getKey().name(), entry.getValue());
            }
        }

        return result;
    }

    /**
     * Returns the status label in Russian for display.
     */
    public static String statusLabel(ExperienceStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case INTERESTING -> "Интересно";
            case WANT_TO_TRY -> "Хочу попробовать";
            case TRIED -> "Пробовал(а)";
            case WANT_REPEAT -> "Хочу повторить";
        };
    }

    /** Overload for Thymeleaf calls with string keys from getMyExperiences / getProfileExperiences. */
    public static String statusLabel(String statusName) {
        if (statusName == null) return "—";
        return switch (statusName) {
            case "INTERESTING" -> "Интересно";
            case "WANT_TO_TRY" -> "Хочу попробовать";
            case "TRIED" -> "Пробовал(а)";
            case "WANT_REPEAT" -> "Хочу повторить";
            default -> statusName;
        };
    }

    /**
     * Returns recent experience entries from other users (excludes current user),
     * joined with user display_name and activity title, for the Friends section
     * on the main page. Limited to 20 entries.
     */
    @Transactional(readOnly = true)
    public List<FriendActivityDTO> getFriendsActivity(UUID excludeUserId) {
        List<UserExperience> recent = userExperienceRepository.findRecentOtherUsers(excludeUserId, 20);
        List<FriendActivityDTO> result = new ArrayList<>();

        for (UserExperience ux : recent) {
            Activity activity = activityRepository.findById(ux.getActivityId()).orElse(null);
            if (activity == null) continue;

            String displayName = userRepository.findActiveById(ux.getUserId())
                    .map(u -> u.getDisplayName()).orElse("Пользователь");

            result.add(new FriendActivityDTO(
                    displayName,
                    ux.getStatus() != null ? ux.getStatus().name() : null,
                    activity.getTitle(),
                    ux.getActivityId(),
                    ux.getVariantId()));
        }

        return result;
    }

    /**
     * Returns experiences for a given user, grouped by status, with activity/variant titles
     * joined in ProfileExperienceItem DTOs. Only visible content (through views).
     */
    @Transactional(readOnly = true)
    public Map<String, List<ProfileExperienceItem>> getProfileExperiences(UUID userId) {
        List<UserExperience> all = userExperienceRepository.findByUserId(userId);

        List<ExperienceStatus> displayOrder = List.of(
                ExperienceStatus.INTERESTING, ExperienceStatus.WANT_TO_TRY,
                ExperienceStatus.TRIED, ExperienceStatus.WANT_REPEAT);
        Map<ExperienceStatus, List<ProfileExperienceItem>> grouped = new LinkedHashMap<>();

        for (ExperienceStatus status : displayOrder) {
            grouped.put(status, new ArrayList<>());
        }

        for (UserExperience ux : all) {
            Activity activity = activityRepository.findById(ux.getActivityId()).orElse(null);
            if (activity == null) continue;

            String variantTitle = null;
            if (ux.getVariantId() != null) {
                Variant variant = variantRepository.findById(ux.getVariantId()).orElse(null);
                if (variant != null) {
                    variantTitle = variant.getTitle();
                }
            }

            ExperienceStatus key = ux.getStatus() != null ? ux.getStatus() : ExperienceStatus.INTERESTING;
            grouped.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new ProfileExperienceItem(
                            ux, activity.getTitle(), variantTitle,
                            ux.getActivityId(), ux.getVariantId()));
        }

        // Remove empty buckets, key as name() for Thymeleaf
        Map<String, List<ProfileExperienceItem>> result = new LinkedHashMap<>();
        for (ExperienceStatus status : displayOrder) {
            List<ProfileExperienceItem> list = grouped.get(status);
            if (list != null && !list.isEmpty()) {
                result.put(status.name(), list);
            }
        }
        for (var entry : grouped.entrySet()) {
            if (!displayOrder.contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                result.put(entry.getKey().name(), entry.getValue());
            }
        }

        return result;
    }
}
