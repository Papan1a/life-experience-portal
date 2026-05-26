package com.lep.portal.experience;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.Variant;

/**
 * DTO for the /saved page: bookmark + joined activity/variant + optional user experience status.
 */
public record SavedItemDTO(
        Bookmark bookmark,
        Activity activity,
        Variant variant,
        UserExperience userExperience) {
}
