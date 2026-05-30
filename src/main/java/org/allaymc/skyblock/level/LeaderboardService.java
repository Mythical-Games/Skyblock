package org.allaymc.skyblock.level;

import org.allaymc.skyblock.model.IslandMetadata;
import org.allaymc.skyblock.model.LeaderboardEntry;
import org.allaymc.skyblock.persistence.PersistenceLayer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Maintains a sorted top-10 list of islands by level.
 *
 * <p>The internal list is protected by a {@link ReadWriteLock}: multiple threads
 * may read concurrently, but writes are exclusive. All mutating operations
 * re-sort descending by level and trim to at most 10 entries before persisting.</p>
 *
 * <p>Requirements: 9.1, 9.2, 9.3</p>
 */
public class LeaderboardService {

    /** Maximum number of entries kept in the leaderboard. */
    private static final int MAX_ENTRIES = 10;

    /** Comparator: descending by level. */
    private static final Comparator<LeaderboardEntry> DESCENDING_LEVEL =
            Comparator.comparingLong(LeaderboardEntry::getLevel).reversed();

    /** In-memory leaderboard, always sorted descending and trimmed to {@link #MAX_ENTRIES}. */
    private final List<LeaderboardEntry> leaderboard = new ArrayList<>();

    /** Guards {@link #leaderboard}. */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Persistence layer used to save/load the leaderboard. May be {@code null} if not wired. */
    private PersistenceLayer persistenceLayer;

    /**
     * Constructs a {@code LeaderboardService} without a persistence layer.
     * Call {@link #setPersistenceLayer(PersistenceLayer)} before any mutating operation
     * that should persist, or use {@link #LeaderboardService(PersistenceLayer)}.
     */
    public LeaderboardService() {}

    /**
     * Constructs a {@code LeaderboardService} backed by the given persistence layer.
     *
     * @param persistenceLayer the layer used to save/load leaderboard data
     */
    public LeaderboardService(PersistenceLayer persistenceLayer) {
        this.persistenceLayer = persistenceLayer;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Inserts or updates the entry for {@code ownerUUID}, then re-sorts descending
     * by level, trims to {@link #MAX_ENTRIES}, and persists via
     * {@link PersistenceLayer#saveLeaderboard(List)}.
     *
     * @param ownerUUID the island owner's UUID
     * @param ownerName the island owner's display name
     * @param level     the new island level
     */
    public void update(UUID ownerUUID, String ownerName, long level) {
        lock.writeLock().lock();
        try {
            // Remove existing entry for this owner (if any)
            Iterator<LeaderboardEntry> it = leaderboard.iterator();
            while (it.hasNext()) {
                if (ownerUUID.equals(it.next().getOwnerUUID())) {
                    it.remove();
                    break;
                }
            }

            // Insert updated entry
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setOwnerUUID(ownerUUID);
            entry.setOwnerName(ownerName);
            entry.setLevel(level);
            leaderboard.add(entry);

            sortAndTrim();
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a defensive copy of the current top-10 list, sorted descending by level.
     *
     * @return immutable snapshot of the leaderboard (at most 10 entries)
     */
    public List<LeaderboardEntry> getTop10() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(leaderboard);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Rebuilds the leaderboard from scratch using all provided island metadata,
     * re-sorts descending by level, trims to {@link #MAX_ENTRIES}, and persists.
     *
     * @param allIslands all island metadata to rebuild from
     */
    public void rebuild(Collection<IslandMetadata> allIslands) {
        lock.writeLock().lock();
        try {
            leaderboard.clear();
            for (IslandMetadata meta : allIslands) {
                LeaderboardEntry entry = new LeaderboardEntry();
                entry.setOwnerUUID(meta.getOwnerUUID());
                entry.setOwnerName(meta.getOwnerName());
                entry.setLevel(meta.getIslandLevel());
                leaderboard.add(entry);
            }
            sortAndTrim();
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads the leaderboard from disk using the given data directory.
     * Creates a temporary {@link PersistenceLayer} if one is not already set.
     *
     * @param dataDir the plugin data directory
     */
    public void loadFromDisk(Path dataDir) {
        PersistenceLayer pl = persistenceLayer != null
                ? persistenceLayer
                : new PersistenceLayer(dataDir);

        List<LeaderboardEntry> loaded = pl.loadLeaderboard();

        lock.writeLock().lock();
        try {
            leaderboard.clear();
            leaderboard.addAll(loaded);
            sortAndTrim();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Saves the current leaderboard to disk using the given data directory.
     * Creates a temporary {@link PersistenceLayer} if one is not already set.
     *
     * @param dataDir the plugin data directory
     */
    public void saveToDisk(Path dataDir) {
        PersistenceLayer pl = persistenceLayer != null
                ? persistenceLayer
                : new PersistenceLayer(dataDir);

        lock.readLock().lock();
        try {
            pl.saveLeaderboard(new ArrayList<>(leaderboard));
        } finally {
            lock.readLock().unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Wiring
    // -------------------------------------------------------------------------

    /**
     * Sets the persistence layer used by {@link #update} and {@link #rebuild}.
     *
     * @param persistenceLayer the persistence layer to use
     */
    public void setPersistenceLayer(PersistenceLayer persistenceLayer) {
        this.persistenceLayer = persistenceLayer;
    }

    // -------------------------------------------------------------------------
    // Internal helpers (must be called while holding the write lock)
    // -------------------------------------------------------------------------

    /**
     * Sorts {@link #leaderboard} descending by level and trims to {@link #MAX_ENTRIES}.
     * Caller must hold the write lock.
     */
    private void sortAndTrim() {
        leaderboard.sort(DESCENDING_LEVEL);
        while (leaderboard.size() > MAX_ENTRIES) {
            leaderboard.remove(leaderboard.size() - 1);
        }
    }

    /**
     * Persists the current leaderboard if a {@link PersistenceLayer} is available.
     * Caller must hold the write lock.
     */
    private void persist() {
        if (persistenceLayer != null) {
            persistenceLayer.saveLeaderboard(new ArrayList<>(leaderboard));
        }
    }
}
