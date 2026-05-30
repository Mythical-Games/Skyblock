package org.allaymc.skyblock.persistence;

/**
 * Unchecked exception thrown when a persistence operation fails after all retries.
 *
 * <p>Requirements: 10.2, 10.4</p>
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
