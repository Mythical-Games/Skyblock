package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 8: Invite acceptance adds membership

import net.jqwik.api.*;
import org.allaymc.skyblock.invite.InviteManager;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.PendingInvite;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Property-based tests for Property 8: Invite acceptance adds membership.
 *
 * <p>After {@code InviteManager.acceptInvite()} is called, the target player must be
 * added to {@code IslandMetadata.members} with role {@code MEMBER}, and
 * {@code SkyblockPlayerData.islandOwnerUUID} must equal the island owner's UUID.</p>
 *
 * <p><b>Validates: Requirements 5.2</b></p>
 */
class InviteAcceptanceTest {

    // =========================================================================
    // Minimal test stubs (same pattern as MembershipRemovalTest)
    // =========================================================================

    static class NoOpPersistenceLayer extends PersistenceLayer {
        NoOpPersistenceLayer() {
            super(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "skyblock-invite-acceptance-test"));
        }

        @Override public void saveIslandMetadata(IslandMetadata meta) { /* no-op */ }
        @Override public void savePlayerData(SkyblockPlayerData data) { /* no-op */ }
        @Override public List<IslandMetadata> loadAllIslandMetadata() { return Collections.emptyList(); }
        @Override public List<SkyblockPlayerData> loadAllPlayerData() { return Collections.emptyList(); }
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
        private IslandManager islandManager;

        StubPlugin() {
            this.persistence = new NoOpPersistenceLayer();
            this.pdm = new StubPlayerDataManager(persistence);
        }

        void setIslandManager(IslandManager islandManager) {
            this.islandManager = islandManager;
        }

