package xyz.zcraft.studio.auth.database;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Database abstraction layer.
 * All read/write operations return {@link CompletableFuture} for non-blocking async use.
 */
public interface DatabaseProvider {

    /** Called once on plugin enable. Must create tables/indices if absent. */
    void initialize();

    /** Gracefully close connections/pool. */
    void close();

    // ─── Account CRUD ─────────────────────────────────────────────────────────

    CompletableFuture<Optional<PlayerData>> findByUUID(UUID uuid);

    CompletableFuture<Optional<PlayerData>> findByUsername(String username);

    /** Case-insensitive username search — used for spoofing protection. */
    CompletableFuture<Optional<PlayerData>> findByUsernameCaseInsensitive(String username);

    CompletableFuture<Void> savePlayer(PlayerData data);

    CompletableFuture<Void> updatePlayer(PlayerData data);

    CompletableFuture<Void> deletePlayer(UUID uuid);

    CompletableFuture<Boolean> isRegistered(UUID uuid);

    // ─── Session ──────────────────────────────────────────────────────────────

    CompletableFuture<Optional<PlayerData>> findBySessionToken(String token);

    // ─── Backup ───────────────────────────────────────────────────────────────

    /**
     * Exports a SQL/JSON dump to the given file path.
     * @return path of the created backup file
     */
    CompletableFuture<String> backup(String targetPath);

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Returns the underlying table name (for logging). */
    String getTableName();

    /** Returns the provider type label, e.g. "SQLite". */
    String getProviderType();
}
