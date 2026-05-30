package com.lep.portal.catalog;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTMX endpoint for tag autocomplete.
 * Returns an HTML fragment (<datalist> or <ul>) with matching tags.
 */
@Controller
@RequestMapping("/tags")
public class TagController {

    private final CatalogService catalogService;

    public TagController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/suggest")
    public String suggest(@RequestParam(value = "q", required = false, defaultValue = "") String query, Model model) {
        List<Tag> tags = catalogService.getTagsByPrefix(query);
        model.addAttribute("suggestions", tags);
        return "catalog/_tag_suggestions";
    }
}
