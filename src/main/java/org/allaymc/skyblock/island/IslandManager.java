package org.allaymc.skyblock.island;

import org.allaymc.api.item.ItemStack;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.player.Player;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.server.Server;
import org.allaymc.api.world.World;
import org.allaymc.api.world.WorldPool;
import org.allaymc.api.world.generator.WorldGenerator;
import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockConfig;
import org.allaymc.skyblock.model.SkyblockPlayerData;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages island lifecycle: creation, deletion, reset, and member management.
 *
 * <p>All shared state is held in a {@link ConcurrentHashMap} keyed by owner UUID.
 * Persistence is delegated to the plugin's {@link org.allaymc.skyblock.persistence.PersistenceLayer}.</p>
 *
 * <p>Requirements: 2.1, 2.2, 2.4, 2.6, 2.7</p>
 */
public class IslandManager {

    /** World name prefix for all Skyblock island worlds. */
    public static final String WORLD_NAME_PREFIX = "skyblock_";

    /** In-memory store: ownerUUID → IslandMetadata. */
    private final ConcurrentHashMap<UUID, IslandMetadata> islands = new ConcurrentHashMap<>();

    private final SkyblockPlugin plugin;

    /**
     * Constructs an {@code IslandManager} backed by the given plugin instance.
     *
     * @param plugin the owning {@link SkyblockPlugin}
     */
    public IslandManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Island creation
    // -------------------------------------------------------------------------

    /**
     * Creates a new island for the given player.
     *
     * <ol>
     *   <li>Checks that the player is not already in an island; throws {@link IslandCreationException} if so.</li>
     *   <li>Loads a new void world via the AllayMC {@link WorldPool}.</li>
     *   <li>Generates the starter platform and returns the spawn point.</li>
     *   <li>Builds and stores {@link IslandMetadata}; persists both metadata and player data.</li>
     * </ol>
     *
     * @param ownerUUID the UUID of the player who will own the island
     * @return the newly created {@link IslandMetadata}
     * @throws IslandCreationException if the player already owns or is a member of an island,
     *                                 or if world creation fails
     */
    public IslandMetadata createIsland(UUID ownerUUID) {
        // Requirement 2.6: reject if player already has an island
        if (plugin.getPlayerDataManager().hasIsland(ownerUUID)) {
            throw new IslandCreationException(
                    "Player " + ownerUUID + " already owns or is a member of an island.");
        }

        String worldName = WORLD_NAME_PREFIX + ownerUUID.toString();

        // Load a new void world
        World world;
        try {
            WorldPool worldPool = Server.getInstance().getWorldPool();

            // Build a void (empty) world generator — no noisers, no populators
            WorldGenerator voidGenerator = WorldGenerator.builder()
                    .name("void")
                    .build();

            // Derive the storage path from the server's world folder
            Path worldPath = worldPool.getWorldFolder().resolve(worldName);
            var storageFactory = Registries.WORLD_STORAGE_FACTORIES.get("LEVELDB");
            if (storageFactory == null) {
                throw new IslandCreationException(
                        "No 'LEVELDB' world storage factory registered; cannot create island world for " + ownerUUID);
            }
            var storage = storageFactory.apply(worldPath);

            worldPool.loadWorld(worldName, storage, voidGenerator, null, null);
            world = worldPool.getWorld(worldName);

            // Mark as runtime-only so it is never written to world-settings.yml on shutdown.
            // Island worlds are managed entirely by this plugin; they must not be auto-loaded
            // by AllayMC on the next server start.
            if (world != null) {
                world.setRuntimeOnly(true);
            }
        } catch (IslandCreationException e) {
            throw e;
        } catch (Exception e) {
            throw new IslandCreationException(
                    "Failed to create island world '" + worldName + "' for player " + ownerUUID, e);
        }

        if (world == null) {
            throw new IslandCreationException(
                    "Island world '" + worldName + "' was not found after loading for player " + ownerUUID);
        }

        // Generate the starter platform
        var dimension = world.getOverWorld();
        List<ItemStack> starterItems = buildStarterItems();
        Location3d spawnPoint;
        try {
            StarterPlatformGenerator generator = new StarterPlatformGenerator();
            spawnPoint = generator.generate(dimension, starterItems);
        } catch (Exception e) {
            // Clean up the world on failure
            try {
                Server.getInstance().getWorldPool().unloadWorld(worldName);
            } catch (Exception ignored) {
            }
            throw new IslandCreationException(
                    "Failed to generate starter platform for island '" + worldName + "'", e);
        }

        // Retrieve or create the owner's player data
        SkyblockPlayerData playerData = plugin.getPlayerDataManager().getPlayerData(ownerUUID);
        if (playerData == null) {
            playerData = new SkyblockPlayerData();
            playerData.setPlayerUUID(ownerUUID);
        }
        playerData.setIslandOwnerUUID(ownerUUID);
        playerData.setRole(IslandRole.OWNER);

        // Build island metadata
        IslandMetadata meta = new IslandMetadata();
        meta.setOwnerUUID(ownerUUID);
        meta.setOwnerName(playerData.getPlayerName() != null ? playerData.getPlayerName() : ownerUUID.toString());
        meta.setWorldName(worldName);
        meta.setSpawnX(spawnPoint.x());
        meta.setSpawnY(spawnPoint.y());
        meta.setSpawnZ(spawnPoint.z());
        meta.setSpawnYaw(0.0f);
        meta.setSpawnPitch(0.0f);
        meta.setIslandLevel(0L);
        meta.setCreatedAt(System.currentTimeMillis());

        // Store in memory
        islands.put(ownerUUID, meta);
        plugin.getPlayerDataManager().setPlayerData(ownerUUID, playerData);

        // Persist both
        plugin.getPersistenceLayer().saveIslandMetadata(meta);
        plugin.getPersistenceLayer().savePlayerData(playerData);

        return meta;
    }

