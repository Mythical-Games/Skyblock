package org.allaymc.skyblock.model;

import java.util.UUID;

/**
 * Represents a single entry in the island leaderboard.
 * Holds the island owner's UUID, display name, and current level score.
 */
public class LeaderboardEntry {

    private UUID ownerUUID;
    private String ownerName;
    private long level;

    /** No-arg constructor required by Gson. */
    public LeaderboardEntry() {}

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public long getLevel() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
    }
}
