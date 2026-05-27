package com.lep.portal.experience;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.ActivityRepository;
import com.lep.portal.catalog.Variant;
import com.lep.portal.catalog.VariantRepository;
import com.lep.portal.user.PortalUserDetails;

@Controller
public class ExperiencePageController {

    private final ExperienceService experienceService;
    private final ActivityRepository activityRepository;
    private final VariantRepository variantRepository;

    public ExperiencePageController(ExperienceService experienceService,
                                    ActivityRepository activityRepository,
                                    VariantRepository variantRepository) {
        this.experienceService = experienceService;
        this.activityRepository = activityRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * GET /saved — bookmarks of the current user, joined with activity/variant/status info.
     */
    @GetMapping("/saved")
    public String saved(@AuthenticationPrincipal PortalUserDetails principal, Model model) {
        List<SavedItemDTO> savedItems = experienceService.getSavedActivities(principal.getUserId());
        model.addAttribute("savedItems", savedItems);
        return "experience/saved";
    }

    /**
     * GET /my-experiences — all user_experiences of the current user, grouped by status.
     */
    @GetMapping("/my-experiences")
    public String myExperiences(@AuthenticationPrincipal PortalUserDetails principal, Model model) {
        Map<String, List<UserExperience>> grouped = experienceService.getMyExperiences(principal.getUserId());

        // Build title lookup maps for display
        Map<UUID, String> activityTitles = new HashMap<>();
        Map<UUID, String> variantTitles = new HashMap<>();

        for (var entry : grouped.entrySet()) {
            for (UserExperience ux : entry.getValue()) {
                if (!activityTitles.containsKey(ux.getActivityId())) {
                    Optional<Activity> act = activityRepository.findById(ux.getActivityId());
                    activityTitles.put(ux.getActivityId(),
                            act.map(Activity::getTitle).orElse("Активность #" + ux.getActivityId()));
                }
                if (ux.getVariantId() != null && !variantTitles.containsKey(ux.getVariantId())) {
                    Optional<Variant> var = variantRepository.findById(ux.getVariantId());
                    variantTitles.put(ux.getVariantId(),
                            var.map(Variant::getTitle).orElse("Вариант"));
                }
            }
        }

        model.addAttribute("experiences", grouped);
        model.addAttribute("activityTitles", activityTitles);
        model.addAttribute("variantTitles", variantTitles);

        return "experience/my_experiences";
    }
}
