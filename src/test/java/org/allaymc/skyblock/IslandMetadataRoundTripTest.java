package org.allaymc.skyblock;

// Feature: skyblock-plugin, Property 2: Island metadata round-trip

import com.google.gson.Gson;
import net.jqwik.api.*;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.IslandRole;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Property-based tests for IslandMetadata JSON round-trip serialization.
 *
 * Property 2: Island metadata round-trip
 * For any valid IslandMetadata, serialize to JSON then deserialize;
 * result must be field-for-field equal to the original.
 *
 * Validates: Requirements 10.1, 10.2, 10.3
 */
class IslandMetadataRoundTripTest {

    /**
     * Obtain the Gson instance from PersistenceLayer so the UUID TypeAdapter is used.
     * We use a temp path — no actual I/O is performed in these tests.
     */
    private final Gson gson = new PersistenceLayer(
            Path.of(System.getProperty("java.io.tmpdir"), "skyblock-test")
    ).getGson();

    /**
     * Property 2: Island metadata round-trip.
     *
     * For any valid IslandMetadata, serializing to JSON and deserializing back
     * must produce an object that is field-for-field equal to the original.
     *
     * Validates: Requirements 10.1, 10.2, 10.3
     */
    @Property
    void islandMetadataRoundTrip(@ForAll("arbitraryIslandMetadata") IslandMetadata original) {
        // Serialize to JSON
        String json = gson.toJson(original);

        // Deserialize back
        IslandMetadata restored = gson.fromJson(json, IslandMetadata.class);

        // Compare all fields
        assertFieldsEqual(original, restored);
    }

    // -------------------------------------------------------------------------
    // Assertion helper
    // -------------------------------------------------------------------------

    private void assertFieldsEqual(IslandMetadata expected, IslandMetadata actual) {
        if (!expected.getOwnerUUID().equals(actual.getOwnerUUID())) {
            throw new AssertionError("ownerUUID mismatch: expected=" + expected.getOwnerUUID()
                    + " actual=" + actual.getOwnerUUID());
        }
        if (!expected.getOwnerName().equals(actual.getOwnerName())) {
            throw new AssertionError("ownerName mismatch: expected=" + expected.getOwnerName()
                    + " actual=" + actual.getOwnerName());
        }
        if (!expected.getWorldName().equals(actual.getWorldName())) {
            throw new AssertionError("worldName mismatch: expected=" + expected.getWorldName()
                    + " actual=" + actual.getWorldName());
        }
        if (Double.compare(expected.getSpawnX(), actual.getSpawnX()) != 0) {
            throw new AssertionError("spawnX mismatch: expected=" + expected.getSpawnX()
                    + " actual=" + actual.getSpawnX());
        }
        if (Double.compare(expected.getSpawnY(), actual.getSpawnY()) != 0) {
            throw new AssertionError("spawnY mismatch: expected=" + expected.getSpawnY()
                    + " actual=" + actual.getSpawnY());
        }
        if (Double.compare(expected.getSpawnZ(), actual.getSpawnZ()) != 0) {
            throw new AssertionError("spawnZ mismatch: expected=" + expected.getSpawnZ()
                    + " actual=" + actual.getSpawnZ());
        }
        if (Float.compare(expected.getSpawnYaw(), actual.getSpawnYaw()) != 0) {
            throw new AssertionError("spawnYaw mismatch: expected=" + expected.getSpawnYaw()
                    + " actual=" + actual.getSpawnYaw());
        }
        if (Float.compare(expected.getSpawnPitch(), actual.getSpawnPitch()) != 0) {
            throw new AssertionError("spawnPitch mismatch: expected=" + expected.getSpawnPitch()
                    + " actual=" + actual.getSpawnPitch());
        }
        if (expected.getIslandLevel() != actual.getIslandLevel()) {
            throw new AssertionError("islandLevel mismatch: expected=" + expected.getIslandLevel()
                    + " actual=" + actual.getIslandLevel());
        }
        if (expected.getCreatedAt() != actual.getCreatedAt()) {
            throw new AssertionError("createdAt mismatch: expected=" + expected.getCreatedAt()
                    + " actual=" + actual.getCreatedAt());
        }
        // Compare members map
        Map<UUID, IslandRole> expectedMembers = expected.getMembers() != null
                ? expected.getMembers() : new HashMap<>();
        Map<UUID, IslandRole> actualMembers = actual.getMembers() != null
                ? actual.getMembers() : new HashMap<>();
        if (!expectedMembers.equals(actualMembers)) {
            throw new AssertionError("members mismatch: expected=" + expectedMembers
                    + " actual=" + actualMembers);
        }
    }

    // -------------------------------------------------------------------------
    // Arbitrary providers
    // -------------------------------------------------------------------------

    /**
     * Generates arbitrary valid IslandMetadata objects with random field values.
     * Uses chained flatMap to stay within jqwik's 8-argument Combinators limit.
     */
    @Provide
    Arbitrary<IslandMetadata> arbitraryIslandMetadata() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(16);
        Arbitrary<Double> coords = Arbitraries.doubles()
                .between(-30_000_000.0, 30_000_000.0);
        Arbitrary<Float> angles = Arbitraries.floats()
                .between(-180.0f, 180.0f);
        Arbitrary<Long> levels = Arbitraries.longs()
                .between(0L, Long.MAX_VALUE / 2);
        Arbitrary<Long> timestamps = Arbitraries.longs()
                .between(0L, System.currentTimeMillis() + 1_000_000_000L);
        Arbitrary<Map<UUID, IslandRole>> membersMap = arbitraryMembersMap();

        // First combine: ownerUUID, ownerName, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, islandLevel
        return Combinators.combine(uuids, names, coords, coords, coords, angles, angles, levels)
                .flatAs((ownerUUID, ownerName, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, islandLevel) ->
                        // Second combine: createdAt, members
                        Combinators.combine(timestamps, membersMap)
                                .as((createdAt, members) -> {
                                    IslandMetadata meta = new IslandMetadata();
                                    meta.setOwnerUUID(ownerUUID);
                                    meta.setOwnerName(ownerName);
                                    meta.setWorldName("skyblock_" + ownerUUID);
                                    meta.setSpawnX(spawnX);
                                    meta.setSpawnY(spawnY);
                                    meta.setSpawnZ(spawnZ);
                                    meta.setSpawnYaw(spawnYaw);
                                    meta.setSpawnPitch(spawnPitch);
                                    meta.setIslandLevel(islandLevel);
                                    meta.setCreatedAt(createdAt);
                                    meta.setMembers(members);
                                    return meta;
                                })
                );
    }

    /**
     * Generates a Map&lt;UUID, IslandRole&gt; with 0–3 member entries.
     */
    @Provide
    Arbitrary<Map<UUID, IslandRole>> arbitraryMembersMap() {
        Arbitrary<UUID> uuids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<IslandRole> roles = Arbitraries.of(IslandRole.MEMBER);
        return Arbitraries.maps(uuids, roles).ofMaxSize(3);
    }
}
