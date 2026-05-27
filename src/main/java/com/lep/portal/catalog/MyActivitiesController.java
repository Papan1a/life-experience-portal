package com.lep.portal.catalog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.lep.portal.user.PortalUserDetails;

@Controller
@RequestMapping("/my-activities")
public class MyActivitiesController {

    private final CatalogService catalogService;

    public MyActivitiesController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public String myActivities(Authentication auth, Model model) {
        UUID userId = ((PortalUserDetails) auth.getPrincipal()).getUserId();
        List<Activity> activities = catalogService.getActivitiesByUser(userId);

        Map<UUID, List<Variant>> variantsByActivity = activities.stream()
                .collect(Collectors.toMap(
                        Activity::getId,
                        a -> catalogService.getVariantsByUser(userId).stream()
                                .filter(v -> v.getActivityId().equals(a.getId()))
                                .toList()
                ));

        // Варианты к чужим активностям
        List<Variant> myVariants = catalogService.getVariantsByUser(userId);
        List<Variant> variantsOnOthers = myVariants.stream()
                .filter(v -> activities.stream().noneMatch(a -> a.getId().equals(v.getActivityId())))
                .toList();

        model.addAttribute("activities", activities);
        model.addAttribute("variantsByActivity", variantsByActivity);
        model.addAttribute("variantsOnOthers", variantsOnOthers);
        return "catalog/my_activities";
    }
}
