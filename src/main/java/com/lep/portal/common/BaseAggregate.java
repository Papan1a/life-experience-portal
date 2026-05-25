package com.lep.portal.common;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;

/**
 * Base aggregate root with UUIDv7 primary key.
 * id is NULL on insert — the DB generates uuid_generate_v7() as DEFAULT.
 * Spring Data JDBC treats NULL-id rows as new, avoiding isNew() ambiguity.
 */
public abstract class BaseAggregate implements Persistable<UUID> {

    @Id
    protected UUID id;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return id == null;
    }
}
