package org.allaymc.skyblock.listener;

import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.block.BlockBreakEvent;
import org.allaymc.api.eventbus.event.block.BlockPlaceEvent;
import org.allaymc.api.world.Dimension;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.model.IslandMetadata;

/**
 * Listens to block place and break events and enforces island permissions.
 *
 * <p>A player may only place or break blocks in an island world if they are
 * the island owner or a member of that island.</p>
 *
 * <p>Requirements: 7.1, 7.2, 7.3, 7.4</p>
 */
public class BlockPermissionListener {

    private static final String PERMISSION_DENIED_MESSAGE =
            "§cYou don't have permission to build on this island.";

    private final IslandManager islandManager;

    /**
     * Constructs a {@code BlockPermissionListener} with the given {@link IslandManager}.
     *
     * @param islandManager the island manager used to look up island metadata
     */
    public BlockPermissionListener(IslandManager islandManager) {
        this.islandManager = islandManager;
    }

    /**
     * Handles block placement events.
     *
     * <p>If the block is being placed in an island world and the placing player is not
     * allowed to build there, the event is cancelled and a denial message is sent.</p>
     *
     * @param event the block place event
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        var interactInfo = event.getInteractInfo();
        if (interactInfo == null) {
            // Not placed by a player — allow non-player placements (e.g. dispensers)
            return;
        }

        EntityPlayer entityPlayer = interactInfo.player();
        if (entityPlayer == null) {
            return;
        }

        Dimension dimension = event.getBlock().getDimension();
        if (!isInsideIslandWorld(dimension)) {
            return;
        }

        if (!isAllowed(entityPlayer, dimension)) {
            event.cancel();
            sendDeniedMessage(entityPlayer);
        }
    }

    /**
     * Handles block break events.
     *
     * <p>If the block is being broken in an island world and the breaking player is not
     * allowed to build there, the event is cancelled and a denial message is sent.</p>
     *
     * @param event the block break event
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        var entity = event.getEntity();
        if (!(entity instanceof EntityPlayer entityPlayer)) {
            // Not broken by a player — allow non-player breaks (e.g. explosions)
            return;
        }

        Dimension dimension = event.getBlock().getDimension();
        if (!isInsideIslandWorld(dimension)) {
            return;
        }

        if (!isAllowed(entityPlayer, dimension)) {
            event.cancel();
            sendDeniedMessage(entityPlayer);
        }
    }

    /**
     * Returns {@code true} if the given dimension belongs to any Skyblock island world.
     *
     * @param dimension the dimension to check
     * @return {@code true} if the dimension is an island world
     */
    private boolean isInsideIslandWorld(Dimension dimension) {
        var world = dimension.getWorld();
        if (world == null) {
            return false;
        }
        String worldName = world.getName();
        return worldName != null && worldName.startsWith(IslandManager.WORLD_NAME_PREFIX);
    }

    /**
     * Returns {@code true} if the player is allowed to modify blocks in the given dimension.
     *
     * <p>A player is allowed if their UUID matches the island owner UUID or is present
     * in the island's member map. Returns {@code false} if no island metadata is found
     * for the world (i.e. the world is not a registered island world).</p>
     *
     * @param entityPlayer the player entity attempting the block modification
     * @param dimension    the dimension in which the modification is attempted
     * @return {@code true} if the player is the owner or a member of the island
     */
    boolean isAllowed(EntityPlayer entityPlayer, Dimension dimension) {
        var world = dimension.getWorld();
        if (world == null) {
            return false;
        }

        String worldName = world.getName();
        IslandMetadata meta = islandManager.getIslandByWorld(worldName);
        if (meta == null) {
            return false;
        }

        var playerUUID = entityPlayer.getUniqueId();
        return playerUUID.equals(meta.getOwnerUUID())
                || meta.getMembers().containsKey(playerUUID);
    }

    /**
     * Sends the permission-denied message to the player if they have a controller (i.e. are a real player).
     *
     * @param entityPlayer the player entity to notify
     */
    private void sendDeniedMessage(EntityPlayer entityPlayer) {
        var controller = entityPlayer.getController();
        if (controller != null) {
            controller.sendMessage(PERMISSION_DENIED_MESSAGE);
        }
    }
}
