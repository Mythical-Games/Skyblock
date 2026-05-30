package org.allaymc.skyblock.island;

import org.allaymc.api.blockentity.interfaces.BlockEntityChest;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.world.Dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.allaymc.api.block.type.BlockTypes.*;

/**
 * Generates the Vibe-Skyblock starter island — a scenic, irregular floating island
 * inspired by classic Skyblock aesthetics:
 * <ul>
 *   <li>Irregular multi-level grass/dirt terrain with a stone/cobblestone underbelly</li>
 *   <li>Tall oak tree with a wide, layered canopy</li>
 *   <li>Hay bale stack on the west side</li>
 *   <li>Sugarcane (reeds) patch near the east edge</li>
 *   <li>Gravel patch</li>
 *   <li>Scattered flowers, short grass, and ferns</li>
 *   <li>Two ground lanterns</li>
 *   <li>Vines hanging off the south and east edges</li>
 *   <li>Starter chest</li>
 * </ul>
 */
public class StarterPlatformGenerator {

    public static final int ORIGIN_X = 0;
    public static final int ORIGIN_Y = 64;
    public static final int ORIGIN_Z = 0;

    // Convenience aliases
    private static final int OX = ORIGIN_X;
    private static final int OY = ORIGIN_Y;
    private static final int OZ = ORIGIN_Z;

    public Location3d generate(Dimension dimension, List<ItemStack> starterItems) {
        ensureChunksLoaded(dimension);

        placeUnderbelly(dimension);
        placeMainTerrain(dimension);
        placeTree(dimension);
        placeHayBales(dimension);
        placeSugarcane(dimension);
        placeGravelPatch(dimension);
        placeDecorations(dimension);
        placeVines(dimension);
        placeChest(dimension, starterItems);

        // Spawn at the centre of the main surface
        return new Location3d(OX + 0.5, OY + 3, OZ + 0.5, 0.0, 0.0, dimension);
    }

    // -------------------------------------------------------------------------
    // Chunk pre-loading
    // -------------------------------------------------------------------------

    private void ensureChunksLoaded(Dimension dimension) {
        int cx = OX >> 4;
        int cz = OZ >> 4;
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                futures.add(dimension.getChunkManager().getOrLoadChunk(cx + dx, cz + dz));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    // -------------------------------------------------------------------------
    // Terrain
    // -------------------------------------------------------------------------

    /**
     * Stone/cobblestone underbelly that tapers downward, giving the island
     * its classic floating-rock look.
     */
    private void placeUnderbelly(Dimension dimension) {
        // Layer 0 (bottom tip): 3×3 stone
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(dimension, OX + dx, OY - 3, OZ + dz, STONE);

        // Layer 1: 5×5 cobblestone
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                set(dimension, OX + dx, OY - 2, OZ + dz, COBBLESTONE);

        // Layer 2: 7×7 cobblestone (rounded corners)
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                set(dimension, OX + dx, OY - 1, OZ + dz, COBBLESTONE);
            }
    }

