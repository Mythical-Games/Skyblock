package org.allaymc.skyblock;

import com.google.gson.Gson;
import org.allaymc.api.plugin.Plugin;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.server.Server;
import org.allaymc.skyblock.command.IsCommand;
import org.allaymc.skyblock.island.IslandManager;
import org.allaymc.skyblock.invite.InviteManager;
import org.allaymc.skyblock.level.IslandLevelService;
import org.allaymc.skyblock.level.LeaderboardService;
import org.allaymc.skyblock.listener.BlockPermissionListener;
import org.allaymc.skyblock.listener.PlayerJoinListener;
import org.allaymc.skyblock.model.SkyblockConfig;
import org.allaymc.skyblock.persistence.PersistenceLayer;
import org.allaymc.skyblock.player.PlayerDataManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Entry point for the Skyblock plugin.
 * Extends AllayMC's Plugin base class and manages the lifecycle of all services.
 */
public class SkyblockPlugin extends Plugin {

    private PersistenceLayer persistenceLayer;
    private IslandManager islandManager;
    private PlayerDataManager playerDataManager;
    private IslandLevelService islandLevelService;
    private LeaderboardService leaderboardService;
    private InviteManager inviteManager;
    private SkyblockConfig skyblockConfig;

    /**
     * Set to {@code true} at the end of {@link #onEnable()} once all services are fully
     * initialised. Commands check this flag before processing any sub-command.
     */
    private boolean ready = false;

    @Override
    public void onLoad() {
        pluginLogger.info("Skyblock plugin loading...");

        // Initialise the persistence layer rooted at the plugin data folder
        Path dataFolder = pluginContainer.dataFolder();
        persistenceLayer = new PersistenceLayer(dataFolder);

        // Ensure all required sub-directories exist before anything else touches them
        persistenceLayer.ensureDataDirectories();

        // Load config.json; fall back to defaults when the file is absent
        skyblockConfig = loadOrCreateDefaultConfig(dataFolder);

        pluginLogger.info("Skyblock plugin loaded — data directory ready, config loaded.");
    }

    @Override
    public void onEnable() {
        try {
            // Instantiate all services (persistenceLayer is already set in onLoad)
            playerDataManager = new PlayerDataManager(this);
            islandManager = new IslandManager(this);
            leaderboardService = new LeaderboardService(persistenceLayer);
            islandLevelService = new IslandLevelService(
                    islandManager, skyblockConfig, leaderboardService, persistenceLayer);
            inviteManager = new InviteManager(this);

            // Load persisted data into memory
            playerDataManager.loadAll();
            islandManager.loadAll();

            // Restore leaderboard from loaded island data
            leaderboardService.rebuild(islandManager.getAllIslands());

            // Register the /is command
            Registries.COMMANDS.register(new IsCommand(this));

            // Register the block permission event listener
            Server.getInstance().getEventBus().registerListener(new BlockPermissionListener(islandManager));

            // Register the player join listener — always spawns players in the default world
            Server.getInstance().getEventBus().registerListener(new PlayerJoinListener(this));

            // Schedule invite expiry check every 20 ticks
            Server.getInstance().getScheduler().scheduleRepeating(this, inviteManager::tickExpiry, 20);

            ready = true;
            pluginLogger.info("Skyblock enabled");
        } catch (Exception e) {
            pluginLogger.error("Failed to enable Skyblock plugin", e);
            // Gracefully disable this plugin by invoking its own shutdown
            try {
                onDisable();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        ready = false;

        // Flush all in-memory island data to disk (null-safe: onEnable may never have completed)
        if (islandManager != null) {
            try {
                islandManager.saveAll();
            } catch (Exception e) {
                pluginLogger.error("Failed to save island data during shutdown", e);
            }
        }

        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll();
            } catch (Exception e) {
                pluginLogger.error("Failed to save player data during shutdown", e);
            }
        }

        if (leaderboardService != null) {
            try {
                leaderboardService.saveToDisk(pluginContainer.dataFolder());
            } catch (Exception e) {
                pluginLogger.error("Failed to save leaderboard during shutdown", e);
            }
        }

        pluginLogger.info("Skyblock disabled");
    }

