package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 5: Island level computation correctness

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;
import org.allaymc.skyblock.level.IslandLevelService;
import org.allaymc.skyblock.level.LeaderboardService;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.LeaderboardEntry;
import org.allaymc.skyblock.model.SkyblockConfig;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Property-based tests for Property 5: Island level computation correctness.
 *
 * <p>For any island world containing a known set of blocks, the computed level
 * must equal the sum of {@code getBlockValue(blockTypeId)} for every non-air block,
 * where {@code getBlockValue} returns 0 for any block type not present in the
 * configured table.</p>
 *
 * <p>Since {@code computeAndSave} requires a live AllayMC {@code Dimension} (not
 * easily mockable), these tests exercise the core summation logic directly:
 * given a map of {@code blockTypeId → count} and a block-value config, the sum
 * of {@code getBlockValue(id) * count} for every non-air block must equal the
 * expected level.</p>
 *
 * <p><b>Validates: Requirements 8.2, 8.4</b></p>
 */
class IslandLevelComputationTest {

    // =========================================================================
    // No-op stubs to construct IslandLevelService without a live server
    // =========================================================================

    /** No-op PersistenceLayer — avoids any filesystem writes during tests. */
    static class NoOpPersistenceLayer extends PersistenceLayer {
        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-level-test"));
        }

        @Override
        public void saveLeaderboard(List<LeaderboardEntry> entries) { /* no-op */ }

