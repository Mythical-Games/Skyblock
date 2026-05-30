package org.allaymc.skyblock.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.LeaderboardEntry;
import org.allaymc.skyblock.model.SkyblockPlayerData;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles all JSON serialization/deserialization for the Skyblock plugin.
 *
 * <p>File layout:</p>
 * <pre>
 *   &lt;dataDir&gt;/
 *     islands/&lt;ownerUUID&gt;.json   — IslandMetadata
 *     players/&lt;playerUUID&gt;.json  — SkyblockPlayerData
 *     leaderboard.json            — List&lt;LeaderboardEntry&gt;
 * </pre>
 *
 * <p>All writes retry once after 50 ms on {@link IOException}; a second failure
 * throws {@link PersistenceException}.</p>
 *
 * <p>Requirements: 10.1, 10.2, 10.3, 10.4</p>
 */
public class PersistenceLayer {

    private static final String ISLANDS_DIR = "islands";
    private static final String PLAYERS_DIR = "players";
    private static final String LEADERBOARD_FILE = "leaderboard.json";

    private final Path dataDir;
    private final Gson gson;

    /**
     * Constructs a {@code PersistenceLayer} rooted at {@code dataDir}.
     * A {@link Gson} instance with a UUID {@link TypeAdapter} is created internally.
     *
     * @param dataDir root data directory for the plugin
     */
    public PersistenceLayer(Path dataDir) {
        this.dataDir = dataDir;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(UUID.class, new UuidTypeAdapter())
                .setPrettyPrinting()
                .create();
    }

    // -------------------------------------------------------------------------
    // Directory management
    // -------------------------------------------------------------------------

