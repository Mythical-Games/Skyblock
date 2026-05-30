package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 3: Player data round-trip

import com.google.gson.Gson;
import net.jqwik.api.*;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.model.SkyblockPlayerData;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Property-based tests for SkyblockPlayerData JSON round-trip serialization.
 *
 * Property 3: Player data round-trip
 * For any valid SkyblockPlayerData, serializing to JSON and then deserializing
 * SHALL produce an object that is field-for-field equal to the original.
 *
 * Validates: Requirements 10.1, 10.2, 10.3
 */
class PlayerDataRoundTripTest {

    /**
     * Shared Gson instance from PersistenceLayer (includes UUID TypeAdapter).
     * We use a temp path since we only need the Gson instance, not actual I/O.
     */
    private final Gson gson = new PersistenceLayer(Path.of(System.getProperty("java.io.tmpdir"))).getGson();

    // -------------------------------------------------------------------------
    // Property 3: Player data round-trip (player WITH island association)
    // -------------------------------------------------------------------------

    /**
     * Property 3a: Round-trip for a player associated with an island.
     *
     * For any SkyblockPlayerData with non-null islandOwnerUUID and role,
     * serialize → deserialize must produce a field-for-field equal object.
     *
     * Validates: Requirements 10.1, 10.2, 10.3
     */
    @Property
    void playerWithIslandRoundTrip(
            @ForAll("playerUUIDs") UUID playerUUID,
            @ForAll("playerNames") String playerName,
            @ForAll("playerUUIDs") UUID islandOwnerUUID,
            @ForAll("islandRoles") IslandRole role
    ) {
        SkyblockPlayerData original = new SkyblockPlayerData();
        original.setPlayerUUID(playerUUID);
        original.setPlayerName(playerName);
        original.setIslandOwnerUUID(islandOwnerUUID);
        original.setRole(role);

        String json = gson.toJson(original);
        SkyblockPlayerData deserialized = gson.fromJson(json, SkyblockPlayerData.class);

        assertFieldsEqual(original, deserialized);
    }

    // -------------------------------------------------------------------------
    // Property 3: Player data round-trip (player WITHOUT island association)
    // -------------------------------------------------------------------------

    /**
     * Property 3b: Round-trip for a player not associated with any island.
     *
     * For any SkyblockPlayerData with null islandOwnerUUID and null role,
     * serialize → deserialize must produce a field-for-field equal object.
     *
     * Validates: Requirements 10.1, 10.2, 10.3
     */
    @Property
    void playerWithoutIslandRoundTrip(
            @ForAll("playerUUIDs") UUID playerUUID,
            @ForAll("playerNames") String playerName
    ) {
        SkyblockPlayerData original = new SkyblockPlayerData();
        original.setPlayerUUID(playerUUID);
        original.setPlayerName(playerName);
        original.setIslandOwnerUUID(null);
        original.setRole(null);

        String json = gson.toJson(original);
        SkyblockPlayerData deserialized = gson.fromJson(json, SkyblockPlayerData.class);

        assertFieldsEqual(original, deserialized);
    }

    // -------------------------------------------------------------------------
    // Providers
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<UUID> playerUUIDs() {
        // Generate random UUIDs from two random longs
        Arbitrary<Long> longs = Arbitraries.longs();
        return longs.flatMap(msb -> longs.map(lsb -> new UUID(msb, lsb)));
    }

    @Provide
    Arbitrary<String> playerNames() {
        // Minecraft player names: 1–16 alphanumeric characters (plus underscore)
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_")
                .ofMinLength(1)
                .ofMaxLength(16);
    }

    @Provide
    Arbitrary<IslandRole> islandRoles() {
        return Arbitraries.of(IslandRole.OWNER, IslandRole.MEMBER);
    }

    // -------------------------------------------------------------------------
    // Assertion helper
    // -------------------------------------------------------------------------

    private void assertFieldsEqual(SkyblockPlayerData expected, SkyblockPlayerData actual) {
        if (actual == null) {
            throw new AssertionError("Deserialized SkyblockPlayerData must not be null");
        }

        if (!expected.getPlayerUUID().equals(actual.getPlayerUUID())) {
            throw new AssertionError(
                    "playerUUID mismatch after round-trip: expected="
                            + expected.getPlayerUUID() + " actual=" + actual.getPlayerUUID());
        }

        if (!expected.getPlayerName().equals(actual.getPlayerName())) {
            throw new AssertionError(
                    "playerName mismatch after round-trip: expected='"
                            + expected.getPlayerName() + "' actual='" + actual.getPlayerName() + "'");
        }

        // islandOwnerUUID — may be null
        if (expected.getIslandOwnerUUID() == null) {
            if (actual.getIslandOwnerUUID() != null) {
                throw new AssertionError(
                        "islandOwnerUUID should be null after round-trip but was: "
                                + actual.getIslandOwnerUUID());
            }
        } else {
            if (!expected.getIslandOwnerUUID().equals(actual.getIslandOwnerUUID())) {
                throw new AssertionError(
                        "islandOwnerUUID mismatch after round-trip: expected="
                                + expected.getIslandOwnerUUID() + " actual=" + actual.getIslandOwnerUUID());
            }
        }

        // role — may be null
        if (expected.getRole() != actual.getRole()) {
            throw new AssertionError(
                    "role mismatch after round-trip: expected="
                            + expected.getRole() + " actual=" + actual.getRole());
        }
    }
}