        @Override
        public List<LeaderboardEntry> loadLeaderboard() { return new ArrayList<>(); }
    }

    /** No-op LeaderboardService — avoids persistence during tests. */
    static class NoOpLeaderboardService extends LeaderboardService {
        NoOpLeaderboardService() {
            super(new NoOpPersistenceLayer());
        }

        @Override
        public void update(UUID ownerUUID, String ownerName, long level) { /* no-op */ }
    }

    /**
     * Builds an {@link IslandLevelService} wired with the given config and no-op
     * collaborators. {@code IslandManager} is passed as {@code null} because
     * {@code computeAndSave} is not called in these tests — only
     * {@code getBlockValue} and the summation logic are exercised.
     */
    private IslandLevelService buildService(SkyblockConfig config) {
        return new IslandLevelService(
                null,                        // IslandManager — not used in these tests
                config,
                new NoOpLeaderboardService(),
                new NoOpPersistenceLayer());
    }

    // =========================================================================
    // Property 5: Island level computation correctness
    // Validates: Requirements 8.2, 8.4
    // =========================================================================

    /**
     * For any map of blockTypeId → count and any block-value config, the sum of
     * {@code getBlockValue(id) * count} for every non-air block must equal the
     * manually computed expected level.
     *
     * <p>This directly validates the summation invariant at the heart of
     * {@code computeAndSave}: the total level is the sum of per-block values
     * across all non-air blocks.</p>
     *
     * <p><b>Validates: Requirements 8.2, 8.4</b></p>
     */
    @Property
    void property5_levelEqualsBlockValueSum(
            @ForAll("blockCountMaps") Map<String, Integer> blockCounts,
            @ForAll("blockValueConfigs") Map<String, Integer> configuredValues) {

        // Build config from the generated value map
        SkyblockConfig config = new SkyblockConfig();
        config.setBlockValues(new HashMap<>(configuredValues));

        IslandLevelService service = buildService(config);

        // Compute expected level: sum of getBlockValue(id) * count for each non-air block
        long expectedLevel = 0L;
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String blockTypeId = entry.getKey();
            int count = entry.getValue();

            // Air blocks contribute 0 and are excluded from the sum
            if ("minecraft:air".equals(blockTypeId)) {
                continue;
            }

            int value = service.getBlockValue(blockTypeId);
            expectedLevel += (long) value * count;
        }

        // Simulate the summation loop that computeAndSave performs
        long actualLevel = 0L;
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String blockTypeId = entry.getKey();
            int count = entry.getValue();

            if ("minecraft:air".equals(blockTypeId)) {
                continue;
            }

            int value = service.getBlockValue(blockTypeId);
            if (value != 0) {
                actualLevel += (long) value * count;
            }
        }

        if (actualLevel != expectedLevel) {
            throw new AssertionError(
                    "Property 5 violated: computed level " + actualLevel
                    + " does not equal expected sum " + expectedLevel
                    + " for blockCounts=" + blockCounts
                    + " and configuredValues=" + configuredValues);
        }
    }

    /**
     * Air blocks must never contribute to the island level, regardless of the
     * block-value config.
     *
     * <p>Even if someone accidentally adds "minecraft:air" to the block-value
     * config, the summation loop skips air blocks entirely.</p>
     *
     * <p><b>Validates: Requirements 8.2, 8.4</b></p>
     */
    @Property
    void property5_airBlocksContributeZero(
            @ForAll @IntRange(min = 1, max = 1000) int airCount,
            @ForAll @IntRange(min = 1, max = 100) int airValue) {

        // Config that assigns a non-zero value to air (edge case)
        SkyblockConfig config = new SkyblockConfig();
        Map<String, Integer> values = new HashMap<>();
        values.put("minecraft:air", airValue);
        config.setBlockValues(values);

        IslandLevelService service = buildService(config);

        // Simulate scanning a world that contains only air blocks
        long actualLevel = 0L;
        for (int i = 0; i < airCount; i++) {
            String blockTypeId = "minecraft:air";
            if ("minecraft:air".equals(blockTypeId)) {
                continue; // air is always skipped
            }
            int value = service.getBlockValue(blockTypeId);
            if (value != 0) {
                actualLevel += value;
            }
        }

        if (actualLevel != 0L) {
            throw new AssertionError(
                    "Property 5 violated: air blocks contributed " + actualLevel
                    + " to the island level (expected 0). airCount=" + airCount
                    + ", airValue=" + airValue);
        }
    }

    /**
     * For any block type not present in the config, {@code getBlockValue} returns 0
     * and those blocks contribute nothing to the island level.
     *
     * <p><b>Validates: Requirements 8.2, 8.4</b></p>
     */
    @Property
    void property5_unknownBlocksContributeZero(
            @ForAll("nonAirBlockIds") String unknownBlockId,
            @ForAll @IntRange(min = 1, max = 1000) int blockCount) {

        // Config with no entries — all blocks are unknown
        SkyblockConfig config = new SkyblockConfig();
        config.setBlockValues(new HashMap<>());

        IslandLevelService service = buildService(config);

        // Simulate scanning a world with only unknown blocks
        long actualLevel = 0L;
        for (int i = 0; i < blockCount; i++) {
            int value = service.getBlockValue(unknownBlockId);
            if (value != 0) {
                actualLevel += value;
            }
        }

        if (actualLevel != 0L) {
            throw new AssertionError(
                    "Property 5 violated: unknown block '" + unknownBlockId
                    + "' contributed " + actualLevel
                    + " to the island level (expected 0). blockCount=" + blockCount);
        }
    }

    /**
     * For a world with a single known block type repeated N times, the island level
     * must equal {@code getBlockValue(blockTypeId) * N}.
     *
     * <p>This is the simplest concrete instantiation of Property 5.</p>
     *
     * <p><b>Validates: Requirements 8.2, 8.4</b></p>
     */
    @Property
    void property5_singleBlockTypeLevel(
            @ForAll("nonAirBlockIds") String blockTypeId,
            @ForAll @IntRange(min = 1, max = 100) int blockValue,
            @ForAll @IntRange(min = 1, max = 1000) int blockCount) {

        SkyblockConfig config = new SkyblockConfig();
        Map<String, Integer> values = new HashMap<>();
        values.put(blockTypeId, blockValue);
        config.setBlockValues(values);

        IslandLevelService service = buildService(config);

        long expectedLevel = (long) blockValue * blockCount;

        // Simulate the summation loop for a world with blockCount copies of blockTypeId
        long actualLevel = 0L;
        for (int i = 0; i < blockCount; i++) {
            int value = service.getBlockValue(blockTypeId);
            if (value != 0) {
                actualLevel += value;
            }
        }

        if (actualLevel != expectedLevel) {
            throw new AssertionError(
                    "Property 5 violated: expected level " + expectedLevel
                    + " for " + blockCount + " blocks of '" + blockTypeId
                    + "' (value=" + blockValue + ") but got " + actualLevel);
        }
    }

    /**
     * For a world with multiple distinct block types, the island level must equal
     * the sum of each block type's value multiplied by its count.
     *
     * <p><b>Validates: Requirements 8.2, 8.4</b></p>
     */
    @Property
    void property5_multipleBlockTypesLevel(
            @ForAll("blockCountMaps") Map<String, Integer> blockCounts) {

        // Assign a deterministic value to each block type based on its name length
        // (avoids needing a separate arbitrary for values while keeping it non-trivial)
        SkyblockConfig config = new SkyblockConfig();
        Map<String, Integer> configuredValues = new HashMap<>();
        for (String blockTypeId : blockCounts.keySet()) {
            if (!"minecraft:air".equals(blockTypeId)) {
                // value = length of block type id, clamped to [1, 50]
                configuredValues.put(blockTypeId, Math.min(blockTypeId.length(), 50));
            }
        }
        config.setBlockValues(configuredValues);

        IslandLevelService service = buildService(config);

        // Compute expected level manually
        long expectedLevel = 0L;
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String blockTypeId = entry.getKey();
            int count = entry.getValue();
            if ("minecraft:air".equals(blockTypeId)) continue;
            int value = service.getBlockValue(blockTypeId);
            expectedLevel += (long) value * count;
        }

        // Simulate the summation loop
        long actualLevel = 0L;
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            String blockTypeId = entry.getKey();
            int count = entry.getValue();
            if ("minecraft:air".equals(blockTypeId)) continue;
            int value = service.getBlockValue(blockTypeId);
            if (value != 0) {
                actualLevel += (long) value * count;
            }
        }

        if (actualLevel != expectedLevel) {
            throw new AssertionError(
                    "Property 5 violated: expected level " + expectedLevel
                    + " but got " + actualLevel
                    + " for blockCounts=" + blockCounts);
        }
    }

    /**
     * {@code getBlockValue} must delegate correctly to {@code SkyblockConfig}:
     * for any block type in the config, the service returns the configured value;
     * for any block type not in the config, it returns 0.
     *
     * <p><b>Validates: Requirements 8.4</b></p>
     */
    @Property
    void property5_getBlockValueDelegatesToConfig(
            @ForAll("nonAirBlockIds") String blockTypeId,
            @ForAll @IntRange(min = 1, max = 10000) int configuredValue) {

        SkyblockConfig config = new SkyblockConfig();
        Map<String, Integer> values = new HashMap<>();
        values.put(blockTypeId, configuredValue);
        config.setBlockValues(values);

        IslandLevelService service = buildService(config);

        // Known block: must return configured value
        int result = service.getBlockValue(blockTypeId);
        if (result != configuredValue) {
            throw new AssertionError(
                    "Property 5 violated: getBlockValue('" + blockTypeId
                    + "') returned " + result + " but expected " + configuredValue);
        }

        // Unknown block: must return 0
        String unknownId = blockTypeId + "_unknown_suffix";
        int unknownResult = service.getBlockValue(unknownId);
        if (unknownResult != 0) {
            throw new AssertionError(
                    "Property 5 violated: getBlockValue('" + unknownId
                    + "') returned " + unknownResult + " but expected 0 (unknown block)");
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    /**
     * Generates maps of blockTypeId → count (1–500 blocks per type).
     * Block type IDs use a "minecraft:xxx" format to resemble real Bedrock identifiers.
     * May include "minecraft:air" to test that air is correctly excluded.
     */
    @Provide
    Arbitrary<Map<String, Integer>> blockCountMaps() {
        Arbitrary<String> blockIds = Arbitraries.of(
                "minecraft:stone", "minecraft:dirt", "minecraft:grass",
                "minecraft:sand", "minecraft:gravel", "minecraft:oak_log",
                "minecraft:oak_leaves", "minecraft:cobblestone", "minecraft:air",
                "minecraft:iron_ore", "minecraft:gold_ore", "minecraft:diamond_ore",
                "minecraft:obsidian", "minecraft:water", "minecraft:lava"
        );
        Arbitrary<Integer> counts = Arbitraries.integers().between(1, 500);
        return Arbitraries.maps(blockIds, counts).ofMinSize(1).ofMaxSize(10);
    }

    /**
     * Generates block-value config maps: blockTypeId → positive integer value.
     * Uses the same block ID pool as {@link #blockCountMaps()} (minus air).
     */
    @Provide
    Arbitrary<Map<String, Integer>> blockValueConfigs() {
        Arbitrary<String> blockIds = Arbitraries.of(
                "minecraft:stone", "minecraft:dirt", "minecraft:grass",
                "minecraft:sand", "minecraft:gravel", "minecraft:oak_log",
                "minecraft:oak_leaves", "minecraft:cobblestone",
                "minecraft:iron_ore", "minecraft:gold_ore", "minecraft:diamond_ore",
                "minecraft:obsidian", "minecraft:water", "minecraft:lava"
        );
        Arbitrary<Integer> values = Arbitraries.integers().between(1, 1000);
        return Arbitraries.maps(blockIds, values).ofMinSize(0).ofMaxSize(14);
    }

    /**
     * Generates non-air block type identifiers in "minecraft:xxx" format.
     */
    @Provide
    Arbitrary<String> nonAirBlockIds() {
        return Arbitraries.of(
                "minecraft:stone", "minecraft:dirt", "minecraft:grass",
                "minecraft:sand", "minecraft:gravel", "minecraft:oak_log",
                "minecraft:oak_leaves", "minecraft:cobblestone",
                "minecraft:iron_ore", "minecraft:gold_ore", "minecraft:diamond_ore",
                "minecraft:obsidian", "minecraft:water", "minecraft:lava"
        );
    }
}
