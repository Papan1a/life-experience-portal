package com.lep.portal.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {

        if (!form.isConsent()) {
            bindingResult.rejectValue("consent", "consent.required", "Необходимо согласие на обработку данных");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.registrationForm", bindingResult);
            redirectAttributes.addFlashAttribute("registrationForm", form);
            return "redirect:/register";
        }

        try {
            userService.register(form);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("registrationForm", form);
            return "redirect:/register";
        }

        return "redirect:/login?registered";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal PortalUserDetails principal, Model model) {
        User user = userService.findActiveById(principal.getUserId());
        model.addAttribute("user", user);
        return "profile";
    }
}
