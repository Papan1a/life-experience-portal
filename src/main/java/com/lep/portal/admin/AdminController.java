package com.lep.portal.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.ActivityRepository;
import com.lep.portal.catalog.Tag;
import com.lep.portal.catalog.TagRepository;
import com.lep.portal.catalog.Variant;
import com.lep.portal.catalog.VariantRepository;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ActivityRepository activityRepository;
    private final VariantRepository variantRepository;
    private final TagRepository tagRepository;

    public AdminController(ActivityRepository activityRepository,
                           VariantRepository variantRepository,
                           TagRepository tagRepository) {
        this.activityRepository = activityRepository;
        this.variantRepository = variantRepository;
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("allActivities", activityRepository.findAll());
        model.addAttribute("allVariants", variantRepository.findAll());
        model.addAttribute("allTags", tagRepository.findAllOrdered());
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

    @PostMapping("/tags/{id}/rename")
    public String renameTag(@PathVariable UUID id,
                            @RequestParam("name") String name,
                            RedirectAttributes ra) {
        Tag tag = tagRepository.findById(id).orElseThrow();
        String trimmed = name.trim();
        if (trimmed.isBlank()) {
            ra.addFlashAttribute("error", "Имя тега не может быть пустым");
            return "redirect:/admin";
        }
        String newSlug = normalizeSlug(trimmed);
        tag.setName(trimmed);
        tag.setSlug(newSlug);
        tagRepository.save(tag);
        ra.addFlashAttribute("success", "Тег переименован: " + trimmed);
        return "redirect:/admin";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reportsMessage", "Функция отчётов будет доступна в следующей версии.");
        return "admin/dashboard";
    }

    private String normalizeSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-zа-яё0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
