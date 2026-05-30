package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 7: Kick/leave removes membership

import net.jqwik.api.*;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.skyblock.island.IslandException;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unit and property-based tests for {@link IslandManager} member management:
 * {@code addMember}, {@code removeMember}, and {@code setSpawnPoint}.
 *
 * <p>Property 7: Kick/leave removes membership — after {@code removeMember()},
 * the player UUID must not appear in {@code IslandMetadata.members} and
 * {@code SkyblockPlayerData.islandOwnerUUID} must be null.</p>
 *
 * <p>Validates: Requirements 5.2, 6.1, 6.2, 3.2</p>
 */
class MemberManagementTest {

    // =========================================================================
    // Minimal test stubs
    // =========================================================================

    /**
     * No-op persistence layer that records calls without touching the filesystem.
     */
    static class NoOpPersistenceLayer extends PersistenceLayer {
        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-test-noop"));
        }

        @Override
        public void saveIslandMetadata(IslandMetadata meta) { /* no-op */ }

        @Override
        public void savePlayerData(SkyblockPlayerData data) { /* no-op */ }

        @Override
        public java.util.List<IslandMetadata> loadAllIslandMetadata() {
            return java.util.Collections.emptyList();
        }

        @Override
        public java.util.List<SkyblockPlayerData> loadAllPlayerData() {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Stub {@link PlayerDataManager} backed by an in-memory map; delegates
     * {@code clearIslandAssociation} to the real implementation (which calls
     * {@code savePlayerData} on the no-op layer).
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
        private final StubPlayerDataManager pdm;
        private final NoOpPersistenceLayer persistence;

        StubPlugin() {
            this.persistence = new NoOpPersistenceLayer();
            this.pdm = new StubPlayerDataManager(persistence);
        }

        @Override
        public PlayerDataManager getPlayerDataManager() { return pdm; }

        @Override
        public PersistenceLayer getPersistenceLayer() { return persistence; }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds a minimal {@link IslandMetadata} and injects it into the manager's
     * private {@code islands} map via reflection.
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

    // =========================================================================
    // addMember — unit tests
    // =========================================================================

    @Example
    void addMember_addsToMembersMap() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);

        manager.addMember(ownerUUID, memberUUID);

