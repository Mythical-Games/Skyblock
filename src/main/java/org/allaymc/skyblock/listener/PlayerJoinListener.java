package org.allaymc.skyblock.listener;

import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.server.PlayerJoinEvent;
import org.allaymc.api.eventbus.event.server.PlayerQuitEvent;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.math.location.Location3ic;
import org.allaymc.api.server.Server;
import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.island.IslandManager;

import java.util.UUID;

/**
 * Handles player join events for Vibe-Skyblock:
 * <ol>
 *   <li>If the player has an island, ensure the island world is loaded so that
 *       {@code /is home} and block-permission checks work immediately.</li>
 *   <li>If the player's saved position is inside a Skyblock island world (e.g.
 *       they disconnected while on their island), teleport them to the default
 *       world spawn instead — the island world may not be loaded yet at this point.</li>
 * </ol>
 */
public class PlayerJoinListener {

    private final SkyblockPlugin plugin;

    public PlayerJoinListener(SkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (player == null) return;

        var entity = player.getControlledEntity();
        if (entity == null) return;

        UUID playerUUID = entity.getUniqueId();

        // ── Step 1: load the island world if the player owns/belongs to one ──
        if (plugin.getPlayerDataManager().hasIsland(playerUUID)) {
            var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(playerUUID);
            if (ownerUUID != null) {
                // Run on a separate thread to avoid blocking the network thread
                // (world loading can take a moment for large LevelDB stores).
                Thread.ofVirtual().name("island-world-loader-" + ownerUUID).start(() ->
                        plugin.getIslandManager().ensureIslandWorldLoaded(ownerUUID)
                );
            }
        }

        // ── Step 2: redirect to default spawn if saved in an island world ──
        var dimension = entity.getDimension();
        if (dimension == null) return;

        var world = dimension.getWorld();
        if (world == null) return;

        if (world.getName().startsWith(IslandManager.WORLD_NAME_PREFIX)) {
            Location3ic spawnPoint = Server.getInstance().getWorldPool().getGlobalSpawnPoint();
            var spawnLoc = new Location3d(
                    spawnPoint.x(),
                    spawnPoint.y(),
                    spawnPoint.z(),
                    0.0,
                    0.0,
                    spawnPoint.dimension()
            );
            entity.teleport(spawnLoc);
        }
    }

    /**
     * Fires when a fully-spawned player quits the server.
     *
     * <p>If the quitting player owns an island, unload that island world —
     * but only if no other online players are still inside it.</p>
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (player == null) return;

        var entity = player.getControlledEntity();
        if (entity == null) return;

        UUID playerUUID = entity.getUniqueId();

        if (!plugin.getPlayerDataManager().hasIsland(playerUUID)) return;

        var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(playerUUID);
        if (ownerUUID == null) return;

        var meta = plugin.getIslandManager().getIslandByOwner(ownerUUID);
        if (meta == null) return;

        String worldName = meta.getWorldName();
        var world = Server.getInstance().getWorldPool().getWorld(worldName);
        if (world == null) return; // already unloaded

        // Only unload if no other players remain in the island world
        // Note: at the time PlayerQuitEvent fires the leaving player may still be
        // counted in getPlayers(), so we treat "1 or fewer" as empty.
        if (world.getPlayers().size() <= 1) {
            try {
                Server.getInstance().getWorldPool().unloadWorld(worldName);
                plugin.getPluginLogger().info(
                        "Unloaded island world '{}' — no players remaining.", worldName);
            } catch (Exception e) {
                plugin.getPluginLogger().warn(
                        "Failed to unload island world '{}': {}", worldName, e.getMessage());
            }
        }
    }
}
