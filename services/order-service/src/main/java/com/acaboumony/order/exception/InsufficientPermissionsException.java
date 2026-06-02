package com.acaboumony.order.exception;

/**
 * Thrown when a user attempts to access a resource they are not authorized to view or modify.
 */
public class InsufficientPermissionsException extends OrderServiceException {

    public InsufficientPermissionsException() {
        super("INSUFFICIENT_PERMISSIONS", "You do not have permission to perform this action");
    }
}
