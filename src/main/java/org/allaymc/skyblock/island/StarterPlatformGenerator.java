package org.allaymc.skyblock.island;

import org.allaymc.api.block.property.type.BlockPropertyTypes;
import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.blockentity.interfaces.BlockEntityChest;
import org.allaymc.api.item.ItemStack;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.world.Dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.allaymc.api.block.type.BlockTypes.*;

/**
 * Generates the Vibe-Skyblock starter island.
 *
 * All Y values are relative to ORIGIN_Y (64).
 *
 * Surface (Y and Y+1):
 *   13×13 footprint (radius 6), extreme corners trimmed.
 *   Dirt at Y, Grass at Y+1.
 *
 * Cone underbelly — each layer shrinks by 2 on each side:
 *   Y-1  Cobblestone  11×11, corners trimmed
 *   Y-2  Cobblestone   9×9, corners trimmed
 *   Y-3  Cobblestone   7×7
 *   Y-4  Cobblestone   5×5
 *   Y-5  Cobblestone   3×3
 *   Y-6  Stone          1×1 (tip)
 *
 * Lake (centred at 0,0):
 *   5×5 with 4 corners clipped → 21 water blocks
 *   Sand at Y, water source at Y+1 (flush with grass)
 *   Lily pad at Y+2 centre
 *
 * Sugarcane ring (immediately outside the lake):
 *   Only placed where the cell is fully inside the island AND
 *   has at least one lake-water neighbour at Y+1.
 *   Sand at Y, sand at Y+1, sugarcane at Y+2 and Y+3.
 *
 * Tree at (-4, -4), trunk base at Y+2.
 * Chest, flowers, lanterns, hay bales on the surface.
 */
public class StarterPlatformGenerator {

    public static final int ORIGIN_X = 0;
    public static final int ORIGIN_Y = 64;
    public static final int ORIGIN_Z = 0;

    private static final int OX = ORIGIN_X;
    private static final int OY = ORIGIN_Y;
    private static final int OZ = ORIGIN_Z;

    // Half-width of the surface layer
    private static final int RADIUS = 6;

    public Location3d generate(Dimension dimension, List<ItemStack> starterItems) {
        ensureChunksLoaded(dimension);

        // Guard: skip if already generated
        var existing = dimension.getBlockState(OX, OY + 1, OZ, 0);
        if (existing != null && existing.getBlockType() != AIR) {
            return spawnPoint(dimension);
        }

        placeUnderbelly(dimension);
        placeIslandBase(dimension);
        placeLake(dimension);
        placeSugarcane(dimension);
        placeTree(dimension);
        placeDecorations(dimension);
        placeChest(dimension, starterItems);

        return spawnPoint(dimension);
    }

    private Location3d spawnPoint(Dimension dimension) {
        // (4, 4): 4²+4²=32, well inside the circle
        return new Location3d(OX + 4.5, OY + 2, OZ + 4.5, 0.0, 0.0, dimension);
    }

    // -------------------------------------------------------------------------
    // Chunk loading
    // -------------------------------------------------------------------------

