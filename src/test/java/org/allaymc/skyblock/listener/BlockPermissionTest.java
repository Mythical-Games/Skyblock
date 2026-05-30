package org.allaymc.skyblock.listener;

// Feature: skyblock-plugin, Property 4: Block permission enforcement

import net.jqwik.api.*;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.world.Dimension;
import org.allaymc.api.world.World;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for Property 4: Block permission enforcement.
 *
 * <p>For any island world and any player, {@code BlockPermissionListener.isAllowed()}
 * returns {@code true} if and only if the player's UUID is the island owner or is
 * present in the island's member map.</p>
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.3, 7.4</b></p>
 */
class BlockPermissionTest {

    // =========================================================================
    // Helpers: build a stub IslandManager backed by a pre-populated metadata map
    // =========================================================================

    /**
     * Builds a minimal {@link IslandMetadata} with the given owner and members.
     */
    private IslandMetadata buildMeta(UUID ownerUUID, Set<UUID> memberUUIDs) {
        IslandMetadata meta = new IslandMetadata();
        meta.setOwnerUUID(ownerUUID);
        meta.setOwnerName(ownerUUID.toString());
        String worldName = "skyblock_" + ownerUUID;
        meta.setWorldName(worldName);
        meta.setSpawnX(0.0);
        meta.setSpawnY(66.0);
        meta.setSpawnZ(0.0);
        meta.setSpawnYaw(0.0f);
        meta.setSpawnPitch(0.0f);
        meta.setIslandLevel(0L);
        meta.setCreatedAt(System.currentTimeMillis());

        Map<UUID, IslandRole> members = new HashMap<>();
        for (UUID m : memberUUIDs) {
            members.put(m, IslandRole.MEMBER);
        }
        meta.setMembers(members);
        return meta;
    }

    /**
     * Injects an {@link IslandMetadata} directly into the {@code IslandManager}'s
     * private {@code islands} map via reflection.
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
            throw new RuntimeException("Reflection injection failed", e);
        }
    }

    /**
     * Creates a mock {@link Dimension} whose {@link World} returns the given world name.
     */
    private Dimension mockDimension(String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Dimension dimension = mock(Dimension.class);
        when(dimension.getWorld()).thenReturn(world);
        return dimension;
    }

    /**
     * Creates a mock {@link EntityPlayer} with the given UUID.
     */
    private EntityPlayer mockPlayer(UUID uuid) {
        EntityPlayer player = mock(EntityPlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    /**
     * Builds a {@link BlockPermissionListener} backed by an {@link IslandManager}
     * that has the given island pre-loaded.
     */
    private BlockPermissionListener buildListener(IslandManager manager) {
        return new BlockPermissionListener(manager);
    }

    // =========================================================================
    // Property 4a: Owner is always allowed
    // Validates: Requirements 7.1, 7.2
    // =========================================================================

    /**
     * For any island, the owner UUID must always be allowed to build.
     *
     * <p><b>Validates: Requirements 7.1, 7.2</b></p>
     */
    @Property
    void property4_ownerIsAlwaysAllowed(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("memberSet") Set<UUID> memberUUIDs) {

        // Ensure owner is not in the member set (degenerate case)
        Assume.that(!memberUUIDs.contains(ownerUUID));

        IslandManager manager = new IslandManager(null);
        IslandMetadata meta = buildMeta(ownerUUID, memberUUIDs);
        injectIsland(manager, ownerUUID, meta);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(ownerUUID);
        Dimension dimension = mockDimension(meta.getWorldName());

        boolean result = listener.isAllowed(player, dimension);

        if (!result) {
            throw new AssertionError(
                    "Property 4 violated: owner " + ownerUUID
                    + " was denied permission on their own island.");
        }
    }

    // =========================================================================
    // Property 4b: Member is always allowed
    // Validates: Requirements 7.1, 7.3
    // =========================================================================

    /**
     * For any island, every UUID in the members map must be allowed to build.
     *
     * <p><b>Validates: Requirements 7.1, 7.3</b></p>
     */
    @Property
    void property4_memberIsAlwaysAllowed(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID memberUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));

        Set<UUID> memberSet = new HashSet<>();
        memberSet.add(memberUUID);

        IslandManager manager = new IslandManager(null);
        IslandMetadata meta = buildMeta(ownerUUID, memberSet);
        injectIsland(manager, ownerUUID, meta);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(memberUUID);
        Dimension dimension = mockDimension(meta.getWorldName());

        boolean result = listener.isAllowed(player, dimension);

        if (!result) {
            throw new AssertionError(
                    "Property 4 violated: member " + memberUUID
                    + " was denied permission on island owned by " + ownerUUID);
        }
    }

    // =========================================================================
    // Property 4c: Stranger (non-owner, non-member) is always denied
    // Validates: Requirements 7.1, 7.4
    // =========================================================================