        IslandMetadata meta = manager.getIslandByOwner(ownerUUID);
        if (!meta.getMembers().containsKey(memberUUID)) {
            throw new AssertionError("memberUUID not found in IslandMetadata.members after addMember()");
        }
        if (meta.getMembers().get(memberUUID) != IslandRole.MEMBER) {
            throw new AssertionError("Expected role MEMBER but got: " + meta.getMembers().get(memberUUID));
        }
    }

    @Example
    void addMember_updatesSkyblockPlayerData() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);

        SkyblockPlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (data == null) {
            throw new AssertionError("SkyblockPlayerData not created for member after addMember()");
        }
        if (!ownerUUID.equals(data.getIslandOwnerUUID())) {
            throw new AssertionError("Expected islandOwnerUUID=" + ownerUUID + " but got: " + data.getIslandOwnerUUID());
        }
        if (data.getRole() != IslandRole.MEMBER) {
            throw new AssertionError("Expected role MEMBER but got: " + data.getRole());
        }
    }

    @Example
    void addMember_throwsWhenIslandNotFound() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        boolean threw = false;
        try {
            manager.addMember(ownerUUID, memberUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Expected IslandException when island not found, but none was thrown");
        }
    }

    @Example
    void addMember_throwsWhenAlreadyMember() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);

        boolean threw = false;
        try {
            manager.addMember(ownerUUID, memberUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Expected IslandException when adding duplicate member, but none was thrown");
        }
    }

    // =========================================================================
    // removeMember — unit tests
    // =========================================================================

    @Example
    void removeMember_removesFromMembersMap() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);
        manager.removeMember(ownerUUID, memberUUID);

        IslandMetadata meta = manager.getIslandByOwner(ownerUUID);
        if (meta.getMembers().containsKey(memberUUID)) {
            throw new AssertionError("memberUUID still present in IslandMetadata.members after removeMember()");
        }
    }

    @Example
    void removeMember_clearsPlayerDataIslandAssociation() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);
        manager.removeMember(ownerUUID, memberUUID);

        SkyblockPlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (data == null) {
            throw new AssertionError("SkyblockPlayerData was deleted entirely; expected it to exist with null island fields");
        }
        if (data.getIslandOwnerUUID() != null) {
            throw new AssertionError("Expected islandOwnerUUID=null after removeMember() but got: " + data.getIslandOwnerUUID());
        }
        if (data.getRole() != null) {
            throw new AssertionError("Expected role=null after removeMember() but got: " + data.getRole());
        }
    }

    @Example
    void removeMember_throwsWhenIslandNotFound() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID memberUUID = UUID.randomUUID();

        boolean threw = false;
        try {
            manager.removeMember(ownerUUID, memberUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Expected IslandException when island not found, but none was thrown");
        }
    }

    @Example
    void removeMember_throwsWhenNotAMember() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();
        UUID nonMemberUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);

        boolean threw = false;
        try {
            manager.removeMember(ownerUUID, nonMemberUUID);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Expected IslandException when removing non-member, but none was thrown");
        }
    }

    // =========================================================================
    // setSpawnPoint — unit tests
    // =========================================================================

    @Example
    void setSpawnPoint_updatesSpawnFields() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        injectIsland(manager, ownerUUID);

        // Location3d constructor: (x, y, z, pitch, yaw, dimension)
        Location3d newSpawn = new Location3d(10.5, 70.0, -5.5, 15.0, 90.0, null);
        manager.setSpawnPoint(ownerUUID, newSpawn);

        IslandMetadata meta = manager.getIslandByOwner(ownerUUID);
        if (Double.compare(meta.getSpawnX(), 10.5) != 0) {
            throw new AssertionError("Expected spawnX=10.5 but got: " + meta.getSpawnX());
        }
        if (Double.compare(meta.getSpawnY(), 70.0) != 0) {
            throw new AssertionError("Expected spawnY=70.0 but got: " + meta.getSpawnY());
        }
        if (Double.compare(meta.getSpawnZ(), -5.5) != 0) {
            throw new AssertionError("Expected spawnZ=-5.5 but got: " + meta.getSpawnZ());
        }
        // setSpawnPoint stores location.yaw as spawnYaw and location.pitch as spawnPitch
        if (Float.compare(meta.getSpawnYaw(), 90.0f) != 0) {
            throw new AssertionError("Expected spawnYaw=90.0 but got: " + meta.getSpawnYaw());
        }
        if (Float.compare(meta.getSpawnPitch(), 15.0f) != 0) {
            throw new AssertionError("Expected spawnPitch=15.0 but got: " + meta.getSpawnPitch());
        }
    }

    @Example
    void setSpawnPoint_throwsWhenIslandNotFound() {
        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);
        UUID ownerUUID = UUID.randomUUID();

        Location3d loc = new Location3d(0.0, 64.0, 0.0, 0.0, 0.0, null);
        boolean threw = false;
        try {
            manager.setSpawnPoint(ownerUUID, loc);
        } catch (IslandException e) {
            threw = true;
        }
        if (!threw) {
            throw new AssertionError("Expected IslandException when island not found, but none was thrown");
        }
    }

    // =========================================================================
    // Property 7: Kick/leave removes membership
    // =========================================================================

    /**
     * For any island and any player who is a Member of that island, after
     * {@code removeMember()} is called, the player's UUID must NOT appear in
     * {@code IslandMetadata.members} and {@code SkyblockPlayerData.islandOwnerUUID}
     * must be null.
     *
     * <p>Validates: Requirements 6.1, 6.2</p>
     */
    @Property
    void removeMember_membershipFullyCleared(
            @ForAll("arbitraryUUID") UUID ownerUUID,
            @ForAll("arbitraryUUID") UUID memberUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);

        // Pre-condition: member is in the island
        IslandMetadata metaBefore = manager.getIslandByOwner(ownerUUID);
        if (!metaBefore.getMembers().containsKey(memberUUID)) {
            throw new AssertionError("Pre-condition failed: member not added before removeMember()");
        }

        manager.removeMember(ownerUUID, memberUUID);

        // Post-condition 1: not in IslandMetadata.members
        IslandMetadata metaAfter = manager.getIslandByOwner(ownerUUID);
        if (metaAfter.getMembers().containsKey(memberUUID)) {
            throw new AssertionError(
                    "Property 7 violated: memberUUID " + memberUUID
                    + " still present in IslandMetadata.members after removeMember()");
        }

        // Post-condition 2: SkyblockPlayerData.islandOwnerUUID is null
        SkyblockPlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (data != null && data.getIslandOwnerUUID() != null) {
            throw new AssertionError(
                    "Property 7 violated: SkyblockPlayerData.islandOwnerUUID is not null after removeMember(). "
                    + "Got: " + data.getIslandOwnerUUID());
        }
    }

    /**
     * For any island and any player who is a Member, after {@code addMember()} is called,
     * the player's UUID must appear in {@code IslandMetadata.members} with role MEMBER
     * and {@code SkyblockPlayerData.islandOwnerUUID} must equal the owner's UUID.
     *
     * <p>Validates: Requirements 5.2</p>
     */
    @Property
    void addMember_membershipFullyEstablished(
            @ForAll("arbitraryUUID") UUID ownerUUID,
            @ForAll("arbitraryUUID") UUID memberUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);

        // Post-condition 1: in IslandMetadata.members with MEMBER role
        IslandMetadata meta = manager.getIslandByOwner(ownerUUID);
        if (!meta.getMembers().containsKey(memberUUID)) {
            throw new AssertionError(
                    "Property 8 violated: memberUUID " + memberUUID
                    + " not found in IslandMetadata.members after addMember()");
        }
        if (meta.getMembers().get(memberUUID) != IslandRole.MEMBER) {
            throw new AssertionError(
                    "Property 8 violated: expected role MEMBER but got: "
                    + meta.getMembers().get(memberUUID));
        }

        // Post-condition 2: SkyblockPlayerData.islandOwnerUUID equals ownerUUID
        SkyblockPlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (data == null) {
            throw new AssertionError(
                    "Property 8 violated: SkyblockPlayerData not created for member after addMember()");
        }
        if (!ownerUUID.equals(data.getIslandOwnerUUID())) {
            throw new AssertionError(
                    "Property 8 violated: expected islandOwnerUUID=" + ownerUUID
                    + " but got: " + data.getIslandOwnerUUID());
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    @Provide
    Arbitrary<UUID> arbitraryUUID() {
        return Arbitraries.randomValue(r -> UUID.randomUUID());
    }
}