    // -------------------------------------------------------------------------
    // Lookup helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link IslandMetadata} for the island owned by {@code ownerUUID},
     * or {@code null} if no such island exists.
     *
     * @param ownerUUID the owner's UUID
     * @return the metadata, or {@code null}
     */
    public IslandMetadata getIslandByOwner(UUID ownerUUID) {
        return islands.get(ownerUUID);
    }

    /**
     * Returns the {@link IslandMetadata} whose {@code worldName} matches the given name,
     * or {@code null} if no island uses that world.
     *
     * @param worldName the AllayMC world name (e.g. {@code "skyblock_<uuid>"})
     * @return the metadata, or {@code null}
     */
    public IslandMetadata getIslandByWorld(String worldName) {
        for (IslandMetadata meta : islands.values()) {
            if (worldName.equals(meta.getWorldName())) {
                return meta;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the player is currently inside any Skyblock island world
     * (i.e. the world name of their current dimension starts with {@value #WORLD_NAME_PREFIX}).
     *
     * @param player the player to check
     * @return {@code true} if the player is in a Skyblock island world
     */
    public boolean isInsideIslandWorld(Player player) {
        var entity = player.getControlledEntity();
        if (entity == null) return false;
        var dimension = entity.getDimension();
        if (dimension == null) return false;
        var world = dimension.getWorld();
        if (world == null) return false;
        return world.getName().startsWith(WORLD_NAME_PREFIX);
    }

    /**
     * Returns {@code true} if the player is currently inside their own island world specifically.
     *
     * @param player the player to check
     * @return {@code true} if the player is in their own island world
     */
    public boolean isInsideOwnIslandWorld(Player player) {
        var entity = player.getControlledEntity();
        if (entity == null) return false;
        var dimension = entity.getDimension();
        if (dimension == null) return false;
        var world = dimension.getWorld();
        if (world == null) return false;
        UUID ownerUUID = plugin.getPlayerDataManager().getIslandOwner(entity.getUniqueId());
        if (ownerUUID == null) return false;
        String expectedWorldName = WORLD_NAME_PREFIX + ownerUUID.toString();
        // Only the owner's own island world counts
        return expectedWorldName.equals(world.getName())
                && ownerUUID.equals(entity.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Island deletion and reset
    // -------------------------------------------------------------------------

    /**
     * Deletes the island owned by the given player.
     *
     * <ol>
     *   <li>Looks up the island by {@code ownerUUID}; throws {@link IslandException} if not found.</li>
     *   <li>Clears island associations for all members via {@link org.allaymc.skyblock.player.PlayerDataManager#clearIslandAssociation(UUID)}.</li>
     *   <li>Clears the owner's island association.</li>
     *   <li>Removes the island from the in-memory map.</li>
     *   <li>Deletes the persisted {@link IslandMetadata}.</li>
     *   <li>Unloads the island world via the AllayMC {@link WorldPool}.</li>
     * </ol>
     *
     * @param ownerUUID the UUID of the island owner
     * @throws IslandException if the island is not found or the world cannot be unloaded
     * Requirements: 4.1
     */
    public void deleteIsland(UUID ownerUUID) {
        IslandMetadata meta = islands.get(ownerUUID);
        if (meta == null) {
            throw new IslandException("Island not found for owner: " + ownerUUID);
        }

        String worldName = meta.getWorldName();

        // Clear island associations for all members
        for (UUID memberUUID : new java.util.ArrayList<>(meta.getMembers().keySet())) {
            plugin.getPlayerDataManager().clearIslandAssociation(memberUUID);
        }

        // Clear the owner's island association
        plugin.getPlayerDataManager().clearIslandAssociation(ownerUUID);

        // Remove from in-memory map
        islands.remove(ownerUUID);

        // Delete persisted metadata
        plugin.getPersistenceLayer().deleteIslandMetadata(ownerUUID);

        // Unload the island world and delete its folder from disk
        try {
            Server.getInstance().getWorldPool().unloadWorld(worldName);
        } catch (Exception e) {
            throw new IslandException("Failed to unload island world '" + worldName + "' for owner " + ownerUUID, e);
        }

        // Wait for the world to fully stop (LevelDB releases file locks asynchronously).
        // Poll until WorldPool no longer knows about this world, then delete the folder.
        Thread.ofVirtual().name("island-delete-" + ownerUUID).start(() -> {
            WorldPool worldPool = Server.getInstance().getWorldPool();
            long deadline = System.currentTimeMillis() + 10_000; // 10 s max wait
            while (worldPool.getWorld(worldName) != null && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            Path worldFolder = worldPool.getWorldFolder().resolve(worldName);
            try {
                deleteDirectoryRecursively(worldFolder);
                plugin.getPluginLogger().info("Deleted island world folder '{}' for owner {}.", worldFolder, ownerUUID);
            } catch (Exception e) {
                plugin.getPluginLogger().warn(
                        "Could not delete island world folder '{}' for owner {}: {}",
                        worldFolder, ownerUUID, e.getMessage());
            }
        });
    }

    /**
     * Recursively deletes a directory and all its contents.
     * Does nothing if the path does not exist.
     */
    private void deleteDirectoryRecursively(Path path) throws java.io.IOException {
        if (!java.nio.file.Files.exists(path)) return;
        try (var stream = java.nio.file.Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try {
                          java.nio.file.Files.delete(p);
                      } catch (java.io.IOException e) {
                          throw new RuntimeException("Failed to delete " + p, e);
                      }
                  });
        }
    }

    /**
     * Resets the island owned by the given player to its initial state.
     *
     * <ol>
     *   <li>Looks up the island by {@code ownerUUID}; throws {@link IslandException} if not found.</li>
     *   <li>Retrieves the island world and its overworld dimension.</li>
     *   <li>Clears all blocks in all loaded chunks by setting them to air.</li>
     *   <li>Regenerates the starter platform via {@link StarterPlatformGenerator#generate}.</li>
     *   <li>Resets {@code islandLevel} to 0 and updates the spawn point fields.</li>
     *   <li>Persists the updated metadata.</li>
     * </ol>
     *
     * @param ownerUUID the UUID of the island owner
     * @throws IslandException if the island is not found or the world cannot be accessed
     * Requirements: 4.2
     */
    public void resetIsland(UUID ownerUUID) {
        IslandMetadata meta = islands.get(ownerUUID);
        if (meta == null) {
            throw new IslandException("Island not found for owner: " + ownerUUID);
        }

        String worldName = meta.getWorldName();

        // Retrieve the world and its overworld dimension
        World world;
        try {
            world = Server.getInstance().getWorldPool().getWorld(worldName);
        } catch (Exception e) {
            throw new IslandException("Failed to retrieve island world '" + worldName + "' for owner " + ownerUUID, e);
        }

        if (world == null) {
            throw new IslandException("Island world '" + worldName + "' is not loaded for owner " + ownerUUID);
        }

        var dimension = world.getOverWorld();

        // Clear all blocks in all loaded chunks by setting them to air
        var airState = org.allaymc.api.block.type.BlockTypes.AIR.getDefaultState();
        // Use standard Bedrock Edition overworld height range (-64 to 320)
        int minY = -64;
        int maxY = 320;

        try {
            dimension.getChunkManager().getLoadedChunks().forEach(chunk -> {
                int chunkBaseX = chunk.getX() << 4;
                int chunkBaseZ = chunk.getZ() << 4;
                for (int x = chunkBaseX; x < chunkBaseX + 16; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = chunkBaseZ; z < chunkBaseZ + 16; z++) {
                            dimension.setBlockState(x, y, z, airState, 0, false, false, false, null);
                        }
                    }
                }
            });
        } catch (Exception e) {
            throw new IslandException("Failed to clear blocks in island world '" + worldName + "' for owner " + ownerUUID, e);
        }

        // Regenerate the starter platform
        List<ItemStack> starterItems = buildStarterItems();
        Location3d spawnPoint;
        try {
            StarterPlatformGenerator generator = new StarterPlatformGenerator();
            spawnPoint = generator.generate(dimension, starterItems);
        } catch (Exception e) {
            throw new IslandException("Failed to regenerate starter platform in island world '" + worldName + "' for owner " + ownerUUID, e);
        }

        // Reset island level to 0 and update spawn point
        meta.setIslandLevel(0L);
        meta.setSpawnX(spawnPoint.x());
        meta.setSpawnY(spawnPoint.y());
        meta.setSpawnZ(spawnPoint.z());
        meta.setSpawnYaw(0.0f);
        meta.setSpawnPitch(0.0f);

        // Persist the updated metadata
        plugin.getPersistenceLayer().saveIslandMetadata(meta);
    }

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    /**
     * Adds a player as a {@link IslandRole#MEMBER} of the specified island.
     *
     * <ol>
     *   <li>Looks up the island by {@code islandOwnerUUID}; throws {@link IslandException} if not found.</li>
     *   <li>Throws {@link IslandException} if the member is already in the island's member map.</li>
     *   <li>Adds the member to {@link IslandMetadata#members} with role {@code MEMBER}.</li>
     *   <li>Updates (or creates) the member's {@link SkyblockPlayerData} with the owner UUID and {@code MEMBER} role.</li>
     *   <li>Persists both the island metadata and the player data.</li>
     * </ol>
     *
     * @param islandOwnerUUID the UUID of the island owner
     * @param memberUUID      the UUID of the player to add as a member
     * @throws IslandException if the island is not found or the player is already a member
     * @see org.allaymc.skyblock.model.IslandMetadata#getMembers()
     * Requirements: 5.2, 6.1
     */
    public void addMember(UUID islandOwnerUUID, UUID memberUUID) {
        IslandMetadata meta = islands.get(islandOwnerUUID);
        if (meta == null) {
            throw new IslandException("Island not found for owner: " + islandOwnerUUID);
        }
        if (meta.getMembers().containsKey(memberUUID)) {
            throw new IslandException("Player " + memberUUID + " is already a member of island owned by " + islandOwnerUUID);
        }

        // Add to island metadata
        meta.getMembers().put(memberUUID, IslandRole.MEMBER);

        // Update the member's player data
        SkyblockPlayerData memberData = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (memberData == null) {
            memberData = new SkyblockPlayerData();
            memberData.setPlayerUUID(memberUUID);
        }
        memberData.setIslandOwnerUUID(islandOwnerUUID);
        memberData.setRole(IslandRole.MEMBER);
        plugin.getPlayerDataManager().setPlayerData(memberUUID, memberData);

        // Persist both
        plugin.getPersistenceLayer().saveIslandMetadata(meta);
        plugin.getPersistenceLayer().savePlayerData(memberData);
    }

    /**
     * Removes a player from the specified island's member list.
     *
     * <ol>
     *   <li>Looks up the island by {@code islandOwnerUUID}; throws {@link IslandException} if not found.</li>
     *   <li>Throws {@link IslandException} if the player is not currently a member of the island.</li>
     *   <li>Removes the member from {@link IslandMetadata#members}.</li>
     *   <li>Calls {@link org.allaymc.skyblock.player.PlayerDataManager#clearIslandAssociation(UUID)}
     *       which clears the player's island reference and persists the player data.</li>
     *   <li>Persists the updated island metadata.</li>
     * </ol>
     *
     * @param islandOwnerUUID the UUID of the island owner
     * @param memberUUID      the UUID of the player to remove
     * @throws IslandException if the island is not found or the player is not a member
     * Requirements: 6.1, 6.2
     */
    public void removeMember(UUID islandOwnerUUID, UUID memberUUID) {
        IslandMetadata meta = islands.get(islandOwnerUUID);
        if (meta == null) {
            throw new IslandException("Island not found for owner: " + islandOwnerUUID);
        }
        if (!meta.getMembers().containsKey(memberUUID)) {
            throw new IslandException("Player " + memberUUID + " is not a member of island owned by " + islandOwnerUUID);
        }

        // Remove from island metadata
        meta.getMembers().remove(memberUUID);

        // Clear the member's island association (also persists player data)
        plugin.getPlayerDataManager().clearIslandAssociation(memberUUID);

        // Persist updated island metadata
        plugin.getPersistenceLayer().saveIslandMetadata(meta);
    }

    /**
     * Updates the spawn point of the specified island.
     *
     * <p>Extracts the x, y, z, yaw, and pitch values from the given {@link Location3d}
     * and stores them in the island's {@link IslandMetadata}, then persists the change.</p>
     *
     * @param islandOwnerUUID the UUID of the island owner
     * @param location        the new spawn point location
     * @throws IslandException if the island is not found
     * Requirements: 3.2
     */
    public void setSpawnPoint(UUID islandOwnerUUID, Location3d location) {
        IslandMetadata meta = islands.get(islandOwnerUUID);
        if (meta == null) {
            throw new IslandException("Island not found for owner: " + islandOwnerUUID);
        }

        meta.setSpawnX(location.x);
        meta.setSpawnY(location.y);
        meta.setSpawnZ(location.z);
        meta.setSpawnYaw((float) location.yaw);
        meta.setSpawnPitch((float) location.pitch);

        plugin.getPersistenceLayer().saveIslandMetadata(meta);
    }

    // -------------------------------------------------------------------------
    // Bulk load / save
    // -------------------------------------------------------------------------

    /**
     * Loads all persisted island metadata from disk and populates the in-memory map.
     * Called from {@code onEnable}.
     *
     * <p>Each island world that is already loaded (because it was in world-settings.yml
     * from a previous run before runtime-only was set) is marked runtime-only so it
     * won't be persisted to world-settings.yml again on the next shutdown.</p>
     */
    public void loadAll() {
        var allMeta = plugin.getPersistenceLayer().loadAllIslandMetadata();
        for (IslandMetadata meta : allMeta) {
            if (meta.getOwnerUUID() != null) {
                islands.put(meta.getOwnerUUID(), meta);

                // If the world was loaded by AllayMC at startup (old entry in world-settings.yml),
                // mark it runtime-only now so it won't be re-added on the next shutdown save.
                var world = Server.getInstance().getWorldPool().getWorld(meta.getWorldName());
                if (world != null) {
                    world.setRuntimeOnly(true);
                }
            }
        }
    }

    /**
     * Ensures the island world for the given owner UUID is loaded.
     * If the world is already loaded, this is a no-op.
     * If the world folder exists on disk but is not loaded, it is loaded now and
     * marked runtime-only so it won't be written to world-settings.yml.
     *
     * <p>Called when a player joins and already has an island, so that
     * {@code /is home} and block-permission checks work immediately.</p>
     *
     * @param ownerUUID the island owner's UUID
     */
    public void ensureIslandWorldLoaded(UUID ownerUUID) {
        IslandMetadata meta = islands.get(ownerUUID);
        if (meta == null) return;

        String worldName = meta.getWorldName();
        WorldPool worldPool = Server.getInstance().getWorldPool();

        // Already loaded — nothing to do
        if (worldPool.getWorld(worldName) != null) return;

        // World folder must exist on disk
        Path worldPath = worldPool.getWorldFolder().resolve(worldName);
        if (!java.nio.file.Files.exists(worldPath)) {
            plugin.getPluginLogger().warn(
                    "Island world folder '{}' not found on disk for owner {}; skipping load.",
                    worldPath, ownerUUID);
            return;
        }

        try {
            var storageFactory = Registries.WORLD_STORAGE_FACTORIES.get("LEVELDB");
            if (storageFactory == null) {
                plugin.getPluginLogger().error("LEVELDB storage factory not registered; cannot load island world.");
                return;
            }
            var storage = storageFactory.apply(worldPath);

            WorldGenerator voidGenerator = WorldGenerator.builder().name("void").build();
            worldPool.loadWorld(worldName, storage, voidGenerator, null, null);

            var world = worldPool.getWorld(worldName);
            if (world != null) {
                world.setRuntimeOnly(true);
                plugin.getPluginLogger().info("Loaded island world '{}' for owner {}.", worldName, ownerUUID);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().error(
                    "Failed to load island world '{}' for owner {}: {}", worldName, ownerUUID, e.getMessage());
        }
    }
    /**
     * Persists every entry in the in-memory map to disk.
     * Called from {@code onDisable}.
     */
    public void saveAll() {
        for (IslandMetadata meta : islands.values()) {
            plugin.getPersistenceLayer().saveIslandMetadata(meta);
        }
    }

    /**
     * Returns an unmodifiable view of all loaded island metadata values.
     *
     * @return collection of all island metadata
     */
    public Collection<IslandMetadata> getAllIslands() {
        return islands.values();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Converts the configured {@link SkyblockConfig.StarterItem} list into actual
     * {@link ItemStack} instances. Returns an empty list if the config or starter items
     * are not yet initialised.
     */
    private List<ItemStack> buildStarterItems() {
        SkyblockConfig config = plugin.getSkyblockConfig();
        if (config == null || config.getStarterItems() == null) {
            return new ArrayList<>();
        }

        List<ItemStack> result = new ArrayList<>();
        for (SkyblockConfig.StarterItem si : config.getStarterItems()) {
            if (si == null || si.getItemTypeId() == null) continue;
            var itemType = Registries.ITEMS.get(
                    new org.allaymc.api.utils.identifier.Identifier(si.getItemTypeId()));
            if (itemType == null) continue;
            var stack = itemType.createItemStack(si.getCount());
            result.add(stack);
        }
        return result;
    }
}
