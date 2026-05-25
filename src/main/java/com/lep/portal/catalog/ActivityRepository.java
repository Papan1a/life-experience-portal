package com.lep.portal.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends CrudRepository<Activity, UUID> {

    @Query("SELECT * FROM visible_activities ORDER BY created_at DESC")
    List<Activity> findAllVisible();

    @Query("SELECT * FROM visible_activities WHERE category_id = :categoryId ORDER BY created_at DESC")
    List<Activity> findVisibleByCategory(@Param("categoryId") UUID categoryId);

    @Query("""
        SELECT a.* FROM activities a
        JOIN activity_tags at ON at.activity_id = a.id
        WHERE at.tag_id = :tagId
          AND a.status = 'ACTIVE' AND a.deleted_at IS NULL
        ORDER BY a.created_at DESC
    """)
    List<Activity> findVisibleByTag(@Param("tagId") UUID tagId);

    @Query("SELECT * FROM activities WHERE created_by = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Activity> findByCreatedBy(@Param("userId") UUID userId);

    @Query("SELECT COUNT(*) > 0 FROM activities WHERE lower(title) = lower(:title) AND deleted_at IS NULL")
    boolean titleExistsIgnoreCase(@Param("title") String title);

    @Query("SELECT * FROM activities WHERE lower(title) = lower(:title) AND deleted_at IS NULL")
    List<Activity> findByTitleIgnoreCase(@Param("title") String title);
}
