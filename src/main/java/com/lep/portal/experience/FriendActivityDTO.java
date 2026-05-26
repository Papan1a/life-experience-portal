package com.lep.portal.experience;

import java.util.UUID;

/**
 * DTO for the "Что делают другие" Friends section on the main page.
 */
public record FriendActivityDTO(
        String displayName,
        String status,
        String activityTitle,
        UUID activityId,
        UUID variantId) {
}
