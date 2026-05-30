package org.allaymc.skyblock;

// Feature: skyblock-plugin, Task 7.5: IslandManager delete and reset

import net.jqwik.api.*;
import org.allaymc.skyblock.island.IslandException;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit tests for {@link IslandManager#deleteIsland(UUID)} and
 * {@link IslandManager#resetIsland(UUID)}.
 *
 * <p>These tests exercise the in-memory state transitions and persistence calls
 * without requiring a live AllayMC server. World-interaction paths (unload/clear)
 * are covered by the documented behaviour; the tests here focus on the registry
 * and player-data invariants that can be verified without a server.</p>
 *
 * <p>Requirements: 4.1, 4.2</p>
 */
class IslandDeleteResetTest {

    // =========================================================================
    // Minimal test stubs
    // =========================================================================

    /**
     * No-op persistence layer — records calls without touching the filesystem.
     */
    static class NoOpPersistenceLayer extends PersistenceLayer {
        final List<UUID> deletedIslands = new ArrayList<>();
        final List<IslandMetadata> savedIslands = new ArrayList<>();
        final List<SkyblockPlayerData> savedPlayers = new ArrayList<>();

        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-delete-reset-test"));
        }

        @Override
        public void saveIslandMetadata(IslandMetadata meta) {
            savedIslands.add(meta);
        }

        @Override
        public void deleteIslandMetadata(UUID ownerUUID) {
            deletedIslands.add(ownerUUID);
        }

        @Override
        public void savePlayerData(SkyblockPlayerData data) {
            savedPlayers.add(data);
        }

        @Override
        public List<IslandMetadata> loadAllIslandMetadata() {
            return java.util.Collections.emptyList();
        }

        @Override
        public List<SkyblockPlayerData> loadAllPlayerData() {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Stub {@link PlayerDataManager} backed by an in-memory map.
     */
    static class StubPlayerDataManager extends PlayerDataManager {
        private final ConcurrentHashMap<UUID, SkyblockPlayerData> map = new ConcurrentHashMap<>();
        private final NoOpPersistenceLayer persistence;

        StubPlayerDataManager(NoOpPersistenceLayer persistence) {
            super(null);
            this.persistence = persistence;
        }

        @Override
        public SkyblockPlayerData getPlayerData(UUID playerUUID) {
            return map.get(playerUUID);
        }

        @Override
        public void setPlayerData(UUID playerUUID, SkyblockPlayerData data) {
            map.put(playerUUID, data);
        }

        @Override
        public boolean hasIsland(UUID playerUUID) {
            SkyblockPlayerData d = map.get(playerUUID);
            return d != null && d.getIslandOwnerUUID() != null;
        }

        @Override
        public UUID getIslandOwner(UUID playerUUID) {
            SkyblockPlayerData d = map.get(playerUUID);
            return d != null ? d.getIslandOwnerUUID() : null;
        }

        @Override
        public void clearIslandAssociation(UUID playerUUID) {
            SkyblockPlayerData data = map.get(playerUUID);
            if (data == null) return;
            data.setIslandOwnerUUID(null);
            data.setRole(null);
            persistence.savePlayerData(data); // no-op
        }
    }

    /**
     * Stub {@link SkyblockPlugin} that wires together the no-op persistence layer
     * and the stub player data manager.
     */
    static class StubPlugin extends SkyblockPlugin {
        final NoOpPersistenceLayer persistence;
        final StubPlayerDataManager pdm;

        StubPlugin() {
            this.persistence = new NoOpPersistenceLayer();
            this.pdm = new StubPlayerDataManager(persistence);
        }

        @Override
        public PlayerDataManager getPlayerDataManager() {
            return pdm;
        }

        @Override
        public PersistenceLayer getPersistenceLayer() {
            return persistence;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Injects a minimal {@link IslandMetadata} into the manager's private
     * {@code islands} map via reflection, bypassing the full creation path.
     */
    private IslandMetadata injectIsland(IslandManager manager, UUID ownerUUID) {
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
        meta.setMembers(new HashMap<>());

        try {
            var field = IslandManager.class.getDeclaredField("islands");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<UUID, IslandMetadata> map =
                    (ConcurrentHashMap<UUID, IslandMetadata>) field.get(manager);
            map.put(ownerUUID, meta);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Reflection injection failed", e);
        }
        return meta;
    }

    /**
     * Seeds a player's data into the stub PDM so that
     * {@code clearIslandAssociation} has something to clear.
     */
    private void seedPlayerData(StubPlugin plugin, UUID playerUUID, UUID islandOwnerUUID, IslandRole role) {
        SkyblockPlayerData data = new SkyblockPlayerData();
        data.setPlayerUUID(playerUUID);
        data.setIslandOwnerUUID(islandOwnerUUID);
        data.setRole(role);
        plugin.pdm.setPlayerData(playerUUID, data);
    }

    // =========================================================================
    // deleteIsland — unit tests
    // =========================================================================

    /**
     * After {@code deleteIsland()}, the island must no longer be present in the
     * in-memory registry.
     *
     * <p>Requirements: 4.1</p>
     */
    @Example
    void deleteIsland_removesFromRegistry() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);

        // deleteIsland calls Server.getInstance().getWorldPool().unloadWorld(...)
        // which will throw in a test environment. We catch IslandException to
        // verify the registry and persistence calls happened before the world step.
        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // Expected: world unload fails without a live server.
            // The registry removal and persistence deletion happen BEFORE the unload call.
        }

        // Island must be removed from the in-memory registry
        IslandMetadata afterDelete = manager.getIslandByOwner(ownerUUID);
        if (afterDelete != null) {
            throw new AssertionError(
                    "deleteIsland() did not remove the island from the registry for owner " + ownerUUID);
        }
    }

    /**
     * After {@code deleteIsland()}, the owner's island association must be cleared.
     *
     * <p>Requirements: 4.1</p>
     */
    @Example
    void deleteIsland_clearsOwnerIslandAssociation() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);

        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // World unload fails without a live server — expected.
        }

        SkyblockPlayerData ownerData = plugin.pdm.getPlayerData(ownerUUID);
        if (ownerData != null && ownerData.getIslandOwnerUUID() != null) {
            throw new AssertionError(
                    "deleteIsland() did not clear the owner's islandOwnerUUID. Got: "
                    + ownerData.getIslandOwnerUUID());
        }
    }

    /**
     * After {@code deleteIsland()}, all members' island associations must be cleared.
     *
     * <p>Requirements: 4.1</p>
     */
    @Example
    void deleteIsland_clearsAllMemberAssociations() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();

        IslandMetadata meta = injectIsland(manager, ownerUUID);
        meta.getMembers().put(member1, IslandRole.MEMBER);
        meta.getMembers().put(member2, IslandRole.MEMBER);

        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);
        seedPlayerData(plugin, member1, ownerUUID, IslandRole.MEMBER);
        seedPlayerData(plugin, member2, ownerUUID, IslandRole.MEMBER);

        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // World unload fails without a live server — expected.
        }

        // Both members must have their island association cleared
        for (UUID memberUUID : new UUID[]{member1, member2}) {
            SkyblockPlayerData data = plugin.pdm.getPlayerData(memberUUID);
            if (data != null && data.getIslandOwnerUUID() != null) {
                throw new AssertionError(
                        "deleteIsland() did not clear islandOwnerUUID for member " + memberUUID
                        + ". Got: " + data.getIslandOwnerUUID());
            }
        }
    }

    /**
     * After {@code deleteIsland()}, the persistence layer must have been asked to
     * delete the island metadata.
     *
     * <p>Requirements: 4.1</p>
     */
    @Example
    void deleteIsland_callsDeleteIslandMetadata() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);

        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // World unload fails without a live server — expected.
        }

        if (!plugin.persistence.deletedIslands.contains(ownerUUID)) {
            throw new AssertionError(
                    "deleteIsland() did not call PersistenceLayer.deleteIslandMetadata() for owner " + ownerUUID);
        }
    }

    /**
     * {@code deleteIsland()} must throw {@link IslandException} when no island
     * exists for the given owner UUID.
     *
     * <p>Requirements: 4.1</p>
     */
    @Example
    void deleteIsland_throwsWhenIslandNotFound() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        boolean threw = false;
        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError(
                    "Expected IslandException when deleting non-existent island, but none was thrown");
        }
    }

    // =========================================================================
    // resetIsland — unit tests
    // =========================================================================

    /**
     * {@code resetIsland()} must throw {@link IslandException} when no island
     * exists for the given owner UUID.
     *
     * <p>Requirements: 4.2</p>
     */
    @Example
    void resetIsland_throwsWhenIslandNotFound() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        boolean threw = false;
        try {
            manager.resetIsland(ownerUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError(
                    "Expected IslandException when resetting non-existent island, but none was thrown");
        }
    }

    /**
     * {@code resetIsland()} must throw {@link IslandException} when the island world
     * is not loaded (i.e. {@code Server.getInstance().getWorldPool().getWorld()} returns null
     * or throws). This verifies the guard against an unloaded world.
     *
     * <p>Requirements: 4.2</p>
     */
    @Example
    void resetIsland_throwsWhenWorldNotLoaded() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);

        // Without a live server, getWorldPool() will throw or return null,
        // which resetIsland() must convert to an IslandException.
        boolean threw = false;
        try {
            manager.resetIsland(ownerUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError(
                    "Expected IslandException when island world is not loaded, but none was thrown");
        }
    }

    // =========================================================================
    // Property: deleteIsland removes island from registry for any owner UUID
    // =========================================================================

    /**
     * For any owner UUID with an existing island, after {@code deleteIsland()} is
     * called, the island must not be present in the registry and the persistence
     * layer must have been asked to delete the metadata.
     *
     * <p>Requirements: 4.1</p>
     */
    @Property
    void property_deleteIsland_registryAndPersistenceCleared(
            @ForAll("randomUUID") UUID ownerUUID) {

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        injectIsland(manager, ownerUUID);
        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);

        // Pre-condition: island is present
        if (manager.getIslandByOwner(ownerUUID) == null) {
            throw new AssertionError("Pre-condition failed: island not injected for owner " + ownerUUID);
        }

        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // World unload fails without a live server — expected.
        }

        // Post-condition 1: island removed from registry
        if (manager.getIslandByOwner(ownerUUID) != null) {
            throw new AssertionError(
                    "Property violated: island still in registry after deleteIsland() for owner " + ownerUUID);
        }

        // Post-condition 2: persistence layer was asked to delete the metadata
        if (!plugin.persistence.deletedIslands.contains(ownerUUID)) {
            throw new AssertionError(
                    "Property violated: deleteIslandMetadata() not called for owner " + ownerUUID);
        }
    }

    /**
     * For any owner UUID with an existing island and one or more members, after
     * {@code deleteIsland()} is called, all member island associations must be null.
     *
     * <p>Requirements: 4.1</p>
     */
    @Property
    void property_deleteIsland_allMemberAssociationsCleared(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID memberUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        IslandMetadata meta = injectIsland(manager, ownerUUID);
        meta.getMembers().put(memberUUID, IslandRole.MEMBER);

        seedPlayerData(plugin, ownerUUID, ownerUUID, IslandRole.OWNER);
        seedPlayerData(plugin, memberUUID, ownerUUID, IslandRole.MEMBER);

        try {
            manager.deleteIsland(ownerUUID);
        } catch (IslandException e) {
            // World unload fails without a live server — expected.
        }

        // Member's island association must be cleared
        SkyblockPlayerData memberData = plugin.pdm.getPlayerData(memberUUID);
        if (memberData != null && memberData.getIslandOwnerUUID() != null) {
            throw new AssertionError(
                    "Property violated: member " + memberUUID
                    + " still has islandOwnerUUID=" + memberData.getIslandOwnerUUID()
                    + " after deleteIsland()");
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    @Provide
    Arbitrary<UUID> randomUUID() {
        return Arbitraries.randomValue(r -> UUID.randomUUID());
    }
}
