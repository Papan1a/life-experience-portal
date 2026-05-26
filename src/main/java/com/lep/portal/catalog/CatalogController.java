package com.lep.portal.catalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.lep.portal.catalog.CatalogService.CatalogPage;

@Controller
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
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
                          Model model) {

        List<String> tagSlugs = (tags != null && !tags.isBlank())
                ? Arrays.asList(tags.split(","))
                : Collections.emptyList();

        CatalogPage catalogPage = catalogService.getCatalogPage(category, tagSlugs, page);

        model.addAttribute("categories", catalogService.getAllCategories());
        model.addAttribute("page", catalogPage);
        model.addAttribute("currentCategory", category);
        model.addAttribute("currentTags", tags);

        // HTMX: return content fragment only, no layout
        if ("true".equals(hxRequest)) {
            return "catalog/index :: catalog-content";
        }

        return "catalog/index";
    }
}