    private void ensureChunksLoaded(Dimension dimension) {
        int cx = OX >> 4;
        int cz = OZ >> 4;
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                futures.add(dimension.getChunkManager().getOrLoadChunk(cx + dx, cz + dz));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    // -------------------------------------------------------------------------
    // Shape helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if (dx, dz) is inside a rounded layer of the given half-width.
     * Uses a circular distance check so the shape is an oval rather than a square.
     * The threshold is tuned so the layer fills roughly the same area as before
     * but with smooth, rounded edges instead of hard corners.
     */
    private boolean inLayer(int dx, int dz, int halfWidth) {
        if (halfWidth == 0) return dx == 0 && dz == 0;
        // Normalise to the unit circle and check distance
        double nx = (double) dx / halfWidth;
        double nz = (double) dz / halfWidth;
        return (nx * nx + nz * nz) <= 1.1;
    }

    /** Returns true if (dx, dz) is a lake water cell. */
    private boolean isLakeWater(int dx, int dz) {
        // 5×5 centred at 0,0 with 4 corners clipped
        if (Math.abs(dx) > 2 || Math.abs(dz) > 2) return false;
        return !(Math.abs(dx) == 2 && Math.abs(dz) == 2);
    }

    /** Returns true if (dx, dz) has at least one lake-water neighbour (4-directional). */
    private boolean adjacentToLake(int dx, int dz) {
        return isLakeWater(dx - 1, dz)
            || isLakeWater(dx + 1, dz)
            || isLakeWater(dx, dz - 1)
            || isLakeWater(dx, dz + 1);
    }

    // -------------------------------------------------------------------------
    // Cone underbelly
    // -------------------------------------------------------------------------

    /**
     * Cone shape: each layer down is 2 blocks narrower on each side.
     *
     *   Y-1  half=5  (11×11, corners trimmed)
     *   Y-2  half=4  ( 9×9, corners trimmed)
     *   Y-3  half=3  ( 7×7, corners trimmed)
     *   Y-4  half=2  ( 5×5, corners trimmed)
     *   Y-5  half=1  ( 3×3, corners trimmed → cross shape)
     *   Y-6  half=0  ( 1×1 stone tip)
     */
    private void placeUnderbelly(Dimension dimension) {
        for (int depth = 1; depth <= 5; depth++) {
            int halfWidth = RADIUS - depth;
            int y = OY - depth;
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                    if (!inLayer(dx, dz, halfWidth)) continue;
                    set(dimension, OX + dx, y, OZ + dz, COBBLESTONE);
                }
            }
        }
        // Stone tip
        set(dimension, OX, OY - 6, OZ, STONE);
    }

    // -------------------------------------------------------------------------
    // Surface
    // -------------------------------------------------------------------------

    private void placeIslandBase(Dimension dimension) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (!inLayer(dx, dz, RADIUS)) continue;
                set(dimension, OX + dx, OY,     OZ + dz, DIRT);
                set(dimension, OX + dx, OY + 1, OZ + dz, GRASS_BLOCK);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lake
    // -------------------------------------------------------------------------

    private void placeLake(Dimension dimension) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!isLakeWater(dx, dz)) continue;
                set(dimension, OX + dx, OY,     OZ + dz, SAND);
                dimension.setBlockState(OX + dx, OY + 1, OZ + dz, waterSource());
            }
        }
        // Lily pad in the centre
        set(dimension, OX, OY + 2, OZ, WATERLILY);
    }

    private BlockState waterSource() {
        return WATER.getDefaultState()
                .setPropertyValue(BlockPropertyTypes.LIQUID_DEPTH, 0);
    }

    // -------------------------------------------------------------------------
    // Sugarcane
    // -------------------------------------------------------------------------

    /**
     * Sugarcane ring around the lake.
     *
     * A cell qualifies if ALL of:
     *   1. It is in the outer ring of the 7×7 area (not inside the lake)
     *   2. It is not a corner of that ring (abs(dx)==3 && abs(dz)==3)
     *   3. It is inside the island footprint
     *   4. It has at least one lake-water neighbour at Y+1
     *
     * Sand replaces the grass/dirt at Y and Y+1; sugarcane at Y+2 and Y+3.
     */
    private void placeSugarcane(Dimension dimension) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                // Must be in the outer ring (not the lake interior)
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) continue;
                // Skip ring corners — not adjacent to water
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                // Must be inside the island
                if (!inLayer(dx, dz, RADIUS)) continue;
                // Must have a lake-water neighbour
                if (!adjacentToLake(dx, dz)) continue;

                set(dimension, OX + dx, OY,     OZ + dz, SAND);
                set(dimension, OX + dx, OY + 1, OZ + dz, SAND);
                set(dimension, OX + dx, OY + 2, OZ + dz, REEDS);
                set(dimension, OX + dx, OY + 3, OZ + dz, REEDS);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tree
    // -------------------------------------------------------------------------

    private void placeTree(Dimension dimension) {
        int tx = OX - 4;
        int tz = OZ - 4;
        int base = OY + 2; // trunk sits on the grass surface

        // Trunk: 6 logs
        for (int dy = 0; dy <= 5; dy++)
            set(dimension, tx, base + dy, tz, OAK_LOG);

        int top = base + 5;

        // Layer top-2: 5×5 minus far corners
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                setLeaf(dimension, tx + dx, top - 2, tz + dz);
            }

        // Layer top-1: 5×5 minus far corners
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                setLeaf(dimension, tx + dx, top - 1, tz + dz);
            }

        // Top cap: 3×3 × 2 layers
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

    private void placeDecorations(Dimension dimension) {
        int surf = OY + 2;

        // All positions verified inside the circle: dx²+dz² <= 39 (radius 6, threshold 1.1)
        // Safe diagonals: (±4,±4)=32, (±5,±2)=29, (±5,±3)=34, (±6,0)=36, (0,±6)=36

        // Flowers
        set(dimension, OX + 4,  surf, OZ - 3,  DANDELION);   // moved: 4²+3²=25 ✓
        set(dimension, OX + 5,  surf, OZ - 2,  POPPY);       // 29 ✓
        set(dimension, OX - 5,  surf, OZ + 3,  AZURE_BLUET); // 34 ✓
        set(dimension, OX + 3,  surf, OZ + 5,  OXEYE_DAISY); // 34 ✓
        set(dimension, OX - 4,  surf, OZ + 4,  ALLIUM);      // 32 ✓

        // Short grass
        set(dimension, OX + 5,  surf, OZ + 2,  SHORT_GRASS); // 29 ✓
        set(dimension, OX - 5,  surf, OZ - 2,  SHORT_GRASS); // 29 ✓
        set(dimension, OX + 4,  surf, OZ - 3,  SHORT_GRASS); // 25 ✓

        // Fern near tree
        set(dimension, OX - 3,  surf, OZ - 4,  FERN);        // 25 ✓

        // Podzol patch near tree base
        set(dimension, OX - 3,  OY + 1, OZ - 3,  PODZOL);   // 18 ✓
        set(dimension, OX - 4,  OY + 1, OZ - 3,  PODZOL);   // 25 ✓
        set(dimension, OX - 3,  OY + 1, OZ - 4,  PODZOL);   // 25 ✓

        // Lanterns — keep away from corners
        set(dimension, OX + 5,  surf, OZ + 2,  LANTERN);     // 29 ✓  (replaces short grass, that's fine)
        set(dimension, OX - 5,  surf, OZ - 2,  LANTERN);     // 29 ✓

        // Hay bale stack — along the +x axis, safe
        set(dimension, OX + 5,  OY + 2, OZ,    HAY_BLOCK);   // 25 ✓
        set(dimension, OX + 5,  OY + 3, OZ,    HAY_BLOCK);
        set(dimension, OX + 5,  OY + 4, OZ,    TORCH);
    }

    // -------------------------------------------------------------------------
    // Chest
    // -------------------------------------------------------------------------

    private void placeChest(Dimension dimension, List<ItemStack> items) {
        // (4,-4): 4²+4²=32 ✓
        int cx = OX + 4, cy = OY + 2, cz = OZ - 4;

        var chestState = CHEST.getDefaultState()
                .setPropertyValue(
                        BlockPropertyTypes.MINECRAFT_CARDINAL_DIRECTION,
                        org.allaymc.api.block.property.enums.MinecraftCardinalDirection.NORTH
                );
        dimension.setBlockState(cx, cy, cz, chestState);

        if (items == null || items.isEmpty()) return;
        var be = dimension.getBlockEntity(cx, cy, cz);
        if (be instanceof BlockEntityChest chest) {
            var container = chest.getContainer();
            for (ItemStack item : items)
                if (item != null) container.tryAddItem(item);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void set(Dimension dimension, int x, int y, int z,
                     org.allaymc.api.block.type.BlockType<?> type) {
        dimension.setBlockState(x, y, z, type.getDefaultState());
    }
}
