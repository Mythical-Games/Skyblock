package org.allaymc.skyblock.level;

import org.allaymc.api.server.Server;
import org.allaymc.api.world.World;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.SkyblockConfig;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.util.UUID;

/**
 * Scans an island world and computes the block-value score.
 *
 * <p>The service iterates all loaded chunks in the island's overworld dimension,
 * sums the configured block values for every non-air block, persists the result
 * in {@link IslandMetadata}, and notifies {@link LeaderboardService}.</p>
 *
 * <p>Requirements: 8.2, 8.3, 8.4, 9.2</p>
 */
public class IslandLevelService {

    /** Standard Bedrock Edition overworld minimum Y (inclusive). */
    private static final int MIN_Y = -64;

    /** Standard Bedrock Edition overworld maximum Y (inclusive). */
    private static final int MAX_Y = 320;

    private final IslandManager islandManager;
    private final SkyblockConfig config;
    private final LeaderboardService leaderboardService;
    private final PersistenceLayer persistenceLayer;

    /**
     * Constructs an {@code IslandLevelService}.
     *
     * @param islandManager      the island manager used to look up metadata and worlds
     * @param config             the Skyblock configuration holding block values
     * @param leaderboardService the leaderboard service to notify after each computation
     * @param persistenceLayer   the persistence layer used to save updated island metadata
     */
    public IslandLevelService(
            IslandManager islandManager,
            SkyblockConfig config,
            LeaderboardService leaderboardService,
            PersistenceLayer persistenceLayer) {
        this.islandManager = islandManager;
        this.config = config;
        this.leaderboardService = leaderboardService;
        this.persistenceLayer = persistenceLayer;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans all loaded chunks in the island world owned by {@code islandOwnerUUID},
     * computes the Island Level as the sum of block values for every non-air block,
     * persists the updated level in {@link IslandMetadata}, and updates the leaderboard.
     *
     * <p>Air blocks (identifier {@code "minecraft:air"}) contribute 0 and are skipped
     * for efficiency. Any block type not present in the configured block-value table
     * also contributes 0 (Requirement 8.4).</p>
     *
     * @param islandOwnerUUID the UUID of the island owner
     * @return the computed island level
     * @throws IllegalArgumentException if no island metadata is found for the given UUID
     * @throws IllegalStateException    if the island world is not currently loaded
     */
    public long computeAndSave(UUID islandOwnerUUID) {
        // Retrieve island metadata
        IslandMetadata meta = islandManager.getIslandByOwner(islandOwnerUUID);
        if (meta == null) {
            throw new IllegalArgumentException(
                    "No island found for owner: " + islandOwnerUUID);
        }

        // Retrieve the island world and its overworld dimension
        String worldName = meta.getWorldName();
        World world = Server.getInstance().getWorldPool().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException(
                    "Island world '" + worldName + "' is not loaded for owner " + islandOwnerUUID);
        }

        var dimension = world.getOverWorld();

        // Iterate all loaded chunks and sum block values
        long totalLevel = 0L;

        for (var chunk : dimension.getChunkManager().getLoadedChunks()) {
            int chunkBaseX = chunk.getX() << 4;
            int chunkBaseZ = chunk.getZ() << 4;

            for (int x = chunkBaseX; x < chunkBaseX + 16; x++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    for (int z = chunkBaseZ; z < chunkBaseZ + 16; z++) {
                        var blockState = dimension.getBlockState(x, y, z, 0);
                        String blockTypeId = blockState.getBlockType().getIdentifier().toString();

                        // Air contributes 0; skip for efficiency
                        if ("minecraft:air".equals(blockTypeId)) {
                            continue;
                        }

                        int value = getBlockValue(blockTypeId);
                        if (value != 0) {
                            totalLevel += value;
                        }
                    }
                }
            }
        }

        // Persist the updated island level
        meta.setIslandLevel(totalLevel);
        persistenceLayer.saveIslandMetadata(meta);

        // Update the leaderboard (Requirement 9.2)
        leaderboardService.update(islandOwnerUUID, meta.getOwnerName(), totalLevel);

        return totalLevel;
    }

    /**
     * Returns the configured point value for the given block type identifier.
     * Delegates to {@link SkyblockConfig#getBlockValue(String)}, which returns 0
     * for any block type not present in the configured table (Requirement 8.4).
     *
     * @param blockTypeId the fully-qualified block type identifier (e.g. {@code "minecraft:stone"})
     * @return the configured point value, or 0 if the block type is unknown
     */
    public int getBlockValue(String blockTypeId) {
        return config.getBlockValue(blockTypeId);
    }
}