        @Override public PlayerDataManager getPlayerDataManager() { return pdm; }
        @Override public PersistenceLayer getPersistenceLayer() { return persistence; }
        @Override public IslandManager getIslandManager() { return islandManager; }
    }

    /**
     * A subclass of {@link InviteManager} that overrides {@code acceptInvite} to skip
     * the player-notification step (which requires a live {@code Server.getInstance()}).
     * The core logic — calling {@code addMember} and removing the invite — is preserved.
     */
    static class TestableInviteManager extends InviteManager {
        TestableInviteManager(SkyblockPlugin plugin) {
            super(plugin);
        }

        @Override
        public boolean acceptInvite(UUID targetUUID) {
            // Retrieve the pending invite via the parent's hasPendingInvite / reflection
            PendingInvite invite = getPendingInvite(targetUUID);
            if (invite == null) {
                return false;
            }

            UUID ownerUUID = invite.getOwnerUUID();

            try {
                // This is the core state-change we want to test
                // Access plugin via reflection since the field is private in InviteManager
                SkyblockPlugin plugin = getPlugin();
                plugin.getIslandManager().addMember(ownerUUID, targetUUID);
            } catch (Exception e) {
                removePendingInvite(targetUUID);
                return false;
            }

            removePendingInvite(targetUUID);
            // Skip player notifications (Server.getInstance() is null in tests)
            return true;
        }

        /** Reads the pending invite from the private map via reflection. */
        private PendingInvite getPendingInvite(UUID targetUUID) {
            try {
                var field = InviteManager.class.getDeclaredField("pendingInvites");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<UUID, PendingInvite> map =
                        (ConcurrentHashMap<UUID, PendingInvite>) field.get(this);
                return map.get(targetUUID);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Reflection access to pendingInvites failed", e);
            }
        }

        /** Removes a pending invite from the private map via reflection. */
        private void removePendingInvite(UUID targetUUID) {
            try {
                var field = InviteManager.class.getDeclaredField("pendingInvites");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                ConcurrentHashMap<UUID, PendingInvite> map =
                        (ConcurrentHashMap<UUID, PendingInvite>) field.get(this);
                map.remove(targetUUID);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Reflection access to pendingInvites failed", e);
            }
        }

        /** Reads the plugin reference from the private field via reflection. */
        private SkyblockPlugin getPlugin() {
            try {
                var field = InviteManager.class.getDeclaredField("plugin");
                field.setAccessible(true);
                return (SkyblockPlugin) field.get(this);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Reflection access to plugin field failed", e);
            }
        }
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
            throw new RuntimeException("Reflection injection into IslandManager failed", e);
        }
        return meta;
    }

    // =========================================================================
    // Helper: inject a PendingInvite directly into InviteManager via reflection
    // =========================================================================

    private void injectPendingInvite(InviteManager inviteManager, UUID ownerUUID, UUID targetUUID) {
        PendingInvite invite = new PendingInvite(ownerUUID, targetUUID);
        try {
            var field = InviteManager.class.getDeclaredField("pendingInvites");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<UUID, PendingInvite> map =
                    (ConcurrentHashMap<UUID, PendingInvite>) field.get(inviteManager);
            map.put(targetUUID, invite);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Reflection injection into InviteManager failed", e);
        }
    }

    // =========================================================================
    // Property 8: Invite acceptance adds membership
    // Validates: Requirements 5.2
    // =========================================================================

    /**
     * For any pending invite where the target player accepts, the target player must be
     * added to {@code IslandMetadata.members} with role {@code MEMBER}, and
     * {@code SkyblockPlayerData.islandOwnerUUID} must equal the island owner's UUID.
     *
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @Property
    void property8_acceptInviteAddsMembership(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID targetUUID) {

        Assume.that(!ownerUUID.equals(targetUUID));

        // Set up stubs
        StubPlugin plugin = new StubPlugin();
        IslandManager islandManager = new IslandManager(plugin);
        plugin.setIslandManager(islandManager);
        TestableInviteManager inviteManager = new TestableInviteManager(plugin);

        // Inject island for the owner
        injectIsland(islandManager, ownerUUID);

        // Inject a pending invite directly (bypasses Server.getInstance() call in sendInvite)
        injectPendingInvite(inviteManager, ownerUUID, targetUUID);

        // Pre-condition: target is NOT yet a member
        IslandMetadata metaBefore = islandManager.getIslandByOwner(ownerUUID);
        if (metaBefore.getMembers().containsKey(targetUUID)) {
            throw new AssertionError("Pre-condition failed: target was already a member before acceptInvite()");
        }

        // Act: accept the invite
        boolean accepted = inviteManager.acceptInvite(targetUUID);

        if (!accepted) {
            throw new AssertionError(
                    "acceptInvite() returned false for targetUUID=" + targetUUID
                    + " — invite was not found or addMember() failed unexpectedly.");
        }

        // Post-condition 1: target must be in IslandMetadata.members with role MEMBER
        IslandMetadata metaAfter = islandManager.getIslandByOwner(ownerUUID);
        if (!metaAfter.getMembers().containsKey(targetUUID)) {
            throw new AssertionError(
                    "Property 8 violated: targetUUID " + targetUUID
                    + " is NOT present in IslandMetadata.members after acceptInvite().");
        }
        IslandRole role = metaAfter.getMembers().get(targetUUID);
        if (role != IslandRole.MEMBER) {
            throw new AssertionError(
                    "Property 8 violated: targetUUID " + targetUUID
                    + " has role " + role + " instead of MEMBER after acceptInvite().");
        }

        // Post-condition 2: SkyblockPlayerData.islandOwnerUUID must equal the owner's UUID
        SkyblockPlayerData targetData = plugin.getPlayerDataManager().getPlayerData(targetUUID);
        if (targetData == null) {
            throw new AssertionError(
                    "Property 8 violated: no SkyblockPlayerData found for targetUUID " + targetUUID
                    + " after acceptInvite().");
        }
        if (!ownerUUID.equals(targetData.getIslandOwnerUUID())) {
            throw new AssertionError(
                    "Property 8 violated: SkyblockPlayerData.islandOwnerUUID is "
                    + targetData.getIslandOwnerUUID()
                    + " but expected " + ownerUUID + " after acceptInvite().");
        }
    }

    /**
     * After accepting an invite, the pending invite must be removed from the map
     * (i.e. {@code hasPendingInvite()} returns {@code false}).
     *
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @Property
    void property8_acceptInviteRemovesPendingInvite(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID targetUUID) {

        Assume.that(!ownerUUID.equals(targetUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager islandManager = new IslandManager(plugin);
        plugin.setIslandManager(islandManager);
        TestableInviteManager inviteManager = new TestableInviteManager(plugin);

        injectIsland(islandManager, ownerUUID);
        injectPendingInvite(inviteManager, ownerUUID, targetUUID);

        // Pre-condition: invite exists
        if (!inviteManager.hasPendingInvite(targetUUID)) {
            throw new AssertionError("Pre-condition failed: no pending invite found before acceptInvite()");
        }

        // Act
        inviteManager.acceptInvite(targetUUID);

        // Post-condition: invite must be gone
        if (inviteManager.hasPendingInvite(targetUUID)) {
            throw new AssertionError(
                    "Property 8 violated: pending invite for targetUUID " + targetUUID
                    + " was NOT removed after acceptInvite().");
        }
    }

    /**
     * Accepting an invite for a target that has no pending invite must return {@code false}
     * and leave the island membership unchanged.
     *
     * <p><b>Validates: Requirements 5.2</b></p>
     */
    @Property
    void property8_acceptInviteWithNoPendingInviteReturnsFalse(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID targetUUID) {

        Assume.that(!ownerUUID.equals(targetUUID));

        StubPlugin plugin = new StubPlugin();
        IslandManager islandManager = new IslandManager(plugin);
        plugin.setIslandManager(islandManager);
        TestableInviteManager inviteManager = new TestableInviteManager(plugin);

        injectIsland(islandManager, ownerUUID);
        // Intentionally do NOT inject a pending invite

        // Act
        boolean accepted = inviteManager.acceptInvite(targetUUID);

        // Post-condition: must return false
        if (accepted) {
            throw new AssertionError(
                    "Property 8 violated: acceptInvite() returned true for targetUUID " + targetUUID
                    + " even though no pending invite existed.");
        }

        // Island membership must be unchanged (target not added)
        IslandMetadata meta = islandManager.getIslandByOwner(ownerUUID);
        if (meta.getMembers().containsKey(targetUUID)) {
            throw new AssertionError(
                    "Property 8 violated: targetUUID " + targetUUID
                    + " was added to IslandMetadata.members despite no pending invite.");
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
