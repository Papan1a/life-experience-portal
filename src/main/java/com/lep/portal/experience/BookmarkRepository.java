package com.lep.portal.experience;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends CrudRepository<Bookmark, UUID> {

    @Query("""
        SELECT * FROM bookmarks
        WHERE user_id = :userId AND activity_id = :activityId
          AND variant_id IS NOT DISTINCT FROM :variantId
    """)
    Optional<Bookmark> findByUserActivityVariant(
            @Param("userId") UUID userId,
            @Param("activityId") UUID activityId,
            @Param("variantId") UUID variantId);

    @Query("SELECT * FROM bookmarks WHERE user_id = :userId ORDER BY created_at DESC")
    List<Bookmark> findByUserId(@Param("userId") UUID userId);

    @Query("""
        DELETE FROM bookmarks
        WHERE user_id = :userId AND activity_id = :activityId
          AND variant_id IS NOT DISTINCT FROM :variantId
    """)
    void deleteByUserActivityVariant(
            @Param("userId") UUID userId,
            @Param("activityId") UUID activityId,
            @Param("variantId") UUID variantId);
}
