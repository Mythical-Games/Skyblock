package org.allaymc.skyblock.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted metadata for a single Skyblock island.
 *
 * <p>The {@code members} map contains only players with the {@link IslandRole#MEMBER} role;
 * the owner is tracked separately via {@code ownerUUID}.</p>
 *
 * <p>A no-arg constructor is provided for Gson deserialization.</p>
 *
 * <p>Requirements: 2.4, 10.1</p>
 */
public class IslandMetadata {

    /** UUID of the island owner. */
    private UUID ownerUUID;

    /** Cached display name of the owner (used by the leaderboard). */
    private String ownerName;

    /** Custom display name for this island (shown in /is top). Defaults to ownerName if not set. */
    private String islandName;

    /** AllayMC world name, e.g. {@code "skyblock_<ownerUUID>"}. */
    private String worldName;

    /** Island spawn point coordinates. */
    private double spawnX;
    private double spawnY;
    private double spawnZ;

    /** Island spawn point orientation. */
    private float spawnYaw;
    private float spawnPitch;

    /** Accumulated block-value score for this island. */
    private long islandLevel;

    /**
     * Players who are members of this island (does NOT include the owner).
     * Key: member UUID, Value: always {@link IslandRole#MEMBER}.
     */
    private Map<UUID, IslandRole> members = new HashMap<>();

    /** Epoch-millisecond timestamp of when this island was created. */
    private long createdAt;

    /** No-arg constructor required by Gson. */
    public IslandMetadata() {}

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

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

    public String getIslandName() {
        return islandName;
    }

    public void setIslandName(String islandName) {
        this.islandName = islandName;
    }

    /** Returns the island display name, falling back to the owner name if not set. */
    public String getDisplayName() {
        return (islandName != null && !islandName.isBlank()) ? islandName : ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public void setSpawnX(double spawnX) {
        this.spawnX = spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public void setSpawnY(double spawnY) {
        this.spawnY = spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public void setSpawnZ(double spawnZ) {
        this.spawnZ = spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public void setSpawnYaw(float spawnYaw) {
        this.spawnYaw = spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public void setSpawnPitch(float spawnPitch) {
        this.spawnPitch = spawnPitch;
    }

    public long getIslandLevel() {
        return islandLevel;
    }

    public void setIslandLevel(long islandLevel) {
        this.islandLevel = islandLevel;
    }

    public Map<UUID, IslandRole> getMembers() {
        return members;
    }

    public void setMembers(Map<UUID, IslandRole> members) {
        this.members = members;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
