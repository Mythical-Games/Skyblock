package org.allaymc.skyblock.island;

/**
 * Thrown when island creation fails, e.g. because the player already owns or is a member of an island.
 *
 * <p>Requirements: 2.6, 2.7</p>
 */
public class IslandCreationException extends IslandException {

    public IslandCreationException(String message) {
        super(message);
    }

    public IslandCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
