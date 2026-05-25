package com.lep.portal.invite;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.user.PortalUserDetails;

@Controller
@RequestMapping("/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public String listMyInvites(@AuthenticationPrincipal PortalUserDetails principal, Model model) {
        model.addAttribute("invites", inviteService.getMyInvites(principal.getUserId()));
        return "invites";
    }

    @PostMapping("/generate")
    public String generateInvite(@AuthenticationPrincipal PortalUserDetails principal,
                                 RedirectAttributes redirectAttributes) {
        Invite invite = inviteService.createInvite(principal.getUserId());
        redirectAttributes.addFlashAttribute("newInviteCode", invite.getCode());
        return "redirect:/invites";
    }

    @PostMapping("/revoke")
    public String revokeInvite(@AuthenticationPrincipal PortalUserDetails principal,
                               String inviteId,
                               RedirectAttributes redirectAttributes) {
        try {
            inviteService.revokeInvite(java.util.UUID.fromString(inviteId), principal.getUserId());
            redirectAttributes.addFlashAttribute("success", "Приглашение отозвано");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/invites";
    }
}
