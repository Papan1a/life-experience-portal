package com.lep.portal.experience;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.lep.portal.user.PortalUserDetails;

@Controller
public class ExperienceController {

    private final ExperienceService experienceService;
    private final UserExperienceRepository userExperienceRepository;

    public ExperienceController(ExperienceService experienceService,
                                UserExperienceRepository userExperienceRepository) {
        this.experienceService = experienceService;
        this.userExperienceRepository = userExperienceRepository;
    }

    /**
     * HTMX endpoint: set/change/clear the user's experience status.
     * Returns the updated status buttons fragment.
     */
    @PostMapping("/experiences")
    public String setStatus(@RequestParam UUID activityId,
                            @RequestParam(required = false) UUID variantId,
                            @RequestParam(required = false) String status,
                            @AuthenticationPrincipal PortalUserDetails principal,
                            Model model) {

        experienceService.setStatus(principal.getUserId(), activityId, variantId, status);

        Optional<UserExperience> ux = userExperienceRepository
                .findByUserActivityVariant(principal.getUserId(), activityId, variantId);

        model.addAttribute("activityId", activityId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("userExperience", ux.orElse(null));

        return "experience/_status_buttons :: statusButtons";
    }
}
