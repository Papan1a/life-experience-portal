package com.lep.portal.invite;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.user.PortalUserDetails;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public String listMyInvites(@AuthenticationPrincipal PortalUserDetails principal,
                                 HttpServletRequest request,
                                 Model model) {
        model.addAttribute("invites", inviteService.getMyInvites(principal.getUserId()));
        model.addAttribute("baseUrl", buildBaseUrl(request));
        model.addAttribute("activeCount", inviteService.countActiveInvites(principal.getUserId()));
        model.addAttribute("maxActive", 5);
        return "invite/list";
    }

    @PostMapping("/generate")
    public String generateInvite(@AuthenticationPrincipal PortalUserDetails principal,
                                 RedirectAttributes ra) {
        try {
            Invite invite = inviteService.createInvite(principal.getUserId());
            return "redirect:/invites/" + invite.getId();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/invites";
        }
    }

    @GetMapping("/{id}")
    public String showInvite(@AuthenticationPrincipal PortalUserDetails principal,
                             @PathVariable String id,
                             HttpServletRequest request,
                             Model model) {
        java.util.UUID inviteId = java.util.UUID.fromString(id);
        Invite invite = inviteService.getInvite(inviteId);
        String baseUrl = buildBaseUrl(request);
        model.addAttribute("invite", invite);
        model.addAttribute("registrationLink", baseUrl + "/register?code=" + invite.getCode());
        return "invite/show";
    }

    private String buildBaseUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        String scheme = request.getScheme();
        String host = request.getServerName();
        if ((scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    @PostMapping("/{id}/revoke")
    public String revokeInvite(@AuthenticationPrincipal PortalUserDetails principal,
                               @PathVariable String id,
                               RedirectAttributes redirectAttributes) {
        try {
            inviteService.revokeInvite(java.util.UUID.fromString(id), principal.getUserId());
            redirectAttributes.addFlashAttribute("success", "Приглашение отозвано");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invites";
    }
}
