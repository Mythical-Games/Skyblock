package org.allaymc.skyblock.player;

import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which island each player belongs to and their role.
 *
 * <p>All shared state is held in a {@link ConcurrentHashMap} for thread safety.
 * Persistence is delegated to {@link PersistenceLayer} via the plugin reference.</p>
 *
 * <p>Requirements: 10.1, 10.2, 10.3</p>
 */
public class PlayerDataManager {

    /** In-memory store: playerUUID → SkyblockPlayerData. */
    private final ConcurrentHashMap<UUID, SkyblockPlayerData> playerDataMap = new ConcurrentHashMap<>();

    private final SkyblockPlugin plugin;

    /**
     * Constructs a {@code PlayerDataManager} backed by the given plugin instance.
     *
     * @param plugin the owning {@link SkyblockPlugin}, used to access the persistence layer
     */
    public PlayerDataManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Core CRUD
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link SkyblockPlayerData} for the given player, or {@code null}
     * if no data has been loaded or set for that player.
     *
     * @param playerUUID the player's UUID
     * @return the player data, or {@code null}
     */
    public SkyblockPlayerData getPlayerData(UUID playerUUID) {
        return playerDataMap.get(playerUUID);
    }

    /**
     * Stores (or replaces) the {@link SkyblockPlayerData} for the given player
     * in the in-memory map. Does not persist to disk; call
     * {@link PersistenceLayer#savePlayerData} explicitly when persistence is needed.
     *
     * @param playerUUID the player's UUID
     * @param data       the data to store
     */
    public void setPlayerData(UUID playerUUID, SkyblockPlayerData data) {
        playerDataMap.put(playerUUID, data);
    }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the player has a non-null {@code islandOwnerUUID}
     * in their stored data, meaning they are associated with an island (as owner
     * or member).
     *
     * @param playerUUID the player's UUID
     * @return {@code true} if the player belongs to an island
     */
    public boolean hasIsland(UUID playerUUID) {
        SkyblockPlayerData data = playerDataMap.get(playerUUID);
        return data != null && data.getIslandOwnerUUID() != null;
    }

    /**
     * Returns the player's {@link IslandRole}, or {@code null} if the player is
     * not associated with any island.
     *
     * @param playerUUID the player's UUID
     * @return the role, or {@code null}
     */
    public IslandRole getRole(UUID playerUUID) {
        SkyblockPlayerData data = playerDataMap.get(playerUUID);
        return data != null ? data.getRole() : null;
    }

    /**
     * Returns the UUID of the island owner for the island this player belongs to,
     * or {@code null} if the player is not in any island.
     *
     * <p>For island owners this returns their own UUID; for members it returns the
     * owner's UUID.</p>
     *
     * @param playerUUID the player's UUID
     * @return the owner UUID, or {@code null}
     */
    public UUID getIslandOwner(UUID playerUUID) {
        SkyblockPlayerData data = playerDataMap.get(playerUUID);
        return data != null ? data.getIslandOwnerUUID() : null;
    }

    /**
     * Clears the island association for the given player by setting
     * {@code islandOwnerUUID} and {@code role} to {@code null}, then persists
     * the change to disk.
     *
     * <p>If no data exists for the player this method is a no-op.</p>
     *
     * @param playerUUID the player's UUID
     */
    public void clearIslandAssociation(UUID playerUUID) {
        SkyblockPlayerData data = playerDataMap.get(playerUUID);
        if (data == null) {
            return;
        }
        data.setIslandOwnerUUID(null);
        data.setRole(null);
        plugin.getPersistenceLayer().savePlayerData(data);
    }

    // -------------------------------------------------------------------------
    // Bulk load / save
    // -------------------------------------------------------------------------

    /**
     * Loads all persisted player data from disk via
     * {@link PersistenceLayer#loadAllPlayerData()} and populates the in-memory map.
     * Any existing entries are replaced.
     */
    public void loadAll() {
        List<SkyblockPlayerData> allData = plugin.getPersistenceLayer().loadAllPlayerData();
        for (SkyblockPlayerData data : allData) {
            if (data.getPlayerUUID() != null) {
                playerDataMap.put(data.getPlayerUUID(), data);
            }
        }
    }

    /**
     * Persists every entry in the in-memory map to disk by calling
     * {@link PersistenceLayer#savePlayerData(SkyblockPlayerData)} for each entry.
     */
    public void saveAll() {
        for (SkyblockPlayerData data : playerDataMap.values()) {
            plugin.getPersistenceLayer().savePlayerData(data);
        }
    }
}
