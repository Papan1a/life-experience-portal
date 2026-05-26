package com.lep.portal.experience;

import java.util.UUID;

/**
 * DTO for user profile experience lists: joined activity/variant titles.
 */
public record ProfileExperienceItem(
        UserExperience userExperience,
        String activityTitle,
        String variantTitle,
        UUID activityId,
        UUID variantId) {
}
