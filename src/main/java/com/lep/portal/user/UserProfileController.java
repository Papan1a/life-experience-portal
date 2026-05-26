package com.lep.portal.user;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lep.portal.experience.ExperienceService;
import com.lep.portal.experience.ProfileExperienceItem;

import net.coobird.thumbnailator.Thumbnails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Controller
public class UserProfileController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final ExperienceService experienceService;
    private final S3Client s3Client;

    @Value("${r2.bucket:}")
    private String r2Bucket;

    @Value("${app.avatar-max-bytes:2097152}")
    private long avatarMaxBytes;

    public UserProfileController(UserService userService,
                                 UserRepository userRepository,
                                 ExperienceService experienceService,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false) S3Client s3Client) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.experienceService = experienceService;
        this.s3Client = s3Client;
    }

    // ---- 6.1: Public profiles ----

    @GetMapping("/users/{id}")
    public String viewProfile(@PathVariable UUID id,
                              @AuthenticationPrincipal PortalUserDetails principal,
                              Model model) {
        User profileUser = userService.findActiveById(id);
        Map<String, List<ProfileExperienceItem>> experiences =
                experienceService.getProfileExperiences(id);

        model.addAttribute("profileUser", profileUser);
        model.addAttribute("experiences", experiences);
        model.addAttribute("isOwnProfile", principal.getUserId().equals(id));

        return "user/profile";
    }

    // ---- 6.2: User search (HTMX) ----

    @GetMapping("/users/search")
    public String searchUsers(@RequestParam("q") String query, Model model) {
        List<User> users = userRepository.searchByDisplayName(query.trim());
        model.addAttribute("users", users);
        model.addAttribute("query", query);
        return "user/search_results";
    }

    // ---- 6.5: Edit own profile ----

    @GetMapping("/profile/edit")
    public String editProfileForm(@AuthenticationPrincipal PortalUserDetails principal,
                                   Model model) {
        User user = userService.findActiveById(principal.getUserId());
        model.addAttribute("user", user);
        return "user/edit";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal PortalUserDetails principal,
                                @RequestParam String displayName,
                                @RequestParam(required = false) String bio,
                                RedirectAttributes redirectAttributes) {
        if (displayName == null || displayName.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Имя не может быть пустым");
            return "redirect:/profile/edit";
        }

        userService.updateProfile(principal.getUserId(), displayName.trim(), bio);
        redirectAttributes.addFlashAttribute("success", "Профиль обновлён");
        return "redirect:/profile";
    }

    // ---- 6.4: Avatar upload ----

    @PostMapping("/profile/avatar")
    public String uploadAvatar(@AuthenticationPrincipal PortalUserDetails principal,
                               @RequestParam("avatar") MultipartFile file,
                               RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Файл не выбран");
            return "redirect:/profile/edit";
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            redirectAttributes.addFlashAttribute("error", "Разрешены только JPEG и PNG");
            return "redirect:/profile/edit";
        }

        // Validate size
        if (file.getSize() > avatarMaxBytes) {
            redirectAttributes.addFlashAttribute("error",
                    "Файл слишком большой (макс. " + (avatarMaxBytes / 1024 / 1024) + " MB)");
            return "redirect:/profile/edit";
        }

        if (s3Client == null) {
            redirectAttributes.addFlashAttribute("error", "Хранилище аватаров не настроено");
            return "redirect:/profile/edit";
        }

        try {
            byte[] resized = resizeAvatar(file.getBytes(), 256, 256);

            // Upload to R2
            String key = "avatars/" + principal.getUserId();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(r2Bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(resized));

            // Build avatar URL
            String avatarUrl = "/r2/" + key;

            userService.updateAvatar(principal.getUserId(), avatarUrl);
            redirectAttributes.addFlashAttribute("success", "Аватар обновлён");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка обработки изображения: " + e.getMessage());
        }

        return "redirect:/profile/edit";
    }

    private byte[] resizeAvatar(byte[] original, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(original))
                .size(width, height)
                .outputFormat("JPEG")
                .toOutputStream(out);
        return out.toByteArray();
    }
}
