package com.lep.portal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.lep.portal.catalog.Activity;
import com.lep.portal.catalog.ActivityRepository;
import com.lep.portal.catalog.Category;
import com.lep.portal.catalog.CategoryRepository;
import com.lep.portal.catalog.TagRepository;
import com.lep.portal.catalog.Variant;
import com.lep.portal.catalog.VariantRepository;
import com.lep.portal.experience.Bookmark;
import com.lep.portal.experience.BookmarkRepository;
import com.lep.portal.experience.UserExperience;
import com.lep.portal.experience.UserExperienceRepository;
import com.lep.portal.invite.InviteRepository;
import com.lep.portal.user.User;
import com.lep.portal.user.UserRepository;

@Testcontainers
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ComponentScan(basePackages = "com.lep.portal")
public class RepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("leptest")
            .withUsername("leptest")
            .withPassword("leptest");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private VariantRepository variantRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InviteRepository inviteRepository;
    @Autowired
    private UserExperienceRepository userExperienceRepository;
    @Autowired
    private BookmarkRepository bookmarkRepository;

    private User adminUser;
    private Category category;

    @BeforeEach
    void setUp() {
        // Schema is managed by Flyway (V1__init.sql); only seed a test user here.
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setPasswordHash("argon2hash");
        adminUser.setDisplayName("Admin");
        adminUser.setAdmin(true);
        adminUser = userRepository.save(adminUser);

        category = categoryRepository.findAllActive().get(0);
    }

    // ---- T1.1 ----
    @Test
    @DisplayName("T1.1 — Category: find all 9 categories after migration")
    void findAllCategories() {
        List<Category> categories = categoryRepository.findAllActive();
        assertThat(categories).hasSize(9);
    }

    // ---- T1.2 ----
    @Test
    @DisplayName("T1.2 — Activity: create -> find by id -> status=ACTIVE")
    void createAndFindActivity() {
        Activity a = new Activity();
        a.setTitle("Test Activity");
        a.setDescription("Test description");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a.setStatus("ACTIVE");

        Activity saved = activityRepository.save(a);
        assertThat(saved.getId()).isNotNull();

        Optional<Activity> found = activityRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
    }

    // ---- T1.3 ----
    @Test
    @DisplayName("T1.3 — Variant: create with activity_id -> find -> FK")
    void createAndFindVariant() {
        Activity a = new Activity();
        a.setTitle("Activity for Variant");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a = activityRepository.save(a);

        Variant v = new Variant();
        v.setActivityId(a.getId());
        v.setTitle("Test Variant");
        v.setCreatedBy(adminUser.getId());
        v = variantRepository.save(v);

        assertThat(v.getId()).isNotNull();
        assertThat(v.getActivityId()).isEqualTo(a.getId());

        List<Variant> variants = variantRepository.findByActivity(a.getId());
        assertThat(variants).hasSize(1);
    }

    // ---- T1.4 ----
    @Test
    @DisplayName("T1.4 — Tag: duplicate slug -> constraint violation")
    void duplicateTagSlugThrows() {
        com.lep.portal.catalog.Tag tag1 = new com.lep.portal.catalog.Tag();
        tag1.setSlug("water");
        tag1.setName("Water");
        tag1.setCreatedBy(adminUser.getId());
        tagRepository.save(tag1);

        com.lep.portal.catalog.Tag tag2 = new com.lep.portal.catalog.Tag();
        tag2.setSlug("water");
        tag2.setName("water");
        tag2.setCreatedBy(adminUser.getId());

        assertThatThrownBy(() -> tagRepository.save(tag2))
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- T1.5 ----
    @Test
    @DisplayName("T1.5 — UserExperience: activity-level and variant-level coexist")
    void userExperienceBothLevelsExist() {
        Activity a = new Activity();
        a.setTitle("Experience Activity");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a = activityRepository.save(a);

        Variant v = new Variant();
        v.setActivityId(a.getId());
        v.setTitle("Experience Variant");
        v.setCreatedBy(adminUser.getId());
        v = variantRepository.save(v);

        UserExperience uxActivity = new UserExperience();
        uxActivity.setUserId(adminUser.getId());
        uxActivity.setActivityId(a.getId());
        uxActivity.setVariantId(null);
        uxActivity.setStatus("WANT_TO_TRY");
        userExperienceRepository.save(uxActivity);

        UserExperience uxVariant = new UserExperience();
        uxVariant.setUserId(adminUser.getId());
        uxVariant.setActivityId(a.getId());
        uxVariant.setVariantId(v.getId());
        uxVariant.setStatus("TRIED");
        userExperienceRepository.save(uxVariant);

        List<UserExperience> all = userExperienceRepository.findByUserId(adminUser.getId());
        assertThat(all).hasSize(2);
    }

    // ---- T1.6 ----
    @Test
    @DisplayName("T1.6 — UserExperience: duplicate -> constraint violation")
    void userExperienceDuplicateThrows() {
        Activity a = new Activity();
        a.setTitle("Dup Activity");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a = activityRepository.save(a);

        UserExperience ux1 = new UserExperience();
        ux1.setUserId(adminUser.getId());
        ux1.setActivityId(a.getId());
        ux1.setVariantId(null);
        ux1.setStatus("WANT_TO_TRY");
        userExperienceRepository.save(ux1);

        UserExperience ux2 = new UserExperience();
        ux2.setUserId(adminUser.getId());
        ux2.setActivityId(a.getId());
        ux2.setVariantId(null);
        ux2.setStatus("INTERESTING");

        assertThatThrownBy(() -> userExperienceRepository.save(ux2))
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- T1.7 ----
    @Test
    @DisplayName("T1.7 — Bookmark: create and duplicate constraint")
    void bookmarkCreateAndDuplicate() {
        Activity a = new Activity();
        a.setTitle("Bookmark Activity");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a = activityRepository.save(a);

        Bookmark b1 = new Bookmark();
        b1.setUserId(adminUser.getId());
        b1.setActivityId(a.getId());
        b1.setVariantId(null);
        bookmarkRepository.save(b1);

        List<Bookmark> bookmarks = bookmarkRepository.findByUserId(adminUser.getId());
        assertThat(bookmarks).hasSize(1);

        Bookmark b2 = new Bookmark();
        b2.setUserId(adminUser.getId());
        b2.setActivityId(a.getId());
        b2.setVariantId(null);

        assertThatThrownBy(() -> bookmarkRepository.save(b2))
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- T1.8 ----
    @Test
    @DisplayName("T1.8 — Soft delete: activity deleted_at -> not in visible_activities")
    void softDeleteHidesActivity() {
        Activity a = new Activity();
        a.setTitle("To Be Deleted");
        a.setCategoryId(category.getId());
        a.setCreatedBy(adminUser.getId());
        a.setStatus("ACTIVE");
        a = activityRepository.save(a);

        List<Activity> visible = activityRepository.findAllVisible();
        assertThat(visible).extracting(Activity::getId).contains(a.getId());

        a.setDeletedAt(Instant.now());
        activityRepository.save(a);

        List<Activity> visibleAfter = activityRepository.findAllVisible();
        assertThat(visibleAfter).extracting(Activity::getId).doesNotContain(a.getId());
    }
}