    // -------------------------------------------------------------------------
    // Config helpers
    // -------------------------------------------------------------------------

    /**
     * Reads {@code config.json} from {@code dataFolder}.
     * If the file does not exist, a default {@link SkyblockConfig} is created,
     * written to disk, and returned so that server admins have a template to edit.
     *
     * @param dataFolder the plugin data directory
     * @return the loaded or default {@link SkyblockConfig}
     */
    private SkyblockConfig loadOrCreateDefaultConfig(Path dataFolder) {
        Path configFile = dataFolder.resolve("config.json");
        Gson gson = persistenceLayer.getGson();

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                SkyblockConfig loaded = gson.fromJson(reader, SkyblockConfig.class);
                if (loaded != null) {
                    pluginLogger.info("Loaded config.json from {}", configFile);
                    return loaded;
                }
            } catch (IOException e) {
                pluginLogger.warn("Failed to read config.json ({}); using defaults.", e.getMessage());
            }
        }

        // File absent or unreadable — build defaults and persist them
        SkyblockConfig defaults = buildDefaultConfig();
        try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            gson.toJson(defaults, writer);
            pluginLogger.info("Created default config.json at {}", configFile);
        } catch (IOException e) {
            pluginLogger.warn("Could not write default config.json ({}); continuing with in-memory defaults.", e.getMessage());
        }
        return defaults;
    }

    /**
     * Builds a {@link SkyblockConfig} populated with sensible default values.
     *
     * @return a new default config
     */
    private SkyblockConfig buildDefaultConfig() {
        SkyblockConfig cfg = new SkyblockConfig();

        // Default block values — common Skyblock progression blocks
        HashMap<String, Integer> blockValues = new HashMap<>();
        blockValues.put("minecraft:cobblestone", 1);
        blockValues.put("minecraft:stone", 1);
        blockValues.put("minecraft:dirt", 1);
        blockValues.put("minecraft:grass_block", 1);
        blockValues.put("minecraft:sand", 1);
        blockValues.put("minecraft:gravel", 1);
        blockValues.put("minecraft:oak_log", 2);
        blockValues.put("minecraft:oak_planks", 2);
        blockValues.put("minecraft:iron_ore", 5);
        blockValues.put("minecraft:gold_ore", 10);
        blockValues.put("minecraft:diamond_ore", 50);
        blockValues.put("minecraft:obsidian", 20);
        cfg.setBlockValues(blockValues);

        // Default starter items — classic Skyblock chest contents
        ArrayList<SkyblockConfig.StarterItem> starterItems = new ArrayList<>();
        starterItems.add(starterItem("minecraft:lava_bucket", 1));
        starterItems.add(starterItem("minecraft:ice", 1));
        starterItems.add(starterItem("minecraft:melon_seeds", 1));
        starterItems.add(starterItem("minecraft:pumpkin_seeds", 1));
        starterItems.add(starterItem("minecraft:sugar_cane", 2));
        starterItems.add(starterItem("minecraft:brown_mushroom", 1));
        starterItems.add(starterItem("minecraft:red_mushroom", 1));
        starterItems.add(starterItem("minecraft:cactus", 2));
        cfg.setStarterItems(starterItems);

        return cfg;
    }

    private static SkyblockConfig.StarterItem starterItem(String itemTypeId, int count) {
        SkyblockConfig.StarterItem item = new SkyblockConfig.StarterItem();
        item.setItemTypeId(itemTypeId);
        item.setCount(count);
        return item;
    }

    /**
     * Returns {@code true} if the plugin has finished enabling and all services are ready.
     *
     * @return {@code true} if the plugin is ready
     */
    public boolean isReady() {
        return ready;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public PersistenceLayer getPersistenceLayer() {
        return persistenceLayer;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public IslandLevelService getIslandLevelService() {
        return islandLevelService;
    }

    public LeaderboardService getLeaderboardService() {
        return leaderboardService;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public SkyblockConfig getSkyblockConfig() {
        return skyblockConfig;
    }
}
