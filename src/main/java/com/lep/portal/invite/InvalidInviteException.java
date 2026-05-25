package com.lep.portal.invite;

/**
 * Thrown when an invite code is invalid, expired, or revoked.
 */
public class InvalidInviteException extends RuntimeException {

    public InvalidInviteException(String message) {
        super(message);
    }
}
