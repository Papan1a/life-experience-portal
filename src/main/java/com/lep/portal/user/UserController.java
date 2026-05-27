package com.lep.portal.user;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.invite.InvalidInviteException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(value = "code", required = false) String inviteCode,
                               Model model) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return "register";
        }

        if (!model.containsAttribute("registrationForm")) {
            RegistrationForm form = new RegistrationForm();
            form.setInviteCode(inviteCode);
            model.addAttribute("registrationForm", form);
        }

        model.addAttribute("inviteCode", inviteCode);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request) {

        if (!form.isConsent()) {
            bindingResult.rejectValue("consent", "consent.required",
                    "Необходимо согласие на обработку данных");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.registrationForm", bindingResult);
            redirectAttributes.addFlashAttribute("registrationForm", form);
            return "redirect:/register?code=" + form.getInviteCode();
        }

        try {
            User user = userService.register(form);

            // Auto-login: build Authentication directly, no re-auth needed
            PortalUserDetails userDetails = new PortalUserDetails(user);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            HttpSession session = request.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    SecurityContextHolder.getContext());

            return "redirect:/";
        } catch (InvalidInviteException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("registrationForm", form);
            return "redirect:/register?code=" + form.getInviteCode();
        }
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal PortalUserDetails principal, Model model) {
        User user = userService.findActiveById(principal.getUserId());
        model.addAttribute("user", user);
        return "profile";
    }
}
