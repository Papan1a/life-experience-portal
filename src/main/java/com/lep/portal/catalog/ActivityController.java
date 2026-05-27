package com.lep.portal.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.experience.Bookmark;
import com.lep.portal.experience.BookmarkRepository;
import com.lep.portal.experience.UserExperience;
import com.lep.portal.experience.UserExperienceRepository;
import com.lep.portal.user.PortalUserDetails;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/activities")
public class ActivityController {

    private final CatalogService catalogService;
    private final UserExperienceRepository userExperienceRepository;
    private final BookmarkRepository bookmarkRepository;

    public ActivityController(CatalogService catalogService,
                              UserExperienceRepository userExperienceRepository,
                              BookmarkRepository bookmarkRepository) {
        this.catalogService = catalogService;
        this.userExperienceRepository = userExperienceRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // ---- Detail page (Block 3) ----

    @GetMapping("/{id}")
    public String activityDetail(@PathVariable UUID id,
                                 @AuthenticationPrincipal PortalUserDetails principal,
                                 Model model) {
        Activity activity = catalogService.getVisibleActivity(id);
        model.addAttribute("activity", activity);

        boolean canEdit = principal != null &&
                (principal.isAdmin() || activity.getCreatedBy().equals(principal.getUserId()));
        model.addAttribute("canEdit", canEdit);

        model.addAttribute("variants", catalogService.getVisibleVariants(id));
        model.addAttribute("similar", catalogService.getSimilarActivities(id, activity.getCategoryId(), 5));

        Optional<UserExperience> ux = userExperienceRepository
                .findByUserActivityVariant(principal.getUserId(), id, null);
        model.addAttribute("userExperience", ux.orElse(null));

        Optional<Bookmark> bm = bookmarkRepository
                .findByUserActivityVariant(principal.getUserId(), id, null);
        model.addAttribute("bookmark", bm.orElse(null));

        model.addAttribute("category", catalogService.getCategoryById(activity.getCategoryId()));

        return "catalog/activity";
    }

    // ---- Create activity (Block 4) ----

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new CreateActivityForm());
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("complexities", List.of("beginner", "medium", "advanced"));
        model.addAttribute("costTiers", List.of("free", "low", "medium", "high"));
        return "catalog/activity_form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CreateActivityForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal PortalUserDetails principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        // Validation errors
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("complexities", List.of("beginner", "medium", "advanced"));
            model.addAttribute("costTiers", List.of("free", "low", "medium", "high"));
            return "catalog/activity_form";
        }

        // Dup-check: non-blocking warning
        if (!form.isForce()) {
            List<Activity> dups = catalogService.checkDuplicateTitle(form.getTitle());
            if (!dups.isEmpty()) {
                model.addAttribute("categories", catalogService.getAllCategories());
                model.addAttribute("complexities", List.of("beginner", "medium", "advanced"));
                model.addAttribute("costTiers", List.of("free", "low", "medium", "high"));
                model.addAttribute("duplicates", dups);
                return "catalog/activity_form";
            }
        }

        // Build Activity
        Activity activity = new Activity();
        activity.setTitle(form.getTitle().trim());
        activity.setDescription(form.getDescription());
        activity.setCategoryId(UUID.fromString(form.getCategoryId()));
        activity.setComplexity(form.getComplexity().name());
        activity.setCostTier(form.getCostTier().name());
        activity.setEstimatedDuration(form.getEstimatedDuration());
        activity.setRequirements(form.getRequirements());
        activity.setInterestingReason(form.getInterestingReason());

        // Normalise tags
        List<String> tagNames = parseTags(form.getTags());
        for (String name : tagNames) {
            Tag tag = catalogService.findOrCreateTag(name, principal.getUserId());
            activity.addTag(tag.getId());
        }

        Activity saved = catalogService.createActivity(activity, principal.getUserId());
        return "redirect:/activities/" + saved.getId();
    }

    // ---- Edit activity (Block 4) ----

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id,
                           @AuthenticationPrincipal PortalUserDetails principal,
                           Model model) {
        Activity activity = catalogService.getActivity(id);
        // Only creator or admin can edit — check eagerly
        if (!activity.getCreatedBy().equals(principal.getUserId()) && !principal.isAdmin()) {
            throw new com.lep.portal.common.ForbiddenException("Только автор может редактировать активность");
        }

        CreateActivityForm form = new CreateActivityForm();
        form.setTitle(activity.getTitle());
        form.setDescription(activity.getDescription());
        form.setCategoryId(activity.getCategoryId().toString());
        form.setComplexity(activity.getComplexity() != null ? ComplexityLevel.valueOf(activity.getComplexity()) : null);
        form.setCostTier(activity.getCostTier() != null ? CostTier.valueOf(activity.getCostTier()) : null);
        form.setEstimatedDuration(activity.getEstimatedDuration());
        form.setRequirements(activity.getRequirements());
        form.setInterestingReason(activity.getInterestingReason());
        // Tags will be re-entered; we don't pre-populate for simplicity

        model.addAttribute("form", form);
        model.addAttribute("activity", activity);
        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("complexities", List.of("beginner", "medium", "advanced"));
        model.addAttribute("costTiers", List.of("free", "low", "medium", "high"));
        return "catalog/activity_form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("form") CreateActivityForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal PortalUserDetails principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            Activity activity = catalogService.getActivity(id);
            model.addAttribute("activity", activity);
            model.addAttribute("categories", catalogService.getAllCategories());
            model.addAttribute("complexities", List.of("beginner", "medium", "advanced"));
            model.addAttribute("costTiers", List.of("free", "low", "medium", "high"));
            return "catalog/activity_form";
        }

        Activity updates = new Activity();
        updates.setTitle(form.getTitle().trim());
        updates.setDescription(form.getDescription());
        updates.setCategoryId(UUID.fromString(form.getCategoryId()));
        updates.setComplexity(form.getComplexity().name());
        updates.setCostTier(form.getCostTier().name());
        updates.setEstimatedDuration(form.getEstimatedDuration());
        updates.setRequirements(form.getRequirements());
        updates.setInterestingReason(form.getInterestingReason());

        catalogService.updateActivity(id, updates, principal.getUserId(), principal.isAdmin());
        return "redirect:/activities/" + id;
    }

    // ---- Helpers ----

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
