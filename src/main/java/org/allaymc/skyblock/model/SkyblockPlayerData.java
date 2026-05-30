package org.allaymc.skyblock.model;

import java.util.UUID;

/**
 * Stores per-player Skyblock state: which island the player belongs to and their role.
 *
 * <p>{@code islandOwnerUUID} and {@code role} are {@code null} when the player is not
 * associated with any island.</p>
 *
 * <p>Requirements: 10.1</p>
 */
public class SkyblockPlayerData {

    private UUID playerUUID;
    private String playerName;
    /** Null when the player is not in any island. */
    private UUID islandOwnerUUID;
    /** Null when the player is not in any island. */
    private IslandRole role;

    /** No-arg constructor required by Gson. */
    public SkyblockPlayerData() {}

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public UUID getIslandOwnerUUID() {
        return islandOwnerUUID;
    }

    public void setIslandOwnerUUID(UUID islandOwnerUUID) {
        this.islandOwnerUUID = islandOwnerUUID;
    }

    public IslandRole getRole() {
        return role;
    }

    public void setRole(IslandRole role) {
        this.role = role;
    }
}