    /**
     * Multi-level dirt/grass surface. The island is not flat — it has a raised
     * central plateau and lower ledges on the edges, matching the reference image.
     */
    private void placeMainTerrain(Dimension dimension) {
        // ── Base dirt layer (Y = OY) — full 9×9 footprint, rounded corners ──
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 && Math.abs(dz) == 4) continue; // round corners
                set(dimension, OX + dx, OY, OZ + dz, DIRT);
            }

        // ── Grass surface (Y = OY+1) — same footprint ──
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) == 4 && Math.abs(dz) == 4) continue;
                set(dimension, OX + dx, OY + 1, OZ + dz, GRASS_BLOCK);
            }

        // ── Raised central plateau (Y = OY+2) — 5×5 dirt + grass ──
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                set(dimension, OX + dx, OY + 2, OZ + dz, DIRT);
                set(dimension, OX + dx, OY + 3, OZ + dz, GRASS_BLOCK);
            }

        // ── Small raised bump on the west side (hay bale platform) ──
        // Extra dirt block so hay bales sit higher
        set(dimension, OX - 3, OY + 2, OZ - 1, DIRT);
        set(dimension, OX - 3, OY + 2, OZ,     DIRT);
        set(dimension, OX - 3, OY + 2, OZ + 1, DIRT);
        set(dimension, OX - 4, OY + 2, OZ,     DIRT);

        // ── Podzol patch near the tree base for a natural look ──
        set(dimension, OX + 1, OY + 3, OZ - 1, PODZOL);
        set(dimension, OX + 1, OY + 3, OZ,     PODZOL);
        set(dimension, OX + 2, OY + 3, OZ - 1, PODZOL);
    }

    // -------------------------------------------------------------------------
    // Tree
    // -------------------------------------------------------------------------

    /**
     * Tall oak tree at (-1, 0) relative to origin — trunk 7 logs tall,
     * wide layered canopy matching the reference image.
     */
    private void placeTree(Dimension dimension) {
        int tx = OX - 1;
        int tz = OZ;
        int base = OY + 4; // sits on the raised plateau grass

        // Trunk: 7 logs
        for (int dy = 0; dy <= 6; dy++)
            set(dimension, tx, base + dy, tz, OAK_LOG);

        int top = base + 6;

        // Canopy layer top-3 (wide base): 7×7 minus far corners
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                setLeaf(dimension, tx + dx, top - 3, tz + dz);
            }

        // Canopy layer top-2: 7×7 minus far corners
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                setLeaf(dimension, tx + dx, top - 2, tz + dz);
            }

        // Canopy layer top-1: 5×5
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                setLeaf(dimension, tx + dx, top - 1, tz + dz);

        // Canopy top cap: 3×3
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                setLeaf(dimension, tx + dx, top,     tz + dz);
                setLeaf(dimension, tx + dx, top + 1, tz + dz);
            }
    }

    private void setLeaf(Dimension dimension, int x, int y, int z) {
        var cur = dimension.getBlockState(x, y, z, 0);
        if (cur == null || cur.getBlockType() == AIR)
            set(dimension, x, y, z, OAK_LEAVES);
    }

    // -------------------------------------------------------------------------
    // Decorations
    // -------------------------------------------------------------------------

    /** Hay bale stack on the west side (3 tall, 2 wide). */
    private void placeHayBales(Dimension dimension) {
        int baseY = OY + 3; // on top of the raised west dirt
        // 2×1 base
        set(dimension, OX - 3, baseY,     OZ,     HAY_BLOCK);
        set(dimension, OX - 4, baseY,     OZ,     HAY_BLOCK);
        // Second row
        set(dimension, OX - 3, baseY + 1, OZ,     HAY_BLOCK);
        set(dimension, OX - 4, baseY + 1, OZ,     HAY_BLOCK);
        // Top single
        set(dimension, OX - 3, baseY + 2, OZ,     HAY_BLOCK);
    }

    /** Sugarcane (REEDS) patch on the east edge — needs to be on dirt/grass next to water.
     *  We place it on the grass surface; AllayMC will handle the growth rules. */
    private void placeSugarcane(Dimension dimension) {
        int grassY = OY + 2; // top of the lower grass ring
        int reedY  = OY + 3;
        // 3 sugarcane columns, 2 blocks tall
        for (int dz = -1; dz <= 1; dz++) {
            set(dimension, OX + 4, reedY,     OZ + dz, REEDS);
            set(dimension, OX + 4, reedY + 1, OZ + dz, REEDS);
        }
    }

    /** Small gravel patch near the centre. */
    private void placeGravelPatch(Dimension dimension) {
        set(dimension, OX + 2, OY + 4, OZ + 2, GRAVEL);
        set(dimension, OX + 3, OY + 2, OZ + 2, GRAVEL);
        set(dimension, OX + 3, OY + 2, OZ + 3, GRAVEL);
    }

    /** Flowers, short grass, ferns, and lanterns. */
    private void placeDecorations(Dimension dimension) {
        int surf = OY + 4; // surface of the raised plateau

        // Flowers on the plateau
        set(dimension, OX - 2, surf, OZ + 2, DANDELION);
        set(dimension, OX + 0, surf, OZ + 2, POPPY);
        set(dimension, OX + 1, surf, OZ - 2, AZURE_BLUET);
        set(dimension, OX + 2, surf, OZ - 2, OXEYE_DAISY);
        set(dimension, OX + 2, surf, OZ - 1, ALLIUM);

        // Short grass tufts
        set(dimension, OX + 1, surf, OZ + 1, SHORT_GRASS);
        set(dimension, OX - 1, surf, OZ + 1, SHORT_GRASS);
        set(dimension, OX + 2, surf, OZ + 0, SHORT_GRASS);

        // Fern near the tree
        set(dimension, OX + 0, surf, OZ - 1, FERN);

        // Tall grass on the lower ring
        set(dimension, OX + 3, OY + 2, OZ - 3, TALL_GRASS);
        set(dimension, OX - 3, OY + 2, OZ + 3, TALL_GRASS);

        // Ground lanterns
        set(dimension, OX + 2, surf, OZ + 1, LANTERN);
        set(dimension, OX - 2, OY + 2, OZ + 3, LANTERN);

        // Torch on the hay bale stack
        set(dimension, OX - 3, OY + 6, OZ, TORCH);
    }

    /** Vines hanging off the south and east edges. */
    private void placeVines(Dimension dimension) {
        // South edge vines (hanging down 3 blocks)
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = 0; dy <= 2; dy++)
                set(dimension, OX + dx, OY - dy, OZ + 4, VINE);

        // East edge vines
        for (int dz = -2; dz <= 2; dz++)
            for (int dy = 0; dy <= 2; dy++)
                set(dimension, OX + 4, OY - dy, OZ + dz, VINE);
    }

    /** Starter chest on the plateau surface — placed at an isolated position. */
    private void placeChest(Dimension dimension, List<ItemStack> items) {
        // Place at (-2, OY+4, -2) — a corner of the plateau, away from other blocks
        // that could trigger a double-chest merge.
        int cx = OX - 2, cy = OY + 4, cz = OZ - 2;
        set(dimension, cx, cy, cz, CHEST);

        if (items == null || items.isEmpty()) return;
        var be = dimension.getBlockEntity(cx, cy, cz);
        if (be instanceof BlockEntityChest chest) {
            var container = chest.getContainer();
            for (ItemStack item : items)
                if (item != null) container.tryAddItem(item);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void set(Dimension dimension, int x, int y, int z,
                     org.allaymc.api.block.type.BlockType<?> type) {
        dimension.setBlockState(x, y, z, type.getDefaultState());
    }
}
