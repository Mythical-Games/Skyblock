package org.allaymc.skyblock.listener;

import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.server.PlayerJoinEvent;
import org.allaymc.api.eventbus.event.server.PlayerQuitEvent;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.math.location.Location3ic;
import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;
import org.allaymc.api.utils.TextFormat;
import org.allaymc.api.world.particle.DustParticle;
import org.allaymc.api.world.sound.SimpleSound;
import org.allaymc.skyblock.SkyblockPlugin;
import org.allaymc.skyblock.island.IslandManager;
import org.joml.Vector3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles player join events for Vibe-Skyblock:
 * <ol>
 *   <li>If the player has an island, ensure the island world is loaded so that
 *       {@code /is home} and block-permission checks work immediately.</li>
 *   <li>If the player's saved position is inside a Skyblock island world (e.g.
 *       they disconnected while on their island), teleport them to the default
 *       world spawn instead — the island world may not be loaded yet at this point.</li>
 *   <li>Play a heart particle effect and welcome title after a short delay.</li>
 * </ol>
 */
public class PlayerJoinListener {

    // ── Timing ────────────────────────────────────────────────────────────────
    private static final int SPAWN_DELAY_TICKS  = 20;
    private static final int TITLE_DELAY_TICKS  = 60;

    // ── Streaming rate ────────────────────────────────────────────────────────
    private static final int PACKETS_PER_FIRE    = 30;
    private static final int STREAM_PERIOD_TICKS = 2;

    // ── Heart geometry ────────────────────────────────────────────────────────
    private static final double HEART_DISTANCE = 4.0;
    private static final double HEART_SCALE    = 2.5;
    private static final double OUTLINE_STEP   = 0.12;
    private static final double FILL_STEP      = 0.15;

