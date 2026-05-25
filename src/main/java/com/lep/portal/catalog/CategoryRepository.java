package com.lep.portal.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface CategoryRepository extends CrudRepository<Category, UUID> {

    @Query("SELECT * FROM categories WHERE is_active = true ORDER BY sort_order")
    List<Category> findAllActive();

    Optional<Category> findBySlug(String slug);
}
