package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 6: Leaderboard ordering invariant

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.allaymc.skyblock.level.LeaderboardService;
import org.allaymc.skyblock.model.LeaderboardEntry;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Property-based tests for Property 6: Leaderboard ordering invariant.
 *
 * <p>For any collection of islands with distinct level values,
 * {@code LeaderboardService.getTop10()} must return a list that is:
 * <ol>
 *   <li>Sorted in descending order by {@code level}.</li>
 *   <li>Contains at most 10 entries.</li>
 *   <li>Contains only islands whose levels are among the highest in the collection.</li>
 * </ol>
 *
 * <p><b>Validates: Requirements 9.1, 9.3</b></p>
 */
class LeaderboardOrderingTest {

    // =========================================================================
    // No-op PersistenceLayer — avoids any filesystem writes during tests
    // =========================================================================

    static class NoOpPersistenceLayer extends PersistenceLayer {
        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-leaderboard-test"));
        }

        @Override
        public void saveLeaderboard(List<LeaderboardEntry> entries) {
            // no-op: do not write to disk during property tests
        }

        @Override
        public List<LeaderboardEntry> loadLeaderboard() {
            return new ArrayList<>();
        }
    }

    // =========================================================================
    // Helper: build a fresh LeaderboardService with a no-op persistence layer
    // =========================================================================

    private LeaderboardService newService() {
        return new LeaderboardService(new NoOpPersistenceLayer());
    }

    // =========================================================================
    // Property 6: Leaderboard ordering invariant
    // Validates: Requirements 9.1, 9.3
    // =========================================================================

    /**
     * For any list of islands with distinct level values, {@code getTop10()} must
     * return a list sorted descending by level and containing at most 10 entries.
     *
     * <p><b>Validates: Requirements 9.1, 9.3</b></p>
     */
    @Property
    void property6_top10IsSortedDescendingAndAtMost10(
            @ForAll("distinctLevelIslands") List<IslandInput> islands) {

        LeaderboardService service = newService();

        // Feed all islands into the leaderboard
        for (IslandInput island : islands) {
            service.update(island.uuid, island.name, island.level);
        }

        List<LeaderboardEntry> top10 = service.getTop10();

        // Invariant 1: at most 10 entries
        if (top10.size() > 10) {
            throw new AssertionError(
                    "Property 6 violated: getTop10() returned " + top10.size()
                    + " entries (expected at most 10).");
        }

        // Invariant 2: sorted descending by level
        for (int i = 0; i < top10.size() - 1; i++) {
            long current = top10.get(i).getLevel();
            long next = top10.get(i + 1).getLevel();
            if (current < next) {
                throw new AssertionError(
                        "Property 6 violated: leaderboard is not sorted descending at index "
                        + i + ". Entry[" + i + "].level=" + current
                        + " < Entry[" + (i + 1) + "].level=" + next);
            }
        }

        // Invariant 3: if there are more than 10 islands, only the top 10 by level are present
        if (islands.size() > 10) {
            // Compute the 10th-highest level among all islands
            List<Long> allLevels = new ArrayList<>();
            for (IslandInput island : islands) {
                allLevels.add(island.level);
            }
            allLevels.sort(Collections.reverseOrder());
            long tenthHighestLevel = allLevels.get(9); // 0-indexed: 10th entry

            // Every entry in top10 must have level >= tenthHighestLevel
            for (LeaderboardEntry entry : top10) {
                if (entry.getLevel() < tenthHighestLevel) {
                    throw new AssertionError(
                            "Property 6 violated: leaderboard contains entry with level "
                            + entry.getLevel() + " which is below the 10th-highest level "
                            + tenthHighestLevel + ". Only top-10 islands should be present.");
                }
            }

            // The leaderboard must have exactly 10 entries when there are more than 10 islands
            if (top10.size() != 10) {
                throw new AssertionError(
                        "Property 6 violated: expected exactly 10 entries when " + islands.size()
                        + " islands exist, but got " + top10.size());
            }
        }
    }

    /**
     * When fewer than 10 islands exist, {@code getTop10()} must return exactly
     * as many entries as there are islands (no empty/null padding).
     *
     * <p><b>Validates: Requirements 9.1, 9.3</b></p>
     */
    @Property
    void property6_fewerThan10IslandsReturnsAllEntries(
            @ForAll("distinctLevelIslandsUpTo9") List<IslandInput> islands) {

        LeaderboardService service = newService();

        for (IslandInput island : islands) {
            service.update(island.uuid, island.name, island.level);
        }

        List<LeaderboardEntry> top10 = service.getTop10();

        // Must return exactly as many entries as islands (no empty entries)
        if (top10.size() != islands.size()) {
            throw new AssertionError(
                    "Property 6 violated: expected " + islands.size()
                    + " entries (all islands) but got " + top10.size()
                    + ". No empty entries should be present.");
        }

        // Must still be sorted descending
        for (int i = 0; i < top10.size() - 1; i++) {
            long current = top10.get(i).getLevel();
            long next = top10.get(i + 1).getLevel();
            if (current < next) {
                throw new AssertionError(
                        "Property 6 violated: leaderboard is not sorted descending at index "
                        + i + " (fewer-than-10 case). Entry[" + i + "].level=" + current
                        + " < Entry[" + (i + 1) + "].level=" + next);
            }
        }
    }

    /**
     * Updating an existing island's level must keep the leaderboard correctly
     * sorted and trimmed.
     *
     * <p><b>Validates: Requirements 9.1, 9.3</b></p>
     */
    @Property
    void property6_updatePreservesOrderingInvariant(
            @ForAll("distinctLevelIslands") List<IslandInput> islands,
            @ForAll @IntRange(min = 0, max = 999_999) int newLevel) {

        Assume.that(!islands.isEmpty());

        LeaderboardService service = newService();

        for (IslandInput island : islands) {
            service.update(island.uuid, island.name, island.level);
        }

        // Update the first island with a new level
        IslandInput first = islands.get(0);
        service.update(first.uuid, first.name, newLevel);

        List<LeaderboardEntry> top10 = service.getTop10();

        // Invariant 1: at most 10 entries
        if (top10.size() > 10) {
            throw new AssertionError(
                    "Property 6 violated after update: getTop10() returned " + top10.size()
                    + " entries (expected at most 10).");
        }

        // Invariant 2: sorted descending
        for (int i = 0; i < top10.size() - 1; i++) {
            long current = top10.get(i).getLevel();
            long next = top10.get(i + 1).getLevel();
            if (current < next) {
                throw new AssertionError(
                        "Property 6 violated after update: leaderboard is not sorted descending at index "
                        + i + ". Entry[" + i + "].level=" + current
                        + " < Entry[" + (i + 1) + "].level=" + next);
            }
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    /**
     * Generates a list of 1–20 islands with distinct level values (0–999_999).
     * Distinct levels ensure deterministic ordering for the top-10 check.
     */
    @Provide
    Arbitrary<List<IslandInput>> distinctLevelIslands() {
        // Generate a list of distinct longs in [0, 999_999], then map to IslandInput
        return Arbitraries.integers().between(0, 999_999)
                .list()
                .ofMinSize(1)
                .ofMaxSize(20)
                .uniqueElements()
                .map(levels -> {
                    List<IslandInput> result = new ArrayList<>();
                    for (int level : levels) {
                        result.add(new IslandInput(UUID.randomUUID(), "player_" + level, level));
                    }
                    return result;
                });
    }

    /**
     * Generates a list of 0–9 islands with distinct level values.
     * Used to test the "fewer than 10 islands" case.
     */
    @Provide
    Arbitrary<List<IslandInput>> distinctLevelIslandsUpTo9() {
        return Arbitraries.integers().between(0, 999_999)
                .list()
                .ofMinSize(0)
                .ofMaxSize(9)
                .uniqueElements()
                .map(levels -> {
                    List<IslandInput> result = new ArrayList<>();
                    for (int level : levels) {
                        result.add(new IslandInput(UUID.randomUUID(), "player_" + level, level));
                    }
                    return result;
                });
    }

    // =========================================================================
    // Simple value holder for test inputs
    // =========================================================================

    static class IslandInput {
        final UUID uuid;
        final String name;
        final long level;

        IslandInput(UUID uuid, String name, long level) {
            this.uuid = uuid;
            this.name = name;
            this.level = level;
        }

        @Override
        public String toString() {
            return "IslandInput{uuid=" + uuid + ", name='" + name + "', level=" + level + "}";
        }
    }
}