    // ── Particles ─────────────────────────────────────────────────────────────
    private static final DustParticle OUTLINE_PARTICLE = new DustParticle(new Color(220, 0,   50));
    private static final DustParticle FILL_PARTICLE    = new DustParticle(new Color(255, 105, 150));

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
                Thread.ofVirtual().name("island-world-loader-" + ownerUUID).start(() ->
                        plugin.getIslandManager().ensureIslandWorldLoaded(ownerUUID)
                );
            }
        }

        // ── Step 2: redirect to default spawn if saved in an island world ──
        var dimension = entity.getDimension();
        if (dimension != null) {
            var world = dimension.getWorld();
            if (world != null && world.getName().startsWith(IslandManager.WORLD_NAME_PREFIX)) {
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

        // ── Step 3: heart effect + welcome title after spawn delay ──
        Server.getInstance().getScheduler().scheduleDelayed(
                plugin, () -> startJoinSequence(player), SPAWN_DELAY_TICKS);
    }

    // ── Join sequence ─────────────────────────────────────────────────────────

    private void startJoinSequence(Player player) {
        if (!player.isInitialized() || player.isDisconnected()) return;

        var entity = player.getControlledEntity();
        if (entity == null) return;

        var loc = entity.getLocation();

        // Pre-compute all heart points (CPU only, no packets yet)
        List<ParticlePoint> points = buildHeartPoints(loc.x(), loc.y(), loc.z(), loc.yaw());

        // Sounds
        player.viewSound(SimpleSound.FIREWORK_BLAST,
                new Vector3d(loc.x(), loc.y(), loc.z()), false);
        player.viewSound(SimpleSound.FIREWORK_HUGE_BLAST,
                new Vector3d(loc.x(), loc.y(), loc.z()), false);

        // Stream particles at PACKETS_PER_FIRE every STREAM_PERIOD_TICKS ticks
        AtomicInteger cursor = new AtomicInteger(0);
        Server.getInstance().getScheduler().scheduleRepeating(plugin, () -> {
            if (player.isDisconnected()) {
                cursor.set(points.size());
                return;
            }
            int start = cursor.get();
            int end   = Math.min(start + PACKETS_PER_FIRE, points.size());
            for (int i = start; i < end; i++) {
                ParticlePoint pp = points.get(i);
                player.viewParticle(pp.particle(), pp.pos());
            }
            cursor.set(end);
        }, STREAM_PERIOD_TICKS);

        // Welcome title fires after the heart has had time to fully draw
        Server.getInstance().getScheduler().scheduleDelayed(
                plugin, () -> showWelcomeTitle(player), TITLE_DELAY_TICKS);
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    private void showWelcomeTitle(Player player) {
        if (!player.isInitialized() || player.isDisconnected()) return;

        var entity = player.getControlledEntity();
        if (entity == null) return;

        player.setTitleSettings(10, 120, 20);
        player.sendTitle(TextFormat.LIGHT_PURPLE + "❤ Welcome! ❤");
        player.sendSubtitle(
                TextFormat.YELLOW + "Glad to have you, " +
                TextFormat.AQUA   + entity.getDisplayName() +
                TextFormat.YELLOW + "!"
        );
    }

    // ── Heart point generation ────────────────────────────────────────────────

    private List<ParticlePoint> buildHeartPoints(
            double px, double py, double pz, double yawDeg
    ) {
        double yawRad = Math.toRadians(yawDeg);
        double fwdX   = -Math.sin(yawRad);
        double fwdZ   =  Math.cos(yawRad);
        double rightX =  fwdZ;
        double rightZ = -fwdX;

        double cx = px + fwdX * HEART_DISTANCE;
        double cy = py + 1.6 + 2.0;
        double cz = pz + fwdZ * HEART_DISTANCE;

        List<double[]>      outline = buildOutlinePoints();
        List<ParticlePoint> result  = new ArrayList<>(outline.size() * 4);

        // Outline
        for (double[] p : outline) {
            result.add(toPoint(OUTLINE_PARTICLE, cx, cy, cz, p[0], p[1], rightX, rightZ));
        }

        // Fill (horizontal scan-lines)
        double minHy = outline.stream().mapToDouble(p -> p[1]).min().orElse(-1.0);
        double maxHy = outline.stream().mapToDouble(p -> p[1]).max().orElse(1.0);

        for (double hy = minHy; hy <= maxHy; hy += FILL_STEP) {
            double left  =  Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;

            for (double[] p : outline) {
                if (Math.abs(p[1] - hy) <= FILL_STEP * 0.75) {
                    if (p[0] < left)  left  = p[0];
                    if (p[0] > right) right = p[0];
                }
            }

            if (left == Double.MAX_VALUE) continue;

            for (double hx = left + FILL_STEP; hx < right; hx += FILL_STEP) {
                result.add(toPoint(FILL_PARTICLE, cx, cy, cz, hx, hy, rightX, rightZ));
            }
        }

        return result;
    }

    private List<double[]> buildOutlinePoints() {
        List<double[]> points     = new ArrayList<>();
        final double   norm       = 16.0;
        final int      oversample = 4000;

        double prevX = 0, prevY = 0;
        double accumulated = 0.0;

        for (int i = 0; i <= oversample; i++) {
            double t = (2.0 * Math.PI * i) / oversample;

            double rawX =  16.0 * Math.pow(Math.sin(t), 3);
            double rawY =  13.0 * Math.cos(t)
                         -  5.0 * Math.cos(2 * t)
                         -  2.0 * Math.cos(3 * t)
                         -        Math.cos(4 * t);

            double hx = (rawX / norm) * HEART_SCALE;
            double hy = (rawY / norm) * HEART_SCALE;

            if (i == 0) {
                prevX = hx; prevY = hy;
                points.add(new double[]{hx, hy});
                continue;
            }

            double dx = hx - prevX;
            double dy = hy - prevY;
            accumulated += Math.sqrt(dx * dx + dy * dy);

            if (accumulated >= OUTLINE_STEP) {
                points.add(new double[]{hx, hy});
                accumulated = 0.0;
            }

            prevX = hx; prevY = hy;
        }

        return points;
    }

    private ParticlePoint toPoint(
            DustParticle particle,
            double cx, double cy, double cz,
            double hx, double hy,
            double rightX, double rightZ
    ) {
        return new ParticlePoint(particle, new Vector3d(
                cx + rightX * hx,
                cy + hy,
                cz + rightZ * hx
        ));
    }

    private record ParticlePoint(DustParticle particle, Vector3d pos) {}

    // ── Quit handler ──────────────────────────────────────────────────────────

    /**
     * Fires when a fully-spawned player quits the server.
     * Unloads the island world if no other players remain inside it.
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
