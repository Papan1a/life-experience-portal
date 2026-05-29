package com.lep.portal.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.lep.portal.catalog.CatalogService.CatalogPage;
import com.lep.portal.experience.ExperienceService;
import com.lep.portal.experience.FriendActivityDTO;
import com.lep.portal.user.PortalUserDetails;

@Controller
public class CatalogController {

    private final CatalogService catalogService;
    private final ExperienceService experienceService;

    public CatalogController(CatalogService catalogService,
                             ExperienceService experienceService) {
        this.catalogService = catalogService;
        this.experienceService = experienceService;
    }

    /**
     * Main catalog page at GET /.
     * Supports optional filters: ?category=sport, ?tags=water,outdoor, ?page=2.
     * When HX-Request:true header is present, returns only the content fragment.
     */
    @GetMapping("/")
    public String catalog(@RequestParam(required = false) String category,
                          @RequestParam(required = false) String tags,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                          @AuthenticationPrincipal PortalUserDetails principal,
                          Model model) {

        List<String> tagSlugs = (tags != null && !tags.isBlank())
                ? Arrays.asList(tags.split(","))
                : Collections.emptyList();

        CatalogPage catalogPage = catalogService.getCatalogPage(category, tagSlugs, page);

        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("page", catalogPage);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentTags", tags);

        // Friends activity section
        java.util.List<FriendActivityDTO> friendsActivity =
                experienceService.getFriendsActivity(principal.getUserId());
        model.addAttribute("friendsActivity", friendsActivity);

        // HTMX: return card page fragment (infinite scroll)
        if ("true".equals(hxRequest)) {
            return "catalog/_cards :: card-page";
        }

        return "catalog/index";
    }
}
