package com.lep.portal.invite;

/**
 * Computed status of an Invite (not stored in DB).
 * Derived from used_at, revoked_at, expires_at.
 */
public enum InviteStatus {
    ACTIVE,
    USED,
    EXPIRED,
    REVOKED
}