    /**
     * For any island, a player who is neither the owner nor a member must be denied.
     *
     * <p><b>Validates: Requirements 7.1, 7.4</b></p>
     */
    @Property
    void property4_strangerIsAlwaysDenied(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID strangerUUID) {

        Assume.that(!ownerUUID.equals(strangerUUID));

        // Island has no members — stranger is definitely not owner or member
        IslandManager manager = new IslandManager(null);
        IslandMetadata meta = buildMeta(ownerUUID, Collections.emptySet());
        injectIsland(manager, ownerUUID, meta);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(strangerUUID);
        Dimension dimension = mockDimension(meta.getWorldName());

        boolean result = listener.isAllowed(player, dimension);

        if (result) {
            throw new AssertionError(
                    "Property 4 violated: stranger " + strangerUUID
                    + " was granted permission on island owned by " + ownerUUID
                    + " (stranger is neither owner nor member).");
        }
    }

    // =========================================================================
    // Property 4d: Stranger with members present is still denied
    // Validates: Requirements 7.1, 7.4
    // =========================================================================

    /**
     * A player who is not the owner and not in the members map must be denied,
     * even when the island has other members.
     *
     * <p><b>Validates: Requirements 7.1, 7.4</b></p>
     */
    @Property
    void property4_nonMemberDeniedEvenWithOtherMembers(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("randomUUID") UUID memberUUID,
            @ForAll("randomUUID") UUID strangerUUID) {

        Assume.that(!ownerUUID.equals(memberUUID));
        Assume.that(!ownerUUID.equals(strangerUUID));
        Assume.that(!memberUUID.equals(strangerUUID));

        Set<UUID> memberSet = new HashSet<>();
        memberSet.add(memberUUID);

        IslandManager manager = new IslandManager(null);
        IslandMetadata meta = buildMeta(ownerUUID, memberSet);
        injectIsland(manager, ownerUUID, meta);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(strangerUUID);
        Dimension dimension = mockDimension(meta.getWorldName());

        boolean result = listener.isAllowed(player, dimension);

        if (result) {
            throw new AssertionError(
                    "Property 4 violated: stranger " + strangerUUID
                    + " was granted permission on island owned by " + ownerUUID
                    + " (only member " + memberUUID + " should be allowed).");
        }
    }

    // =========================================================================
    // Property 4e: No island metadata → always denied
    // Validates: Requirements 7.1, 7.4
    // =========================================================================

    /**
     * When no island metadata exists for the world, {@code isAllowed()} must return
     * {@code false} for any player — including the world name's embedded UUID.
     *
     * <p><b>Validates: Requirements 7.1, 7.4</b></p>
     */
    @Property
    void property4_noMetadataAlwaysDenied(
            @ForAll("randomUUID") UUID playerUUID,
            @ForAll("randomUUID") UUID worldOwnerUUID) {

        // Empty IslandManager — no islands registered
        IslandManager manager = new IslandManager(null);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(playerUUID);
        // Use a world name that looks like an island world but has no registered metadata
        Dimension dimension = mockDimension("skyblock_" + worldOwnerUUID);

        boolean result = listener.isAllowed(player, dimension);

        if (result) {
            throw new AssertionError(
                    "Property 4 violated: player " + playerUUID
                    + " was granted permission in world skyblock_" + worldOwnerUUID
                    + " which has no registered island metadata.");
        }
    }

    // =========================================================================
    // Property 4f: isAllowed iff owner or member (combined biconditional)
    // Validates: Requirements 7.1, 7.2, 7.3, 7.4
    // =========================================================================

    /**
     * The biconditional: {@code isAllowed(player, dimension)} returns {@code true}
     * if and only if the player UUID equals the owner UUID or is a key in the members map.
     *
     * <p>This is the core statement of Property 4.</p>
     *
     * <p><b>Validates: Requirements 7.1, 7.2, 7.3, 7.4</b></p>
     */
    @Property
    void property4_isAllowedIffOwnerOrMember(
            @ForAll("randomUUID") UUID ownerUUID,
            @ForAll("memberSet") Set<UUID> memberUUIDs,
            @ForAll("randomUUID") UUID playerUUID) {

        // Ensure the player is not accidentally the owner (we test that separately)
        // We allow playerUUID == ownerUUID to test the owner case too
        Set<UUID> safeMembers = new HashSet<>(memberUUIDs);
        safeMembers.remove(ownerUUID); // owner is tracked separately, not in members map

        IslandManager manager = new IslandManager(null);
        IslandMetadata meta = buildMeta(ownerUUID, safeMembers);
        injectIsland(manager, ownerUUID, meta);

        BlockPermissionListener listener = buildListener(manager);
        EntityPlayer player = mockPlayer(playerUUID);
        Dimension dimension = mockDimension(meta.getWorldName());

        boolean result = listener.isAllowed(player, dimension);

        // Expected: true iff player is owner OR in members map
        boolean expectedAllowed = playerUUID.equals(ownerUUID) || safeMembers.contains(playerUUID);

        if (result != expectedAllowed) {
            throw new AssertionError(
                    "Property 4 violated: isAllowed returned " + result
                    + " but expected " + expectedAllowed
                    + " for playerUUID=" + playerUUID
                    + ", ownerUUID=" + ownerUUID
                    + ", members=" + safeMembers);
        }
    }

    // =========================================================================
    // Arbitrary providers
    // =========================================================================

    @Provide
    Arbitrary<UUID> randomUUID() {
        return Arbitraries.randomValue(r -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<Set<UUID>> memberSet() {
        // Generate sets of 0–4 random UUIDs to represent island member sets
        return Arbitraries.randomValue(r -> UUID.randomUUID())
                .set()
                .ofMaxSize(4);
    }
}
