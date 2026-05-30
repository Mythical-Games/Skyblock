package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 9: Unknown block value is zero

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotEmpty;
import org.allaymc.skyblock.model.SkyblockConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Property-based tests for SkyblockConfig.getBlockValue().
 *
 * Property 9: Unknown block value is zero
 * For any block type identifier not present in blockValues,
 * getBlockValue() returns exactly 0.
 *
 * Validates: Requirements 8.4
 */
class UnknownBlockValueTest {

    /**
     * Property 9: Unknown block value is zero.
     *
     * Generates an arbitrary map of known block values and an arbitrary block ID
     * that is NOT present in that map, then verifies getBlockValue returns 0.
     *
     * Validates: Requirements 8.4
     */
    @Property
    void unknownBlockTypeReturnsZero(
            @ForAll("knownBlockMaps") Map<String, Integer> knownBlocks,
            @ForAll @NotEmpty String unknownBlockId
    ) {
        // Ensure the generated unknownBlockId is truly not in the map
        Assume.that(!knownBlocks.containsKey(unknownBlockId));

        SkyblockConfig config = new SkyblockConfig();
        config.setBlockValues(new HashMap<>(knownBlocks));

        int result = config.getBlockValue(unknownBlockId);
        if (result != 0) {
            throw new AssertionError(
                    "getBlockValue() must return 0 for any block ID not in blockValues, but returned " + result
                            + " for blockId='" + unknownBlockId + "'");
        }
    }

    /**
     * Positive property: known block types return their configured value.
     *
     * For any block type identifier present in blockValues,
     * getBlockValue() returns the configured positive value.
     *
     * Validates: Requirements 8.4
     */
    @Property
    void knownBlockTypeReturnsConfiguredValue(
            @ForAll @NotEmpty String blockId,
            @ForAll @IntRange(min = 1) int blockValue
    ) {
        SkyblockConfig config = new SkyblockConfig();
        Map<String, Integer> blockValues = new HashMap<>();
        blockValues.put(blockId, blockValue);
        config.setBlockValues(blockValues);

        int result = config.getBlockValue(blockId);
        if (result != blockValue) {
            throw new AssertionError(
                    "getBlockValue() must return the configured value for a known block ID. "
                            + "Expected " + blockValue + " but got " + result
                            + " for blockId='" + blockId + "'");
        }
    }

    /**
     * Edge case: empty blockValues map — any block ID returns 0.
     *
     * Validates: Requirements 8.4
     */
    @Property
    void emptyConfigReturnsZeroForAnyBlock(@ForAll @NotEmpty String blockId) {
        SkyblockConfig config = new SkyblockConfig();
        config.setBlockValues(new HashMap<>());

        int result = config.getBlockValue(blockId);
        if (result != 0) {
            throw new AssertionError(
                    "getBlockValue() must return 0 when blockValues map is empty, but returned " + result
                            + " for blockId='" + blockId + "'");
        }
    }

    /**
     * Provides maps of String → positive Integer for use as known block value maps.
     */
    @Provide
    Arbitrary<Map<String, Integer>> knownBlockMaps() {
        Arbitrary<String> keys = Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(20);
        Arbitrary<Integer> values = Arbitraries.integers().greaterOrEqual(1);
        return Arbitraries.maps(keys, values);
    }
}
