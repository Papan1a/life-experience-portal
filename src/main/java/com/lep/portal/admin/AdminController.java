package com.lep.portal.admin;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.ActivityRepository;
import com.lep.portal.catalog.Variant;
import com.lep.portal.catalog.VariantRepository;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ActivityRepository activityRepository;
    private final VariantRepository variantRepository;

    public AdminController(ActivityRepository activityRepository,
                           VariantRepository variantRepository) {
        this.activityRepository = activityRepository;
        this.variantRepository = variantRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        // Placeholder — full admin UI in Block 7
        model.addAttribute("allActivities", activityRepository.findAll());
        return "admin/dashboard";
    }

    @PostMapping("/activities/{id}/archive")
    public String archiveActivity(@PathVariable UUID id, RedirectAttributes ra) {
        Activity a = activityRepository.findById(id).orElseThrow();
        a.setStatus("ARCHIVED");
        activityRepository.save(a);
        ra.addFlashAttribute("success", "Активность архивирована");
        return "redirect:/admin";
    }

    @PostMapping("/activities/{id}/block")
    public String blockActivity(@PathVariable UUID id, RedirectAttributes ra) {
        Activity a = activityRepository.findById(id).orElseThrow();
        a.setStatus("BLOCKED");
        activityRepository.save(a);
        ra.addFlashAttribute("success", "Активность заблокирована");
        return "redirect:/admin";
    }

    @PostMapping("/activities/{id}/activate")
    public String activateActivity(@PathVariable UUID id, RedirectAttributes ra) {
        Activity a = activityRepository.findById(id).orElseThrow();
        a.setStatus("ACTIVE");
        activityRepository.save(a);
        ra.addFlashAttribute("success", "Активность активирована");
        return "redirect:/admin";
    }

    @PostMapping("/variants/{id}/archive")
    public String archiveVariant(@PathVariable UUID id, RedirectAttributes ra) {
        Variant v = variantRepository.findById(id).orElseThrow();
        v.setStatus("ARCHIVED");
        variantRepository.save(v);
        ra.addFlashAttribute("success", "Вариант архивирован");
        return "redirect:/admin";
    }
}
