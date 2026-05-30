package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 7: Kick/leave removes membership

import net.jqwik.api.*;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Property-based tests for Property 7: Kick/leave removes membership.
 *
 * <p>After {@code IslandManager.removeMember()} is called, the player UUID must NOT
 * appear in {@code IslandMetadata.members} and {@code SkyblockPlayerData.islandOwnerUUID}
 * must be {@code null}.</p>
 *
 * <p><b>Validates: Requirements 6.1, 6.2</b></p>
 */
class MembershipRemovalTest {

    // =========================================================================
    // Minimal test stubs (mirrors MemberManagementTest infrastructure)
    // =========================================================================

    static class NoOpPersistenceLayer extends PersistenceLayer {
        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-membership-removal-test"));
        }

        @Override public void saveIslandMetadata(IslandMetadata meta) { /* no-op */ }
        @Override public void savePlayerData(SkyblockPlayerData data) { /* no-op */ }
        @Override public java.util.List<IslandMetadata> loadAllIslandMetadata() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<SkyblockPlayerData> loadAllPlayerData() { return java.util.Collections.emptyList(); }
    }

    static class StubPlayerDataManager extends PlayerDataManager {
        private final ConcurrentHashMap<UUID, SkyblockPlayerData> map = new ConcurrentHashMap<>();
        private final NoOpPersistenceLayer persistence;

        StubPlayerDataManager(NoOpPersistenceLayer persistence) {
            super(null);
            this.persistence = persistence;
        }

        @Override public SkyblockPlayerData getPlayerData(UUID playerUUID) { return map.get(playerUUID); }
        @Override public void setPlayerData(UUID playerUUID, SkyblockPlayerData data) { map.put(playerUUID, data); }
        @Override public boolean hasIsland(UUID playerUUID) {
            SkyblockPlayerData d = map.get(playerUUID);
            return d != null && d.getIslandOwnerUUID() != null;
        }
        @Override public UUID getIslandOwner(UUID playerUUID) {
            SkyblockPlayerData d = map.get(playerUUID);
            return d != null ? d.getIslandOwnerUUID() : null;
        }
        @Override public void clearIslandAssociation(UUID playerUUID) {
            SkyblockPlayerData data = map.get(playerUUID);
            if (data == null) return;
            data.setIslandOwnerUUID(null);
            data.setRole(null);
            persistence.savePlayerData(data); // no-op
        }
    }

    static class StubPlugin extends SkyblockPlugin {
        private final StubPlayerDataManager pdm;
        private final NoOpPersistenceLayer persistence;

        StubPlugin() {
            this.persistence = new NoOpPersistenceLayer();
            this.pdm = new StubPlayerDataManager(persistence);
        }

        @Override public PlayerDataManager getPlayerDataManager() { return pdm; }
        @Override public PersistenceLayer getPersistenceLayer() { return persistence; }
    }

    // =========================================================================
    // Helper: inject an island directly into IslandManager via reflection
    // =========================================================================

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
    // Property 7: Kick/leave removes membership
    // Validates: Requirements 6.1, 6.2
    // =========================================================================

    /**
     * For any island and any player who is a Member of that island, after
     * {@code removeMember()} is called:
     * <ol>
     *   <li>The player UUID must NOT appear in {@code IslandMetadata.members}.</li>
     *   <li>{@code SkyblockPlayerData.islandOwnerUUID} must be {@code null}.</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 6.1, 6.2</b></p>
     */
    @Property
    void property7_kickLeaveRemovesMembership(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID memberUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        // Set up: create island and add member
        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberUUID);

        // Pre-condition: member is present before removal
        IslandMetadata metaBefore = manager.getIslandByOwner(ownerUUID);
        if (!metaBefore.getMembers().containsKey(memberUUID)) {
            throw new AssertionError("Pre-condition failed: member was not added before removeMember()");
        }

        // Act: remove the member (simulates kick or leave)
        manager.removeMember(ownerUUID, memberUUID);

        // Post-condition 1: UUID must NOT be in IslandMetadata.members
        IslandMetadata metaAfter = manager.getIslandByOwner(ownerUUID);
        if (metaAfter.getMembers().containsKey(memberUUID)) {
            throw new AssertionError(
                    "Property 7 violated: memberUUID " + memberUUID
                    + " still present in IslandMetadata.members after removeMember()");
        }

        // Post-condition 2: SkyblockPlayerData.islandOwnerUUID must be null
        SkyblockPlayerData data = plugin.getPlayerDataManager().getPlayerData(memberUUID);
        if (data != null && data.getIslandOwnerUUID() != null) {
            throw new AssertionError(
                    "Property 7 violated: SkyblockPlayerData.islandOwnerUUID is not null after removeMember(). "
                    + "Got: " + data.getIslandOwnerUUID());
        }
    }

    /**
     * Removing a member must not affect other members of the same island.
     * The island's remaining members must be unchanged after one member is removed.
     *
     * <p><b>Validates: Requirements 6.1, 6.2</b></p>
     */
    @Property
    void property7_removingOneMemberDoesNotAffectOthers(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID memberToRemove,
            @ForAll("randomUUID") UUID otherMember) {

        Assume.that(!ownerUUID.equals(memberToRemove));
        Assume.that(!ownerUUID.equals(otherMember));
        Assume.that(!memberToRemove.equals(otherMember));

        StubPlugin plugin = new StubPlugin();
        IslandManager manager = new IslandManager(plugin);

        injectIsland(manager, ownerUUID);
        manager.addMember(ownerUUID, memberToRemove);
        manager.addMember(ownerUUID, otherMember);

        // Remove only one member
        manager.removeMember(ownerUUID, memberToRemove);

        IslandMetadata meta = manager.getIslandByOwner(ownerUUID);

        // Removed member must be gone
        if (meta.getMembers().containsKey(memberToRemove)) {
            throw new AssertionError(
                    "Property 7 violated: removed member " + memberToRemove
                    + " still present in IslandMetadata.members");
        }

        // Other member must still be present
        if (!meta.getMembers().containsKey(otherMember)) {
            throw new AssertionError(
                    "Property 7 violated: otherMember " + otherMember
                    + " was incorrectly removed from IslandMetadata.members");
        }

        // Other member's player data must still reference the island
        SkyblockPlayerData otherData = plugin.getPlayerDataManager().getPlayerData(otherMember);
        if (otherData == null || !ownerUUID.equals(otherData.getIslandOwnerUUID())) {
            throw new AssertionError(
                    "Property 7 violated: otherMember's islandOwnerUUID was incorrectly cleared. "
                    + "Got: " + (otherData == null ? "null data" : otherData.getIslandOwnerUUID()));
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
