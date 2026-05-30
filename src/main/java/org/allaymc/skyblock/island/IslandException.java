package org.allaymc.skyblock.island;

/**
 * Base unchecked exception for island-related errors.
 *
 * <p>Requirements: 2.1, 2.6, 2.7</p>
 */
public class IslandException extends RuntimeException {

    public IslandException(String message) {
        super(message);
    }

    public IslandException(String message, Throwable cause) {
        super(message, cause);
    }
}
