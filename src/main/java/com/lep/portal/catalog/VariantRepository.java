package com.lep.portal.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface VariantRepository extends CrudRepository<Variant, UUID> {

    @Query("SELECT * FROM visible_variants WHERE activity_id = :activityId ORDER BY created_at")
    List<Variant> findVisibleByActivity(@Param("activityId") UUID activityId);

    @Query("SELECT * FROM variants WHERE activity_id = :activityId AND deleted_at IS NULL ORDER BY created_at")
    List<Variant> findByActivity(@Param("activityId") UUID activityId);

    @Query("SELECT * FROM variants WHERE created_by = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    List<Variant> findByCreatedBy(@Param("userId") UUID userId);
}
