package org.allaymc.skyblock.invite;

import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;
import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.model.PendingInvite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending island invitations with 60-second expiry.
 *
 * <p>Invitations are stored in a {@link ConcurrentHashMap} keyed by the invited player's UUID.
 * Each player can only have one pending invite at a time (the latest one replaces any prior).</p>
 *
 * <p>Requirements: 5.1, 5.2, 5.3, 5.4, 5.5</p>
 */
public class InviteManager {

    /** In-memory store: invitedPlayerUUID → PendingInvite. */
    private final ConcurrentHashMap<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    private final SkyblockPlugin plugin;

    /**
     * Constructs an {@code InviteManager} backed by the given plugin instance.
     *
     * @param plugin the owning {@link SkyblockPlugin}
     */
    public InviteManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Invite lifecycle
    // -------------------------------------------------------------------------

    /**
     * Sends an island invitation from the owner to the target player.
     *
     * <ol>
     *   <li>Checks that the target is not already in an island; sends an error to the owner if so.</li>
     *   <li>Checks that the target is currently online; sends an error to the owner if offline.</li>
     *   <li>Creates a {@link PendingInvite} (expires in 60 s), stores it, and notifies the target.</li>
     * </ol>
     *
     * @param ownerUUID  the UUID of the island owner sending the invite
     * @param targetUUID the UUID of the player being invited
     * Requirements: 5.1, 5.3, 5.4
     */
    public void sendInvite(UUID ownerUUID, UUID targetUUID) {
        // Requirement 5.3: target must not already be in an island
        if (plugin.getPlayerDataManager().hasIsland(targetUUID)) {
            Player owner = getOnlinePlayer(ownerUUID);
            if (owner != null) {
                owner.sendMessage("§cThat player is already a member of an island.");
            }
            return;
        }

        // Requirement 5.4: target must be online
        Player target = getOnlinePlayer(targetUUID);
        if (target == null) {
            Player owner = getOnlinePlayer(ownerUUID);
            if (owner != null) {
                owner.sendMessage("§cThat player is not online.");
            }
            return;
        }

        // Create and store the invite
        PendingInvite invite = new PendingInvite(ownerUUID, targetUUID);
        pendingInvites.put(targetUUID, invite);

        // Notify the target player
        String ownerName = getPlayerName(ownerUUID);
        target.sendMessage("§aYou have been invited to join §e" + ownerName + "§a's island! "
                + "Type §f/is accept §ato accept or §f/is decline §ato decline. "
                + "This invite expires in 60 seconds.");
    }

    /**
     * Accepts the pending invitation for the given target player.
     *
     * <ol>
     *   <li>Retrieves the pending invite; returns {@code false} if none exists.</li>
     *   <li>Calls {@link org.allaymc.skyblock.island.IslandManager#addMember} to add the player.</li>
     *   <li>Removes the invite from the map.</li>
     *   <li>Notifies both the owner and the new member.</li>
     * </ol>
     *
     * @param targetUUID the UUID of the player accepting the invite
     * @return {@code true} if the invite was found and accepted successfully, {@code false} otherwise
     * Requirements: 5.2
     */
    public boolean acceptInvite(UUID targetUUID) {
        PendingInvite invite = pendingInvites.get(targetUUID);
        if (invite == null) {
            return false;
        }

        UUID ownerUUID = invite.getOwnerUUID();

        try {
            plugin.getIslandManager().addMember(ownerUUID, targetUUID);
        } catch (Exception e) {
            // If addMember fails, remove the invite and notify the target
            pendingInvites.remove(targetUUID);
            Player target = getOnlinePlayer(targetUUID);
            if (target != null) {
                target.sendMessage("§cFailed to join the island: " + e.getMessage());
            }
            return false;
        }

        // Remove the invite
        pendingInvites.remove(targetUUID);

        // Notify both parties
        String ownerName = getPlayerName(ownerUUID);
        String targetName = getPlayerName(targetUUID);

        Player owner = getOnlinePlayer(ownerUUID);
        if (owner != null) {
            owner.sendMessage("§a" + targetName + " has joined your island!");
        }

        Player target = getOnlinePlayer(targetUUID);
        if (target != null) {
            target.sendMessage("§aYou have joined §e" + ownerName + "§a's island!");
        }

        return true;
    }

    /**
     * Declines the pending invitation for the given target player.
     *
     * <p>Removes the invite from the map and notifies the owner.</p>
     *
     * @param targetUUID the UUID of the player declining the invite
     */
    public void declineInvite(UUID targetUUID) {
        PendingInvite invite = pendingInvites.remove(targetUUID);
        if (invite == null) {
            return;
        }

        String targetName = getPlayerName(targetUUID);
        Player owner = getOnlinePlayer(invite.getOwnerUUID());
        if (owner != null) {
            owner.sendMessage("§e" + targetName + " §cdeclined your island invitation.");
        }
    }

    /**
     * Returns {@code true} if the given player has a pending invite.
     *
     * @param targetUUID the UUID of the player to check
     * @return {@code true} if a pending invite exists for this player
     */
    public boolean hasPendingInvite(UUID targetUUID) {
        return pendingInvites.containsKey(targetUUID);
    }

    /**
     * Iterates all pending invites and removes any that have expired
     * ({@code System.currentTimeMillis() >= expiresAt}), notifying the owner of each.
     *
     * <p>This method is intended to be called by the server scheduler every tick (or every second).</p>
     *
     * Requirements: 5.5
     */
    public void tickExpiry() {
        long now = System.currentTimeMillis();

        // Collect expired entries to avoid ConcurrentModificationException
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, PendingInvite> entry : pendingInvites.entrySet()) {
            if (now >= entry.getValue().getExpiresAt()) {
                expired.add(entry.getKey());
            }
        }

        for (UUID targetUUID : expired) {
            PendingInvite invite = pendingInvites.remove(targetUUID);
            if (invite == null) {
                // Already removed by a concurrent accept/decline
                continue;
            }

            String targetName = getPlayerName(targetUUID);
            Player owner = getOnlinePlayer(invite.getOwnerUUID());
            if (owner != null) {
                owner.sendMessage("§eYour island invitation to §f" + targetName + " §ehas expired.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the online {@link Player} for the given UUID, or {@code null} if offline.
     *
     * @param uuid the player's UUID
     * @return the online player, or {@code null}
     */
    private Player getOnlinePlayer(UUID uuid) {
        return Server.getInstance().getPlayerManager().getPlayers().get(uuid);
    }

    /**
     * Returns a display name for the given UUID.
     * Uses the player's online name if available, otherwise falls back to stored player data,
     * and finally to the UUID string.
     *
     * @param uuid the player's UUID
     * @return a human-readable name
     */
    private String getPlayerName(UUID uuid) {
        Player online = getOnlinePlayer(uuid);
        if (online != null) {
            return online.getOriginName();
        }
        var data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data != null && data.getPlayerName() != null) {
            return data.getPlayerName();
        }
        return uuid.toString();
    }
}
