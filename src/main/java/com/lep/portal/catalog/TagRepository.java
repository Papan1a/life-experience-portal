package com.lep.portal.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends CrudRepository<Tag, UUID> {

    Optional<Tag> findBySlug(String slug);

    @Query("SELECT * FROM tags WHERE slug IN (:slugs)")
    List<Tag> findBySlugIn(@Param("slugs") List<String> slugs);

    @Query("SELECT * FROM tags WHERE lower(name) LIKE lower('%' || :query || '%') ORDER BY name LIMIT 20")
    List<Tag> searchByName(@Param("query") String query);

    @Query("SELECT * FROM tags WHERE slug LIKE CAST(:prefix AS text) || '%' ORDER BY slug LIMIT 10")
    List<Tag> findBySlugPrefix(@Param("prefix") String prefix);

    @Query("SELECT * FROM tags ORDER BY name")
    List<Tag> findAllOrdered();
}
