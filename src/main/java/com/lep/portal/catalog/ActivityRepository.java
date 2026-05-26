package com.lep.portal.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends CrudRepository<Activity, UUID> {

    // ---- visible_activities view queries ----

    @Query("SELECT * FROM visible_activities ORDER BY created_at DESC")
    List<Activity> findAllVisible();

    @Query("SELECT * FROM visible_activities ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<Activity> findAllVisiblePaginated(@Param("limit") int limit, @Param("offset") long offset);

    @Query("SELECT count(*) FROM visible_activities")
    long countVisible();

    @Query("SELECT * FROM visible_activities WHERE id = :id")
    Optional<Activity> findByIdFromVisible(@Param("id") UUID id);

    @Query("SELECT * FROM visible_activities WHERE category_id = :categoryId ORDER BY created_at DESC")
    List<Activity> findVisibleByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT * FROM visible_activities WHERE category_id = :categoryId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<Activity> findVisibleByCategoryPaginated(@Param("categoryId") UUID categoryId,
                                                   @Param("limit") int limit,
                                                   @Param("offset") long offset);

    @Query("SELECT count(*) FROM visible_activities WHERE category_id = :categoryId")
    long countVisibleByCategory(@Param("categoryId") UUID categoryId);

    // ---- tag filtering (AND-style: activity must have ALL specified tags) ----

    /**
     * Uses a subquery approach — finds activity IDs that have all given tags,
     * then selects those activities. Avoids GROUP BY a.* which PG 18 rejects.
     */
    @Query("""
        SELECT * FROM visible_activities
        WHERE id IN (
            SELECT at.activity_id FROM activity_tags at
            WHERE at.tag_id IN (:tagIds)
            GROUP BY at.activity_id
            HAVING COUNT(DISTINCT at.tag_id) = :tagCount
        )
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    List<Activity> findVisibleByTags(@Param("tagIds") List<UUID> tagIds,
                                     @Param("tagCount") long tagCount,
                                     @Param("limit") int limit,
                                     @Param("offset") long offset);

    @Query("""
        SELECT count(*) FROM (
            SELECT at.activity_id FROM activity_tags at
            WHERE at.tag_id IN (:tagIds)
            GROUP BY at.activity_id
            HAVING COUNT(DISTINCT at.tag_id) = :tagCount
        ) sub
    """)
    long countVisibleByTags(@Param("tagIds") List<UUID> tagIds,
                            @Param("tagCount") long tagCount);

    // Legacy — single-tag lookup (used elsewhere)
    @Query("""
        SELECT a.* FROM visible_activities a
        JOIN activity_tags at ON at.activity_id = a.id
        WHERE at.tag_id = :tagId
        ORDER BY a.created_at DESC
    """)
    List<Activity> findVisibleByTag(@Param("tagId") UUID tagId);

    // ---- similar activities ----

    @Query("""
        SELECT * FROM visible_activities
        WHERE category_id = :categoryId AND id != :excludeId
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    List<Activity> findSimilar(@Param("excludeId") UUID excludeId,
                               @Param("categoryId") UUID categoryId,
                               @Param("limit") int limit);

    // ---- user-scoped queries ----

    @Query("SELECT * FROM activities WHERE created_by = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Activity> findByCreatedBy(@Param("userId") UUID userId);

    @Query("SELECT COUNT(*) > 0 FROM activities WHERE lower(title) = lower(:title) AND deleted_at IS NULL")
    boolean titleExistsIgnoreCase(@Param("title") String title);

    @Query("SELECT * FROM activities WHERE lower(title) = lower(:title) AND deleted_at IS NULL")
    List<Activity> findByTitleIgnoreCase(@Param("title") String title);
}
