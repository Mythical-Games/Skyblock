package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 1: Island creation is exclusive

import net.jqwik.api.*;
import org.allaymc.skyblock.island.IslandCreationException;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Property-based tests for island creation exclusivity.
 *
 * <p>Property 1: Island creation is exclusive</p>
 * <p>For any player UUID already associated with an island (as Owner or Member),
 * calling {@code createIsland()} must throw {@link IslandCreationException} and
 * leave the island registry unchanged.</p>
 *
 * <p>Validates: Requirements 2.6</p>
 *
 * <p>Note: {@code SkyblockPlugin} extends the AllayMC {@code Plugin} class which is
 * {@code compileOnly} and unavailable at test runtime. We therefore use a hand-rolled
 * {@link StubPlugin} that satisfies only the interface surface that
 * {@link IslandManager#createIsland} exercises before throwing.</p>
 */
class IslandCreationExclusivityTest {

    // =========================================================================
    // Minimal test stubs
    // =========================================================================

    /**
     * A minimal stub for {@link SkyblockPlugin} that does NOT extend the AllayMC
     * {@code Plugin} base class (which is compileOnly and unavailable at test time).
     *
     * <p>It exposes only the methods called by {@link IslandManager#createIsland}
     * before the exclusivity check throws.</p>
     */
    static class StubPlugin extends SkyblockPlugin {
        private final StubPlayerDataManager pdm;

        StubPlugin(StubPlayerDataManager pdm) {
            this.pdm = pdm;
        }

        @Override
        public PlayerDataManager getPlayerDataManager() {
            return pdm;
        }

        @Override
        public PersistenceLayer getPersistenceLayer() {
            // Should never be reached in these tests (exception is thrown first)
            throw new UnsupportedOperationException("getPersistenceLayer() should not be called in exclusivity tests");
        }
    }

    /**
     * A minimal stub for {@link PlayerDataManager} that does NOT call the real
     * persistence layer. It uses an in-memory map to answer {@code hasIsland()}.
     */
    static class StubPlayerDataManager extends PlayerDataManager {
        private final ConcurrentHashMap<UUID, Boolean> hasIslandMap = new ConcurrentHashMap<>();

        StubPlayerDataManager() {
            super(null); // plugin reference not needed for these stubs
        }

        /** Mark a UUID as already having an island. */
        void markHasIsland(UUID uuid) {
            hasIslandMap.put(uuid, Boolean.TRUE);
        }

        @Override
        public boolean hasIsland(UUID playerUUID) {
            return Boolean.TRUE.equals(hasIslandMap.get(playerUUID));
        }

        @Override
        public SkyblockPlayerData getPlayerData(UUID playerUUID) {
            return null; // not needed before the exception
        }
    }

    // =========================================================================
    // Property: OWNER case — player already owns an island
    // =========================================================================

    /**
     * For any UUID where {@code PlayerDataManager.hasIsland()} returns {@code true}
     * (simulating an OWNER), {@code IslandManager.createIsland()} must throw
     * {@link IslandCreationException} and leave the registry unchanged.
     *
     * <p>Validates: Requirements 2.6</p>
     */
    @Property
    void createIsland_throwsForExistingOwner(@ForAll("arbitraryUUID") UUID ownerUUID) {
        // Arrange: stub PDM reports the player already has an island (OWNER case)
        StubPlayerDataManager pdm = new StubPlayerDataManager();
        pdm.markHasIsland(ownerUUID);
        IslandManager islandManager = new IslandManager(new StubPlugin(pdm));

        // Capture registry state before the call (must be null — no island yet)
        IslandMetadata beforeCall = islandManager.getIslandByOwner(ownerUUID);

        // Act + Assert: must throw IslandCreationException
        boolean threw = false;
        try {
            islandManager.createIsland(ownerUUID);
        } catch (IslandCreationException e) {
            threw = true;
        }

        if (!threw) {
            throw new AssertionError(
                    "Expected IslandCreationException for UUID " + ownerUUID
                    + " (OWNER case) but no exception was thrown.");
        }

        // Registry must be unchanged: still null (no island was added)
        IslandMetadata afterCall = islandManager.getIslandByOwner(ownerUUID);
        if (beforeCall != afterCall) {
            throw new AssertionError(
                    "Registry was modified after failed createIsland() for UUID " + ownerUUID
                    + " (OWNER case). Before=" + beforeCall + " After=" + afterCall);
        }
    }

    // =========================================================================
    // Property: MEMBER case — player is a member of another island
    // =========================================================================

    /**
     * For any UUID where {@code PlayerDataManager.hasIsland()} returns {@code true}
     * (simulating a MEMBER), {@code IslandManager.createIsland()} must throw
     * {@link IslandCreationException} and leave the registry unchanged.
     *
     * <p>From {@code IslandManager}'s perspective the MEMBER case is identical to
     * the OWNER case: {@code hasIsland()} returns {@code true} for both roles, so
     * the guard rejects them uniformly.</p>
     *
     * <p>Validates: Requirements 2.6</p>
     */
    @Property
    void createIsland_throwsForExistingMember(@ForAll("arbitraryUUID") UUID memberUUID) {
        // Arrange: stub PDM reports the player already has an island (MEMBER case)
        StubPlayerDataManager pdm = new StubPlayerDataManager();
        pdm.markHasIsland(memberUUID);
        IslandManager islandManager = new IslandManager(new StubPlugin(pdm));

        // Capture registry state before the call
        IslandMetadata beforeCall = islandManager.getIslandByOwner(memberUUID);

        // Act + Assert: must throw IslandCreationException
        boolean threw = false;
        try {
            islandManager.createIsland(memberUUID);
        } catch (IslandCreationException e) {
            threw = true;
        }

        if (!threw) {
            throw new AssertionError(
                    "Expected IslandCreationException for UUID " + memberUUID
                    + " (MEMBER case) but no exception was thrown.");
        }

        // Registry must be unchanged: still null (no island was added)
        IslandMetadata afterCall = islandManager.getIslandByOwner(memberUUID);
        if (beforeCall != afterCall) {
            throw new AssertionError(
                    "Registry was modified after failed createIsland() for UUID " + memberUUID
                    + " (MEMBER case). Before=" + beforeCall + " After=" + afterCall);
        }
    }

    // =========================================================================
    // Property: registry is unchanged even when it already has other entries
    // =========================================================================

    /**
     * For any UUID already associated with an island, calling {@code createIsland()}
     * must not alter any pre-existing entries in the registry.
     *
     * <p>This variant pre-populates the registry with a different island and verifies
     * that the failed call does not corrupt it.</p>
     *
     * <p>Validates: Requirements 2.6</p>
     */
    @Property
    void createIsland_doesNotCorruptRegistryOnRejection(
            @ForAll("arbitraryUUID") UUID existingOwnerUUID,
            @ForAll("arbitraryUUID") UUID attemptingUUID) {

        // Ensure the two UUIDs are distinct so the pre-populated entry is unambiguous
        Assume.that(!existingOwnerUUID.equals(attemptingUUID));

        // Arrange: the attempting player already has an island; the existing owner does not
        StubPlayerDataManager pdm = new StubPlayerDataManager();
        pdm.markHasIsland(attemptingUUID);
        IslandManager islandManager = new IslandManager(new StubPlugin(pdm));

        // Pre-populate the registry with an existing island entry via reflection
        IslandMetadata existingMeta = buildMinimalMetadata(existingOwnerUUID);
        injectIsland(islandManager, existingOwnerUUID, existingMeta);

        // Act: attempt to create an island for the already-associated player
        boolean threw = false;
        try {
            islandManager.createIsland(attemptingUUID);
        } catch (IslandCreationException e) {
            threw = true;
        }

        if (!threw) {
            throw new AssertionError(
                    "Expected IslandCreationException for UUID " + attemptingUUID
                    + " but no exception was thrown.");
        }

        // The pre-existing entry must still be intact
        IslandMetadata afterCall = islandManager.getIslandByOwner(existingOwnerUUID);
        if (afterCall == null) {
            throw new AssertionError(
                    "Pre-existing island for owner " + existingOwnerUUID
                    + " was removed after a failed createIsland() call.");
        }
        if (!existingOwnerUUID.equals(afterCall.getOwnerUUID())) {
            throw new AssertionError(
                    "Pre-existing island metadata was corrupted after a failed createIsland() call. "
                    + "Expected ownerUUID=" + existingOwnerUUID
                    + " but got=" + afterCall.getOwnerUUID());
        }

        // The attempting player must still have no island in the registry
        IslandMetadata attemptEntry = islandManager.getIslandByOwner(attemptingUUID);
        if (attemptEntry != null) {
            throw new AssertionError(
                    "Registry gained an entry for the rejected UUID " + attemptingUUID
                    + " after IslandCreationException was thrown.");
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    /** Generates arbitrary UUIDs using randomized generation (100 tries by default). */
    @Provide
    Arbitrary<UUID> arbitraryUUID() {
        // Use randomValue so jqwik uses RANDOMIZED generation (not EXHAUSTIVE),
        // ensuring the property is checked across 100 distinct random UUIDs.
        return Arbitraries.randomValue(r -> UUID.randomUUID());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal {@link IslandMetadata} for use in tests that need a
     * pre-populated registry entry without going through the full creation path.
     */
    private IslandMetadata buildMinimalMetadata(UUID ownerUUID) {
        IslandMetadata meta = new IslandMetadata();
        meta.setOwnerUUID(ownerUUID);
        meta.setOwnerName(ownerUUID.toString());
        meta.setWorldName("skyblock_" + ownerUUID);
        meta.setSpawnX(0.0);
        meta.setSpawnY(66.0);
        meta.setSpawnZ(0.0);
        meta.setSpawnYaw(0.0f);
        meta.setSpawnPitch(0.0f);
        meta.setIslandLevel(0L);
        meta.setCreatedAt(System.currentTimeMillis());
        return meta;
    }

    /**
     * Injects an {@link IslandMetadata} entry directly into the {@code IslandManager}'s
     * private {@code islands} map via reflection, bypassing the full creation path.
     * This lets us test registry-corruption scenarios without needing a live server.
     */
    private void injectIsland(IslandManager manager, UUID ownerUUID, IslandMetadata meta) {
        try {
            var field = IslandManager.class.getDeclaredField("islands");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<UUID, IslandMetadata> map =
                    (ConcurrentHashMap<UUID, IslandMetadata>) field.get(manager);
            map.put(ownerUUID, meta);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject island into IslandManager via reflection", e);
        }
    }
}
