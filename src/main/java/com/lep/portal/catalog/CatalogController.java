package com.lep.portal.catalog;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.user.PortalUserDetails;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public String catalog(@RequestParam(required = false) UUID categoryId,
                          @RequestParam(required = false) UUID tagId,
                          Model model) {
        model.addAttribute("categories", catalogService.getAllCategories());

        if (tagId != null) {
            model.addAttribute("activities", catalogService.getVisibleActivitiesByTag(tagId));
        } else if (categoryId != null) {
            model.addAttribute("activities", catalogService.getVisibleActivitiesByCategory(categoryId));
        } else {
            model.addAttribute("activities", catalogService.getVisibleActivities());
        }

        return "catalog";
    }

    @GetMapping("/activities/{id}")
    public String activityDetail(@PathVariable UUID id, Model model) {
        Activity activity = catalogService.getActivity(id);
        model.addAttribute("activity", activity);
        model.addAttribute("variants", catalogService.getVisibleVariants(id));
        model.addAttribute("category", catalogService.getCategoryBySlug(
                catalogService.getCategoryBySlug("sport").getSlug())); // placeholder
        return "activity";
    }

    @GetMapping("/activities/new")
    public String newActivityForm(Model model) {
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("activity", new Activity());
        return "activity-form";
    }

    @PostMapping("/activities/new")
    public String createActivity(@Valid @ModelAttribute Activity activity,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal PortalUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "activity-form";
        }

        // Duplicate title check (non-blocking warning)
        var duplicates = catalogService.checkDuplicateTitle(activity.getTitle());
        if (!duplicates.isEmpty()) {
            redirectAttributes.addFlashAttribute("duplicateWarning",
                    "Похожая активность уже существует. Возможно, стоит создать Вариант вместо новой активности.");
        }

        Activity created = catalogService.createActivity(activity, principal.getUserId());
        return "redirect:/catalog/activities/" + created.getId();
    }

    @GetMapping("/variants/new")
    public String newVariantForm(@RequestParam UUID activityId, Model model) {
        model.addAttribute("activity", catalogService.getActivity(activityId));
        model.addAttribute("variant", new Variant());
        return "variant-form";
    }

    @PostMapping("/variants/new")
    public String createVariant(@Valid @ModelAttribute Variant variant,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal PortalUserDetails principal) {
        if (bindingResult.hasErrors()) {
            return "variant-form";
        }
        Variant created = catalogService.createVariant(variant, principal.getUserId());
        return "redirect:/catalog/activities/" + created.getActivityId();
    }
}
