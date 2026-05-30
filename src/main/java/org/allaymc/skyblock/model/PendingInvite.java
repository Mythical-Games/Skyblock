package org.allaymc.skyblock.model;

import java.util.UUID;

/**
 * Represents a pending island invitation sent from an island owner to a target player.
 * Invitations expire 60 seconds after creation.
 */
public class PendingInvite {

    private UUID ownerUUID;
    private UUID targetUUID;
    private long expiresAt;

    /**
     * Creates a new pending invite that expires in 60 seconds.
     *
     * @param ownerUUID  the UUID of the island owner sending the invite
     * @param targetUUID the UUID of the player being invited
     */
    public PendingInvite(UUID ownerUUID, UUID targetUUID) {
        this.ownerUUID = ownerUUID;
        this.targetUUID = targetUUID;
        this.expiresAt = System.currentTimeMillis() + 60_000;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
