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
public class BookmarkController {

    private final ExperienceService experienceService;
    private final BookmarkRepository bookmarkRepository;

    public BookmarkController(ExperienceService experienceService,
                              BookmarkRepository bookmarkRepository) {
        this.experienceService = experienceService;
        this.bookmarkRepository = bookmarkRepository;
    }

    /**
     * HTMX endpoint: toggle bookmark on/off.
     * Returns the updated bookmark button fragment.
     */
    @PostMapping("/bookmarks/toggle")
    public String toggleBookmark(@RequestParam UUID activityId,
                                 @RequestParam(required = false) UUID variantId,
                                 @AuthenticationPrincipal PortalUserDetails principal,
                                 Model model) {

        experienceService.toggleBookmark(principal.getUserId(), activityId, variantId);

        Optional<Bookmark> bm = bookmarkRepository
                .findByUserActivityVariant(principal.getUserId(), activityId, variantId);

        model.addAttribute("activityId", activityId);
        model.addAttribute("variantId", variantId);
        model.addAttribute("bookmark", bm.orElse(null));

        return "experience/_bookmark_button :: bookmarkButton";
    }
}
