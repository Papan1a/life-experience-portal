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

import com.lep.portal.common.NotFoundException;
import com.lep.portal.experience.Bookmark;
import com.lep.portal.experience.BookmarkRepository;
import com.lep.portal.experience.UserExperience;
import com.lep.portal.experience.UserExperienceRepository;
import com.lep.portal.user.PortalUserDetails;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/activities/{activityId}/variants")
public class VariantController {

    private final CatalogService catalogService;
    private final UserExperienceRepository userExperienceRepository;
    private final BookmarkRepository bookmarkRepository;

    public VariantController(CatalogService catalogService,
                             UserExperienceRepository userExperienceRepository,
                             BookmarkRepository bookmarkRepository) {
        this.catalogService = catalogService;
        this.userExperienceRepository = userExperienceRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // ---- Detail page (Block 3) ----

    @GetMapping("/{variantId}")
    public String variantDetail(@PathVariable UUID activityId,
                                @PathVariable UUID variantId,
                                @AuthenticationPrincipal PortalUserDetails principal,
                                Model model) {
        Activity activity = catalogService.getVisibleActivity(activityId);
        Variant variant = catalogService.getVisibleVariant(variantId);

        if (!variant.getActivityId().equals(activityId)) {
            throw new NotFoundException("Variant", variantId.toString());
        }

        model.addAttribute("activity", activity);
        model.addAttribute("variant", variant);

        boolean canEdit = principal != null &&
                (principal.isAdmin() || variant.getCreatedBy().equals(principal.getUserId()));
        model.addAttribute("canEdit", canEdit);

        Optional<UserExperience> ux = userExperienceRepository
                .findByUserActivityVariant(principal.getUserId(), activityId, variantId);
        model.addAttribute("userExperience", ux.orElse(null));

        Optional<Bookmark> bm = bookmarkRepository
                .findByUserActivityVariant(principal.getUserId(), activityId, variantId);
        model.addAttribute("bookmark", bm.orElse(null));

        return "catalog/variant";
    }

    // ---- Create variant (Block 4) ----

    @GetMapping("/new")
    public String createForm(@PathVariable UUID activityId,
                             @AuthenticationPrincipal PortalUserDetails principal,
                             Model model) {
        Activity activity = catalogService.getActivity(activityId);
        if (!"ACTIVE".equals(activity.getStatus())) {
            throw new NotFoundException("Activity", activityId.toString());
        }

        model.addAttribute("activity", activity);
        model.addAttribute("form", new CreateVariantForm());
        return "catalog/variant_form";
    }

    @PostMapping
    public String create(@PathVariable UUID activityId,
                         @Valid @ModelAttribute("form") CreateVariantForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal PortalUserDetails principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            Activity activity = catalogService.getActivity(activityId);
            model.addAttribute("activity", activity);
            return "catalog/variant_form";
        }

        Activity activity = catalogService.getActivity(activityId);
        if (!"ACTIVE".equals(activity.getStatus())) {
            throw new NotFoundException("Activity", activityId.toString());
        }

        Variant variant = new Variant();
        variant.setActivityId(activityId);
        variant.setTitle(form.getTitle().trim());
        variant.setDescription(form.getDescription());
        variant.setDifferenceReason(form.getDifferenceReason());
        variant.setExtraRequirements(form.getExtraRequirements());

        // Normalise tags
        List<String> tagNames = parseTags(form.getTags());
        for (String name : tagNames) {
            Tag tag = catalogService.findOrCreateTag(name, principal.getUserId());
            variant.addTag(tag.getId());
        }

        Variant saved = catalogService.createVariant(variant, principal.getUserId());
        return "redirect:/activities/" + activityId + "/variants/" + saved.getId();
    }

    // ---- Edit variant (Block 4) ----

    @GetMapping("/{variantId}/edit")
    public String editForm(@PathVariable UUID activityId,
                           @PathVariable UUID variantId,
                           @AuthenticationPrincipal PortalUserDetails principal,
                           Model model) {
        Variant variant = catalogService.getVariant(variantId);
        if (!variant.getActivityId().equals(activityId)) {
            throw new NotFoundException("Variant", variantId.toString());
        }
        // Only creator or admin can edit
        if (!variant.getCreatedBy().equals(principal.getUserId()) && !principal.isAdmin()) {
            throw new com.lep.portal.common.ForbiddenException("Только автор может редактировать вариант");
        }

        CreateVariantForm form = new CreateVariantForm();
        form.setTitle(variant.getTitle());
        form.setDescription(variant.getDescription());
        form.setDifferenceReason(variant.getDifferenceReason());
        form.setExtraRequirements(variant.getExtraRequirements());
        // Tags not pre-populated for simplicity

        model.addAttribute("form", form);
        model.addAttribute("activity", catalogService.getActivity(activityId));
        model.addAttribute("variant", variant);
        return "catalog/variant_form";
    }

    @PostMapping("/{variantId}")
    public String update(@PathVariable UUID activityId,
                         @PathVariable UUID variantId,
                         @Valid @ModelAttribute("form") CreateVariantForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal PortalUserDetails principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activity", catalogService.getActivity(activityId));
            model.addAttribute("variant", catalogService.getVariant(variantId));
            return "catalog/variant_form";
        }

        Variant updates = new Variant();
        updates.setTitle(form.getTitle().trim());
        updates.setDescription(form.getDescription());
        updates.setDifferenceReason(form.getDifferenceReason());
        updates.setExtraRequirements(form.getExtraRequirements());

        catalogService.updateVariant(variantId, updates, principal.getUserId(), principal.isAdmin());
        return "redirect:/activities/" + activityId + "/variants/" + variantId;
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
