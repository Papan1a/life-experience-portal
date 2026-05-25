package com.lep.portal.common;

import org.springframework.data.jdbc.core.mapping.AggregateReference;

/**
 * Helper to create AggregateReference instances for cross-aggregate FK links.
 * In Spring Data JDBC, use AggregateReference.to(id) for foreign-key references.
 * <p>
 * Usage: {@code AggregateReference.to(activityId)}
 * </p>
 */
public final class AggregateReferences {

    private AggregateReferences() {
    }

    public static <T, ID> AggregateReference<T, ID> ref(ID id) {
        return AggregateReference.to(id);
    }
}