    /**
     * Creates the root, {@code islands/}, and {@code players/} directories if they
     * do not already exist.
     *
     * @throws PersistenceException if a directory cannot be created
     */
    public void ensureDataDirectories() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(dataDir.resolve(ISLANDS_DIR));
            Files.createDirectories(dataDir.resolve(PLAYERS_DIR));
        } catch (IOException e) {
            throw new PersistenceException("Failed to create data directories under " + dataDir, e);
        }
    }

    // -------------------------------------------------------------------------
    // Island metadata
    // -------------------------------------------------------------------------

    /**
     * Serializes {@code meta} to {@code islands/<ownerUUID>.json}.
     *
     * @throws PersistenceException on second write failure
     */
    public void saveIslandMetadata(IslandMetadata meta) {
        Path file = dataDir.resolve(ISLANDS_DIR).resolve(meta.getOwnerUUID() + ".json");
        writeJson(file, meta);
    }

    /**
     * Deserializes the island metadata for {@code ownerUUID}.
     *
     * @return the metadata, or {@code null} if the file does not exist
     * @throws PersistenceException on read failure
     */
    public IslandMetadata loadIslandMetadata(UUID ownerUUID) {
        Path file = dataDir.resolve(ISLANDS_DIR).resolve(ownerUUID + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        return readJson(file, IslandMetadata.class);
    }

    /**
     * Deletes the island metadata file for {@code ownerUUID}.
     * Does nothing if the file does not exist.
     *
     * @throws PersistenceException if deletion fails for a reason other than non-existence
     */
    public void deleteIslandMetadata(UUID ownerUUID) {
        Path file = dataDir.resolve(ISLANDS_DIR).resolve(ownerUUID + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new PersistenceException("Failed to delete island metadata for " + ownerUUID, e);
        }
    }

    /**
     * Loads all {@code .json} files from the {@code islands/} directory.
     *
     * @return list of all island metadata (never {@code null})
     * @throws PersistenceException on read failure
     */
    public List<IslandMetadata> loadAllIslandMetadata() {
        return loadAllFromDirectory(dataDir.resolve(ISLANDS_DIR), IslandMetadata.class);
    }

    // -------------------------------------------------------------------------
    // Player data
    // -------------------------------------------------------------------------

    /**
     * Serializes {@code data} to {@code players/<playerUUID>.json}.
     *
     * @throws PersistenceException on second write failure
     */
    public void savePlayerData(SkyblockPlayerData data) {
        Path file = dataDir.resolve(PLAYERS_DIR).resolve(data.getPlayerUUID() + ".json");
        writeJson(file, data);
    }

    /**
     * Deserializes the player data for {@code playerUUID}.
     *
     * @return the player data, or {@code null} if the file does not exist
     * @throws PersistenceException on read failure
     */
    public SkyblockPlayerData loadPlayerData(UUID playerUUID) {
        Path file = dataDir.resolve(PLAYERS_DIR).resolve(playerUUID + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        return readJson(file, SkyblockPlayerData.class);
    }

    /**
     * Loads all {@code .json} files from the {@code players/} directory.
     *
     * @return list of all player data (never {@code null})
     * @throws PersistenceException on read failure
     */
    public List<SkyblockPlayerData> loadAllPlayerData() {
        return loadAllFromDirectory(dataDir.resolve(PLAYERS_DIR), SkyblockPlayerData.class);
    }

    // -------------------------------------------------------------------------
    // Leaderboard
    // -------------------------------------------------------------------------

    /**
     * Serializes {@code entries} to {@code leaderboard.json}.
     *
     * @throws PersistenceException on second write failure
     */
    public void saveLeaderboard(List<LeaderboardEntry> entries) {
        Path file = dataDir.resolve(LEADERBOARD_FILE);
        writeJson(file, entries);
    }

    /**
     * Deserializes the leaderboard from {@code leaderboard.json}.
     *
     * @return the leaderboard entries, or an empty list if the file does not exist
     * @throws PersistenceException on read failure
     */
    public List<LeaderboardEntry> loadLeaderboard() {
        Path file = dataDir.resolve(LEADERBOARD_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<LeaderboardEntry>>() {}.getType();
        return readJsonGeneric(file, listType);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Writes {@code obj} as JSON to {@code file}.
     * Retries once after a 50 ms sleep on {@link IOException}.
     * Throws {@link PersistenceException} if the second attempt also fails.
     */
    void writeJson(Path file, Object obj) {
        try {
            doWrite(file, obj);
        } catch (IOException firstEx) {
            // Retry once after 50 ms
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            try {
                doWrite(file, obj);
            } catch (IOException secondEx) {
                throw new PersistenceException(
                        "Failed to write JSON to " + file + " after retry", secondEx);
            }
        }
    }

    private void doWrite(Path file, Object obj) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(obj, writer);
        }
    }

    private <T> T readJson(Path file, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read JSON from " + file, e);
        }
    }

    private <T> T readJsonGeneric(Path file, Type type) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read JSON from " + file, e);
        }
    }

    private <T> List<T> loadAllFromDirectory(Path dir, Class<T> type) {
        List<T> results = new ArrayList<>();
        if (!Files.exists(dir)) {
            return results;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try {
                    T item = readJson(file, type);
                    if (item != null) {
                        results.add(item);
                    }
                } catch (PersistenceException e) {
                    // Log and skip corrupt files; caller can decide what to do
                    System.err.println("[Skyblock] Skipping corrupt file " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new PersistenceException("Failed to scan directory " + dir, e);
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // UUID TypeAdapter
    // -------------------------------------------------------------------------

    /**
     * Serializes {@link UUID} as its canonical string representation and
     * deserializes it back, since Gson does not handle UUID by default.
     */
    private static final class UuidTypeAdapter extends TypeAdapter<UUID> {

        @Override
        public void write(JsonWriter out, UUID value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public UUID read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return UUID.fromString(in.nextString());
        }
    }

    // -------------------------------------------------------------------------
    // Accessor (for tests / other components that need the Gson instance)
    // -------------------------------------------------------------------------

    /**
     * Returns the configured {@link Gson} instance used by this layer.
     * Useful for tests and components that need to share the same serialization config.
     */
    public Gson getGson() {
        return gson;
    }
}
