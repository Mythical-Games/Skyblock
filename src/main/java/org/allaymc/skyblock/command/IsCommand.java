package org.allaymc.skyblock.command;

import org.allaymc.api.command.Command;
import org.allaymc.api.command.CommandResult;
import org.allaymc.api.command.CommandSender;
import org.allaymc.api.command.SenderType;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.form.Forms;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;
import org.allaymc.api.utils.TextFormat;
import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.island.IslandCreationException;
import org.allaymc.skyblock.island.IslandException;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.LeaderboardEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Root command handler for {@code /is} (Skyblock island commands).
 *
 * <p>All 10 sub-commands are registered in {@link #prepareCommandTree(CommandTree)}.
 * Sub-commands {@code create}, {@code home}, and {@code sethome} are fully implemented
 * here; the remaining seven ({@code invite}, {@code kick}, {@code leave}, {@code level},
 * {@code top}, {@code delete}, {@code reset}) are registered as stubs and will be
 * completed in tasks 12.2–12.5.</p>
 *
 * <p>Requirements: 2.1, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 11.1, 11.2, 11.3, 11.4</p>
 */
public class IsCommand extends Command {

    private final SkyblockPlugin plugin;

    /**
     * Pending confirmation map for destructive actions ({@code delete} and {@code reset}).
     * Key: player UUID; Value: the action name ("delete" or "reset").
     * A player must invoke the same sub-command twice to confirm the destructive action.
     */
    private final ConcurrentHashMap<UUID, String> pendingConfirmations = new ConcurrentHashMap<>();

    /**
     * Constructs the {@code /is} command.
     *
     * @param plugin the owning {@link SkyblockPlugin} instance
     */
    public IsCommand(SkyblockPlugin plugin) {
        super("is", "Skyblock island commands", null);
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Fallback: unrecognised sub-command help (Requirement 11.2)
    // -------------------------------------------------------------------------

    /**
     * Overrides the default execute method. When the player runs bare {@code /is}
     * (no sub-command matched), open the context-sensitive island menu form.
     *
     * <p>Requirements: 11.2</p>
     */
    @Override
    public CommandResult execute(CommandSender sender, String[] args) {
        var result = super.execute(sender, args);
        if (!result.isSuccess() && sender.isPlayer()) {
            openIslandMenu(sender.asPlayer());
        }
        return result;
    }

    /**
     * Opens the island menu form for the player.
     * <ul>
     *   <li>No island → single "Create Island" button.</li>
     *   <li>Has island → all management buttons (Home, Set Home, Invite, Kick,
     *       Leave, Level, Top Islands, Reset, Delete).</li>
     * </ul>
     */
    private void openIslandMenu(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return;
        }

        var controller = player.getController();
        if (controller == null) return;

        boolean hasIsland = plugin.getPlayerDataManager().hasIsland(player.getUniqueId());

        if (!hasIsland) {
            // ── No island: single Create button ──
            Forms.simple()
                    .title("§6§lVibe Skyblock")
                    .content("§7You don't have an island yet.\n§aCreate one to get started!")
                    .button("§a\uE100 §lCreate Island")
                    .onClick(btn -> executeCreate(player))
                    .sendTo(controller);
        } else {
            // ── Has island: all management buttons ──
            Forms.simple()
                    .title("§6§lVibe Skyblock")
                    .content("§7Manage your island:")
                    .button("§b\uE108 §lGo Home")
                    .onClick(btn -> executeHome(player))
                    .button("§b\uE109 §lSet Home")
                    .onClick(btn -> executeSetHome(player))
                    .button("§e\uE107 §lIsland Level")
                    .onClick(btn -> executeLevel(player))
                    .button("§e\uE106 §lTop Islands")
                    .onClick(btn -> executeTop(player))
                    .button("§a\uE10A §lInvite Player")
                    .onClick(btn -> player.sendMessage("§7Use: §f/is invite <player>"))
                    .button("§c\uE101 §lKick Member")
                    .onClick(btn -> player.sendMessage("§7Use: §f/is kick <member>"))
                    .button("§f\uE10C §lLeave Island")
                    .onClick(btn -> executeLeave(player))
                    .button("§6\uE10B §lReset Island")
                    .onClick(btn -> executeReset(player))
                    .button("§d\uE105 §lRename Island")
                    .onClick(btn -> openRenameForm(player))
                    .button("§4\uE102 §lDelete Island")
                    .onClick(btn -> executeDelete(player))
                    .sendTo(controller);
        }
    }

    // -------------------------------------------------------------------------
    // Command tree registration
    // -------------------------------------------------------------------------

    @Override
    public void prepareCommandTree(CommandTree tree) {
        var root = tree.getRoot();

        // /is create
        root.key("create")
                .exec((context, player) -> executeCreate(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is home
                .key("home")
                .exec((context, player) -> executeHome(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is sethome
                .key("sethome")
                .exec((context, player) -> executeSetHome(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is invite <player> — task 12.2
                .key("invite")
                .str("player")
                .exec((context, player) -> executeInvite(context.getSender().asPlayer(), context.getResult(0)), SenderType.PLAYER)
                .root()

        // /is kick <member> — task 12.2
                .key("kick")
                .str("member")
                .exec((context, player) -> executeKick(context.getSender().asPlayer(), context.getResult(0)), SenderType.PLAYER)
                .root()

        // /is leave — task 12.2
                .key("leave")
                .exec((context, player) -> executeLeave(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is level (task 12.3)
                .key("level")
                .exec((context, player) -> executeLevel(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is top (task 12.3)
                .key("top")
                .exec((context, player) -> executeTop(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is delete — task 12.4
                .key("delete")
                .exec((context, player) -> executeDelete(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is reset — task 12.4
                .key("reset")
                .exec((context, player) -> executeReset(context.getSender().asPlayer()), SenderType.PLAYER)
                .root()

        // /is rename <name>
                .key("rename")
                .str("name")
                .exec((context, player) -> executeRename(context.getSender().asPlayer(), context.getResult(0)), SenderType.PLAYER);
    }

    // -------------------------------------------------------------------------
    // Implemented sub-commands
    // -------------------------------------------------------------------------

    /**
     * Handles {@code /is create}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the player does not already have an island.</li>
     *   <li>Calls {@link org.allaymc.skyblock.island.IslandManager#createIsland(java.util.UUID)}.</li>
     *   <li>Teleports the player to the new island spawn point.</li>
     *   <li>Sends a success message.</li>
     * </ol>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeCreate(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        if (plugin.getPlayerDataManager().hasIsland(uuid)) {
            player.sendMessage(TextFormat.RED + "You already have an island! Use /is home to go there.");
            return CommandResult.fail(null);
        }

        IslandMetadata meta;
        try {
            meta = plugin.getIslandManager().createIsland(uuid);
        } catch (IslandCreationException e) {
            player.sendMessage(TextFormat.RED + "Failed to create your island: " + e.getMessage());
            return CommandResult.fail(null);
        }

        // Teleport the player to the island spawn point
        var world = Server.getInstance().getWorldPool().getWorld(meta.getWorldName());
        if (world != null) {
            var dimension = world.getOverWorld();
            var spawnLoc = new Location3d(
                    meta.getSpawnX(), meta.getSpawnY(), meta.getSpawnZ(),
                    meta.getSpawnPitch(), meta.getSpawnYaw(),
                    dimension
            );
            player.teleport(spawnLoc);
        }

        player.sendMessage(TextFormat.GREEN + "Your island has been created! Welcome to Skyblock!");
        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is home}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the player has an island via {@link org.allaymc.skyblock.player.PlayerDataManager#hasIsland}.</li>
     *   <li>Teleports the player to the stored island spawn point.</li>
     * </ol>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeHome(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        if (!plugin.getPlayerDataManager().hasIsland(uuid)) {
            player.sendMessage(TextFormat.YELLOW + "You don't have an island yet. Use /is create to make one!");
            return CommandResult.fail(null);
        }

        // Resolve the island owner (may be the player themselves or their island's owner)
        var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(uuid);
        var meta = plugin.getIslandManager().getIslandByOwner(ownerUUID);

        if (meta == null) {
            player.sendMessage(TextFormat.RED + "Could not find your island data. Please contact an admin.");
            return CommandResult.fail(null);
        }

        var world = Server.getInstance().getWorldPool().getWorld(meta.getWorldName());
        if (world == null) {
            player.sendMessage(TextFormat.RED + "Your island world is not loaded. Please contact an admin.");
            return CommandResult.fail(null);
        }

        var dimension = world.getOverWorld();
        var spawnLoc = new Location3d(
                meta.getSpawnX(), meta.getSpawnY(), meta.getSpawnZ(),
                meta.getSpawnPitch(), meta.getSpawnYaw(),
                dimension
        );
        player.teleport(spawnLoc);
        player.sendMessage(TextFormat.GREEN + "Teleported to your island!");
        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is sethome}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the player is inside their own island world via
     *       {@link org.allaymc.skyblock.island.IslandManager#isInsideOwnIslandWorld}.</li>
     *   <li>Calls {@link org.allaymc.skyblock.island.IslandManager#setSpawnPoint} with the player's current location.</li>
     *   <li>Sends a success message.</li>
     * </ol>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeSetHome(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        // isInsideOwnIslandWorld takes a Player (controller), not EntityPlayer
        var controller = player.getController();
        if (controller == null || !plugin.getIslandManager().isInsideOwnIslandWorld(controller)) {
            player.sendMessage(sethomeNotInOwnIslandError());
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();
        var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(uuid);
        // getLocation() returns Location3dc; wrap in Location3d for setSpawnPoint
        var location = new Location3d(player.getLocation());

        plugin.getIslandManager().setSpawnPoint(ownerUUID, location);
        player.sendMessage(TextFormat.GREEN + "Island home point updated to your current location!");
        return CommandResult.success(null);
    }

    // -------------------------------------------------------------------------
    // Level and leaderboard sub-commands (task 12.3)
    // -------------------------------------------------------------------------

    /**
     * Handles {@code /is level}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the player has an island via {@link org.allaymc.skyblock.player.PlayerDataManager#hasIsland}.</li>
     *   <li>Calls {@link org.allaymc.skyblock.level.IslandLevelService#computeAndSave} to scan and persist the level.</li>
     *   <li>Displays the computed level to the player.</li>
     * </ol>
     *
     * <p>Requirements: 8.2, 8.3, 11.1</p>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeLevel(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        if (!plugin.getPlayerDataManager().hasIsland(uuid)) {
            player.sendMessage(TextFormat.YELLOW + "You don't have an island yet. Use /is create to make one!");
            return CommandResult.fail(null);
        }

        // Resolve the island owner UUID (handles both owners and members)
        var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(uuid);

        long level;
        try {
            level = plugin.getIslandLevelService().computeAndSave(ownerUUID);
        } catch (IllegalArgumentException | IllegalStateException e) {
            player.sendMessage(TextFormat.RED + "Failed to compute island level: " + e.getMessage());
            return CommandResult.fail(null);
        }

        player.sendMessage(TextFormat.GREEN + "Your island level is: " + TextFormat.GOLD + level);
        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is top}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Calls {@link org.allaymc.skyblock.level.LeaderboardService#getTop10} to retrieve the ranked list.</li>
     *   <li>Formats and displays each entry as {@code #rank  ownerName  level}.</li>
     *   <li>If fewer than 10 islands exist, only the available entries are shown (no empty rows).</li>
     * </ol>
     *
     * <p>Requirements: 9.1, 9.3, 11.1</p>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeTop(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        List<LeaderboardEntry> top10 = plugin.getLeaderboardService().getTop10();

        if (top10.isEmpty()) {
            player.sendMessage(TextFormat.YELLOW + "No islands have been leveled yet. Use /is level to compute yours!");
            return CommandResult.success(null);
        }

        player.sendMessage(TextFormat.GOLD + "=== Top Islands ===");
        for (int i = 0; i < top10.size(); i++) {
            LeaderboardEntry entry = top10.get(i);
            String rank = TextFormat.YELLOW + "#" + (i + 1) + " ";
            String name = TextFormat.WHITE + entry.getOwnerName();
            String level = TextFormat.AQUA + " - Level " + entry.getLevel();
            player.sendMessage(rank + name + level);
        }

        return CommandResult.success(null);
    }

    // -------------------------------------------------------------------------
    // Task 12.2 sub-commands: invite, kick, leave
    // -------------------------------------------------------------------------

    /**
     * Handles {@code /is invite <player>}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the caller is the owner of an island.</li>
     *   <li>Looks up the target player by name; sends an error if offline.</li>
     *   <li>Calls {@link org.allaymc.skyblock.invite.InviteManager#sendInvite}.</li>
     * </ol>
     *
     * <p>Requirements: 5.1, 5.3, 5.4, 11.1, 11.3</p>
     *
     * @param player     the player who issued the command
     * @param targetName the name of the player to invite
     * @return the command result
     */
    private CommandResult executeInvite(EntityPlayer player, String targetName) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        // Requirement 11.3: validate that the player name argument is present
        if (isInviteArgMissing(targetName)) {
            player.sendMessage(inviteUsageHint());
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        // Caller must be an island owner
        if (plugin.getPlayerDataManager().getRole(uuid) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "You must be an island owner to invite players.");
            return CommandResult.fail(null);
        }

        // Resolve the target player by name
        Player target = Server.getInstance().getPlayerManager().getPlayerByName(targetName);
        if (target == null) {
            player.sendMessage(TextFormat.RED + "Player '" + targetName + "' is not online.");
            return CommandResult.fail(null);
        }

        var targetUUID = target.getControlledEntity().getUniqueId();

        // Delegate to InviteManager — it handles already-membered and offline checks internally
        // and sends appropriate error messages to the owner.
        plugin.getInviteManager().sendInvite(uuid, targetUUID);
        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is kick <member>}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the caller is the owner of an island.</li>
     *   <li>Resolves the target by name (online or via stored player data).</li>
     *   <li>Verifies the target is a member of the caller's island.</li>
     *   <li>Calls {@link org.allaymc.skyblock.island.IslandManager#removeMember}.</li>
     *   <li>Notifies both parties.</li>
     * </ol>
     *
     * <p>Requirements: 6.1, 6.3, 11.1, 11.3</p>
     *
     * @param player     the player who issued the command
     * @param memberName the name of the member to kick
     * @return the command result
     */
    private CommandResult executeKick(EntityPlayer player, String memberName) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        // Requirement 11.3: validate that the member name argument is present
        if (isKickArgMissing(memberName)) {
            player.sendMessage(kickUsageHint());
            return CommandResult.fail(null);
        }

        var ownerUUID = player.getUniqueId();

        // Caller must be an island owner
        if (plugin.getPlayerDataManager().getRole(ownerUUID) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "You must be an island owner to kick members.");
            return CommandResult.fail(null);
        }

        var meta = plugin.getIslandManager().getIslandByOwner(ownerUUID);
        if (meta == null) {
            player.sendMessage(TextFormat.RED + "You don't have an island.");
            return CommandResult.fail(null);
        }

        // Resolve target UUID: check online players first, then stored player data
        java.util.UUID targetUUID = null;
        String resolvedName = memberName;

        Player onlineTarget = Server.getInstance().getPlayerManager().getPlayerByName(memberName);
        if (onlineTarget != null) {
            targetUUID = onlineTarget.getControlledEntity().getUniqueId();
            resolvedName = onlineTarget.getOriginName();
        } else {
            // Fall back to scanning stored player data for a name match
            for (java.util.UUID candidateUUID : meta.getMembers().keySet()) {
                var data = plugin.getPlayerDataManager().getPlayerData(candidateUUID);
                if (data != null && memberName.equalsIgnoreCase(data.getPlayerName())) {
                    targetUUID = candidateUUID;
                    resolvedName = data.getPlayerName();
                    break;
                }
            }
        }

        if (targetUUID == null) {
            player.sendMessage(TextFormat.RED + "Player '" + memberName + "' is not a member of your island.");
            return CommandResult.fail(null);
        }

        // Verify the target is actually a member of this island
        if (!meta.getMembers().containsKey(targetUUID)) {
            player.sendMessage(TextFormat.RED + "Player '" + memberName + "' is not a member of your island.");
            return CommandResult.fail(null);
        }

        try {
            plugin.getIslandManager().removeMember(ownerUUID, targetUUID);
        } catch (IslandException e) {
            player.sendMessage(TextFormat.RED + "Failed to kick player: " + e.getMessage());
            return CommandResult.fail(null);
        }

        // Notify the owner
        player.sendMessage(TextFormat.GREEN + resolvedName + " has been kicked from your island.");

        // Notify the kicked player if online
        if (onlineTarget != null) {
            onlineTarget.getControlledEntity().sendMessage(
                    TextFormat.YELLOW + "You have been kicked from " + meta.getOwnerName() + "'s island.");
        }

        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is leave}.
     *
     * <ol>
     *   <li>Checks that the plugin is ready.</li>
     *   <li>Checks that the caller is not the island owner (owners must delete or transfer instead).</li>
     *   <li>Calls {@link org.allaymc.skyblock.island.IslandManager#removeMember}.</li>
     *   <li>Notifies the member and the owner.</li>
     * </ol>
     *
     * <p>Requirements: 6.2, 6.4, 11.1</p>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeLeave(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();
        var role = plugin.getPlayerDataManager().getRole(uuid);

        // Requirement 6.4: owners cannot leave — they must delete or transfer
        if (role == IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED
                    + "You are the island owner. Use /is delete to remove your island, or transfer ownership first.");
            return CommandResult.fail(null);
        }

        if (role != IslandRole.MEMBER) {
            player.sendMessage(TextFormat.YELLOW + "You are not a member of any island.");
            return CommandResult.fail(null);
        }

        var ownerUUID = plugin.getPlayerDataManager().getIslandOwner(uuid);
        if (ownerUUID == null) {
            player.sendMessage(TextFormat.RED + "Could not find your island data. Please contact an admin.");
            return CommandResult.fail(null);
        }

        // Fetch owner name before removing membership (metadata will still exist after removal)
        var islandMeta = plugin.getIslandManager().getIslandByOwner(ownerUUID);

        try {
            plugin.getIslandManager().removeMember(ownerUUID, uuid);
        } catch (IslandException e) {
            player.sendMessage(TextFormat.RED + "Failed to leave island: " + e.getMessage());
            return CommandResult.fail(null);
        }

        // Notify the leaving member (include island owner name if available)
        String ownerName = (islandMeta != null) ? islandMeta.getOwnerName() : ownerUUID.toString();
        player.sendMessage(TextFormat.GREEN + "You have left " + ownerName + "'s island.");

        // Notify the owner if online
        Player owner = Server.getInstance().getPlayerManager().getPlayers().get(ownerUUID);
        if (owner != null) {
            String leaverName = player.getController() != null
                    ? player.getController().getOriginName()
                    : player.getUniqueId().toString();
            owner.sendMessage(TextFormat.YELLOW + leaverName + " has left your island.");
        }

        return CommandResult.success(null);
    }

    // -------------------------------------------------------------------------
    // Task 12.4 sub-commands: delete, reset
    // -------------------------------------------------------------------------

    /**
     * Handles {@code /is delete}.
     *
     * <p>Uses a two-step confirmation pattern backed by {@link #pendingConfirmations}:
     * <ol>
     *   <li>First invocation: stores {@code "delete"} in the map and prompts the player to run
     *       the command again to confirm.</li>
     *   <li>Second invocation: calls {@link org.allaymc.skyblock.island.IslandManager#deleteIsland},
     *       teleports the player to the overworld spawn, and sends a success message.</li>
     * </ol>
     *
     * <p>Requirements: 4.1, 4.3, 4.4, 11.1</p>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeDelete(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        // Requirement 4.3: only the owner may delete
        if (plugin.getPlayerDataManager().getRole(uuid) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "Only the island owner can delete the island.");
            return CommandResult.fail(null);
        }

        // Requirement 4.4: two-step confirmation
        String pending = pendingConfirmations.get(uuid);
        if (!"delete".equals(pending)) {
            // First invocation — store and prompt
            pendingConfirmations.put(uuid, "delete");
            player.sendMessage(TextFormat.YELLOW
                    + "WARNING: This will permanently delete your island and all its data!");
            player.sendMessage(TextFormat.YELLOW
                    + "Run /is delete again to confirm, or run any other command to cancel.");
            return CommandResult.success(null);
        }

        // Second invocation — confirmed, execute
        pendingConfirmations.remove(uuid);

        try {
            plugin.getIslandManager().deleteIsland(uuid);
        } catch (IslandException e) {
            player.sendMessage(TextFormat.RED + "Failed to delete island: " + e.getMessage());
            return CommandResult.fail(null);
        }

        // Teleport the player to the overworld spawn
        var spawnPoint = Server.getInstance().getWorldPool().getGlobalSpawnPoint();
        var spawnLoc = new Location3d(
                spawnPoint.x(), spawnPoint.y(), spawnPoint.z(),
                0.0f, 0.0f,
                spawnPoint.dimension()
        );
        player.teleport(spawnLoc);

        player.sendMessage(TextFormat.GREEN + "Your island has been deleted.");
        return CommandResult.success(null);
    }

    /**
     * Handles {@code /is reset}.
     *
     * <p>Uses the same two-step confirmation pattern as {@link #executeDelete}:
     * <ol>
     *   <li>First invocation: stores {@code "reset"} in the map and prompts the player to confirm.</li>
     *   <li>Second invocation: calls {@link org.allaymc.skyblock.island.IslandManager#resetIsland},
     *       teleports the owner to the new spawn point, and sends a success message.</li>
     * </ol>
     *
     * <p>Requirements: 4.2, 4.3, 4.4, 11.1</p>
     *
     * @param player the player who issued the command
     * @return the command result
     */
    private CommandResult executeReset(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        // Requirement 4.3: only the owner may reset
        if (plugin.getPlayerDataManager().getRole(uuid) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "Only the island owner can reset the island.");
            return CommandResult.fail(null);
        }

        // Requirement 4.4: two-step confirmation
        String pending = pendingConfirmations.get(uuid);
        if (!"reset".equals(pending)) {
            // First invocation — store and prompt
            pendingConfirmations.put(uuid, "reset");
            player.sendMessage(TextFormat.YELLOW
                    + "WARNING: This will reset your island to its initial state and clear all blocks!");
            player.sendMessage(TextFormat.YELLOW
                    + "Run /is reset again to confirm, or run any other command to cancel.");
            return CommandResult.success(null);
        }

        // Second invocation — confirmed, execute
        pendingConfirmations.remove(uuid);

        try {
            plugin.getIslandManager().resetIsland(uuid);
        } catch (IslandException e) {
            player.sendMessage(TextFormat.RED + "Failed to reset island: " + e.getMessage());
            return CommandResult.fail(null);
        }

        // Teleport the owner to the new spawn point stored in the updated metadata
        var meta = plugin.getIslandManager().getIslandByOwner(uuid);
        if (meta != null) {
            var world = Server.getInstance().getWorldPool().getWorld(meta.getWorldName());
            if (world != null) {
                var dimension = world.getOverWorld();
                var spawnLoc = new Location3d(
                        meta.getSpawnX(), meta.getSpawnY(), meta.getSpawnZ(),
                        meta.getSpawnPitch(), meta.getSpawnYaw(),
                        dimension
                );
                player.teleport(spawnLoc);
            }
        }

        player.sendMessage(TextFormat.GREEN + "Your island has been reset! Welcome back to Skyblock!");
        return CommandResult.success(null);
    }

    // -------------------------------------------------------------------------
    // Package-private validation helpers (used by property-based tests)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given player name argument is missing or malformed
     * (i.e., {@code null} or empty) for the {@code /is invite} sub-command.
     *
     * <p>This method is public to allow property-based tests to verify the
     * argument-validation logic without needing a live AllayMC server instance.</p>
     *
     * @param targetName the player name argument supplied to {@code /is invite}
     * @return {@code true} if the argument is missing or malformed
     */
    public static boolean isInviteArgMissing(String targetName) {
        return targetName == null || targetName.isEmpty();
    }

    /**
     * Returns {@code true} if the given member name argument is missing or malformed
     * (i.e., {@code null} or empty) for the {@code /is kick} sub-command.
     *
     * <p>This method is public to allow property-based tests to verify the
     * argument-validation logic without needing a live AllayMC server instance.</p>
     *
     * @param memberName the member name argument supplied to {@code /is kick}
     * @return {@code true} if the argument is missing or malformed
     */
    public static boolean isKickArgMissing(String memberName) {
        return memberName == null || memberName.isEmpty();
    }

    /**
     * Returns the usage-hint message for the {@code /is invite} sub-command.
     *
     * @return the usage-hint string
     */
    public static String inviteUsageHint() {
        return TextFormat.RED + "Usage: /is invite <player>";
    }

    /**
     * Returns the usage-hint message for the {@code /is kick} sub-command.
     *
     * @return the usage-hint string
     */
    public static String kickUsageHint() {
        return TextFormat.RED + "Usage: /is kick <member>";
    }

    /**
     * Returns the error message sent when a player tries to use {@code /is sethome}
     * while not inside their own island world.
     *
     * @return the error message string
     */
    public static String sethomeNotInOwnIslandError() {
        return TextFormat.RED + "You must be standing inside your own island to set your home point.";
    }

    // -------------------------------------------------------------------------
    // Rename sub-command
    // -------------------------------------------------------------------------

    /**
     * Opens a CustomForm text-input dialog so the player can type a new island name.
     * Only the island owner can rename.
     */
    private void openRenameForm(EntityPlayer player) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return;
        }

        var controller = player.getController();
        if (controller == null) return;

        if (plugin.getPlayerDataManager().getRole(player.getUniqueId()) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "Only the island owner can rename the island.");
            return;
        }

        // Pre-fill with the current island name
        var ownerUUID = player.getUniqueId();
        var meta = plugin.getIslandManager().getIslandByOwner(ownerUUID);
        String current = (meta != null) ? meta.getDisplayName() : "";

        Forms.custom()
                .title("§6§lRename Your Island")
                .label("§7Enter a new display name for your island.\n§7This name appears in §e/is top§7.")
                .input("§fIsland Name", "e.g. My Awesome Island", current)
                .onResponse(responses -> {
                    if (responses == null || responses.isEmpty()) return;
                    String newName = responses.get(0);
                    executeRename(player, newName);
                })
                .sendTo(controller);
    }

    /**
     * Handles {@code /is rename <name>} — sets a custom display name for the island.
     * Only the island owner may rename. Name is trimmed and capped at 32 characters.
     */
    private CommandResult executeRename(EntityPlayer player, String name) {
        if (!plugin.isReady()) {
            player.sendMessage(TextFormat.RED + "Skyblock is still loading, please wait.");
            return CommandResult.fail(null);
        }

        var uuid = player.getUniqueId();

        if (plugin.getPlayerDataManager().getRole(uuid) != IslandRole.OWNER) {
            player.sendMessage(TextFormat.RED + "Only the island owner can rename the island.");
            return CommandResult.fail(null);
        }

        if (name == null || name.isBlank()) {
            player.sendMessage(TextFormat.RED + "Usage: /is rename <name>");
            return CommandResult.fail(null);
        }

        // Cap at 32 characters
        String trimmed = name.strip();
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }

        try {
            plugin.getIslandManager().setIslandName(uuid, trimmed);
        } catch (IslandException e) {
            player.sendMessage(TextFormat.RED + "Failed to rename island: " + e.getMessage());
            return CommandResult.fail(null);
        }

        player.sendMessage("§6Your island has been renamed to: §f" + trimmed);
        return CommandResult.success(null);
    }

}
