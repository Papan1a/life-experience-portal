package com.lep.portal.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends CrudRepository<Report, UUID> {

    @Query("SELECT * FROM reports ORDER BY created_at DESC")
    List<Report> findAllByOrderByCreatedAtDesc();

    @Query("SELECT * FROM reports WHERE status = :status ORDER BY created_at DESC")
    List<Report> findByStatus(@Param("status") String status);

    @Query("SELECT count(*) FROM reports WHERE status = :status")
    long countByStatus(@Param("status") String status);
}