package com.lep.portal.experience;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface UserExperienceRepository extends CrudRepository<UserExperience, UUID> {

    @Query("""
        SELECT * FROM user_experiences
        WHERE user_id = :userId AND activity_id = :activityId
          AND variant_id IS NOT DISTINCT FROM :variantId
    """)
    Optional<UserExperience> findByUserActivityVariant(
            @Param("userId") UUID userId,
            @Param("activityId") UUID activityId,
            @Param("variantId") UUID variantId);

    @Query("SELECT * FROM user_experiences WHERE user_id = :userId ORDER BY created_at DESC")
    List<UserExperience> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT * FROM user_experiences WHERE user_id = :userId AND status = :status ORDER BY updated_at DESC")
    List<UserExperience> findByUserIdAndStatus(
            @Param("userId") UUID userId, @Param("status") ExperienceStatus status);

    @Modifying
    @Query("""
        DELETE FROM user_experiences
        WHERE user_id = :userId AND activity_id = :activityId
          AND variant_id IS NOT DISTINCT FROM :variantId
    """)
    void deleteByUserActivityVariant(
            @Param("userId") UUID userId,
            @Param("activityId") UUID activityId,
            @Param("variantId") UUID variantId);

    @Query("""
        SELECT ux.* FROM user_experiences ux
        JOIN visible_activities a ON a.id = ux.activity_id
        WHERE ux.user_id != :excludeUserId
        ORDER BY ux.created_at DESC LIMIT :limit
    """)
    List<UserExperience> findRecentOtherUsers(
            @Param("excludeUserId") UUID excludeUserId,
            @Param("limit") int limit);
}
