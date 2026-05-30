package xyz.zcraft.studio.auth.database;

import com.zaxxer.hikari.HikariDataSource;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Shared SQL logic for all JDBC-based database providers.
 * Subclasses provide the HikariDataSource and table/column names.
 */
public abstract class AbstractDatabaseProvider implements DatabaseProvider {

    protected final ZCraftAuth plugin;
    protected HikariDataSource  dataSource;
    protected String            tableName;

    // Column name map — populated by subclass from config
    protected final Map<String, String> col = new ConcurrentHashMap<>();

    // In-memory cache: UUID -> PlayerData
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    // Dedicated async executor (virtual threads on Java 21)
    protected final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    protected AbstractDatabaseProvider(ZCraftAuth plugin) {
        this.plugin = plugin;
    }

    // ─── Column helpers ───────────────────────────────────────────────────────

    protected String c(String key) { return col.getOrDefault(key, key); }

    // ─── Table DDL ────────────────────────────────────────────────────────────

    protected String buildCreateTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                %s VARCHAR(36) NOT NULL PRIMARY KEY,
                %s VARCHAR(16) NOT NULL UNIQUE,
                %s VARCHAR(255),
                %s VARCHAR(255),
                email_verified BOOLEAN DEFAULT FALSE,
                %s VARCHAR(45),
                %s BIGINT,
                %s BIGINT,
                %s VARCHAR(128),
                %s VARCHAR(128),
                %s BIGINT,
                %s BOOLEAN DEFAULT FALSE,
                %s VARCHAR(10),
                %s BOOLEAN DEFAULT FALSE,
                %s VARCHAR(45),
                email_pending BOOLEAN DEFAULT FALSE,
                email_pending_token VARCHAR(64),
                email_pending_expiry BIGINT
            )
            """.formatted(
                tableName,
                c("uuid"), c("username"), c("password"), c("email"),
                c("ip"), c("last_login"), c("registered_at"),
                c("two_factor_secret"), c("session_token"), c("session_expiry"),
                c("is_premium"), c("country_code"),
                c("is_restricted"), c("restricted_ip")
        );
    }

    protected void createTableIfAbsent() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute(buildCreateTableSQL());
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_username ON " + tableName + " (" + c("username") + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_session  ON " + tableName + " (" + c("session_token") + ")");
        }
    }

    // ─── Row → PlayerData ─────────────────────────────────────────────────────

    protected PlayerData fromRow(ResultSet rs) throws SQLException {
        Instant lastLogin  = rs.getLong(c("last_login"))  == 0 ? null : Instant.ofEpochSecond(rs.getLong(c("last_login")));
        Instant regAt      = rs.getLong(c("registered_at")) == 0 ? Instant.now() : Instant.ofEpochSecond(rs.getLong(c("registered_at")));
        Instant sessExp    = rs.getLong(c("session_expiry")) == 0 ? null : Instant.ofEpochSecond(rs.getLong(c("session_expiry")));
        Instant pendingExp = rs.getLong("email_pending_expiry") == 0 ? null : Instant.ofEpochSecond(rs.getLong("email_pending_expiry"));

        return PlayerData.builder(UUID.fromString(rs.getString(c("uuid"))), rs.getString(c("username")))
                .passwordHash(rs.getString(c("password")))
                .email(rs.getString(c("email")))
                .emailVerified(rs.getBoolean("email_verified"))
                .lastIp(rs.getString(c("ip")))
                .lastLogin(lastLogin)
                .registeredAt(regAt)
                .totpSecret(rs.getString(c("two_factor_secret")))
                .sessionToken(rs.getString(c("session_token")))
                .sessionExpiry(sessExp)
                .premium(rs.getBoolean(c("is_premium")))
                .countryCode(rs.getString(c("country_code")))
                .restricted(rs.getBoolean(c("is_restricted")))
                .restrictedIp(rs.getString(c("restricted_ip")))
                .emailPending(rs.getBoolean("email_pending"))
                .emailPendingToken(rs.getString("email_pending_token"))
                .emailPendingExpiry(pendingExp)
                .build();
    }

    // ─── CRUD implementation ──────────────────────────────────────────────────

    @Override
    public CompletableFuture<Optional<PlayerData>> findByUUID(UUID uuid) {
        // Check cache first
        if (plugin.getConfigManager().isCacheEnabled()) {
            CacheEntry entry = cache.get(uuid);
            if (entry != null && !entry.expired()) return CompletableFuture.completedFuture(Optional.of(entry.data));
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE " + c("uuid") + " = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerData d = fromRow(rs);
                        cachePlayer(d);
                        return Optional.of(d);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error findByUUID", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE " + c("username") + " = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerData d = fromRow(rs);
                        cachePlayer(d);
                        return Optional.of(d);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error findByUsername", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> findByUsernameCaseInsensitive(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE LOWER(" + c("username") + ") = LOWER(?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(fromRow(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error findByUsernameCaseInsensitive", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> findBySessionToken(String token) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE " + c("session_token") + " = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(fromRow(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error findBySessionToken", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerData d) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO %s (%s,%s,%s,%s,email_verified,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,
                    email_pending,email_pending_token,email_pending_expiry)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(tableName,
                    c("uuid"), c("username"), c("password"), c("email"),
                    c("ip"), c("last_login"), c("registered_at"),
                    c("two_factor_secret"), c("session_token"), c("session_expiry"),
                    c("is_premium"), c("country_code"), c("is_restricted"), c("restricted_ip"));
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                bindPlayerData(ps, d);
                ps.executeUpdate();
                cachePlayer(d);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error savePlayer", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> updatePlayer(PlayerData d) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE %s SET
                    %s=?,%s=?,%s=?,email_verified=?,%s=?,%s=?,%s=?,%s=?,%s=?,%s=?,%s=?,%s=?,%s=?,
                    email_pending=?,email_pending_token=?,email_pending_expiry=?
                WHERE %s=?
                """.formatted(tableName,
                    c("password"), c("email"), c("ip"),
                    c("last_login"), c("registered_at"), c("two_factor_secret"),
                    c("session_token"), c("session_expiry"), c("is_premium"),
                    c("country_code"), c("is_restricted"), c("restricted_ip"),
                    c("uuid"));
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, d.passwordHash());
                ps.setString(i++, d.email());
                ps.setString(i++, d.lastIp());
                ps.setBoolean(i++, d.emailVerified());
                ps.setLong(i++, d.lastLogin() != null ? d.lastLogin().getEpochSecond() : 0);
                ps.setLong(i++, d.registeredAt().getEpochSecond());
                ps.setString(i++, d.totpSecret());
                ps.setString(i++, d.sessionToken());
                ps.setLong(i++, d.sessionExpiry() != null ? d.sessionExpiry().getEpochSecond() : 0);
                ps.setBoolean(i++, d.premium());
                ps.setString(i++, d.countryCode());
                ps.setBoolean(i++, d.restricted());
                ps.setString(i++, d.restrictedIp());
                ps.setBoolean(i++, d.emailPending());
                ps.setString(i++, d.emailPendingToken());
                ps.setLong(i++, d.emailPendingExpiry() != null ? d.emailPendingExpiry().getEpochSecond() : 0);
                ps.setString(i, d.uuid().toString());
                ps.executeUpdate();
                cachePlayer(d);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error updatePlayer", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deletePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM " + tableName + " WHERE " + c("uuid") + " = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                cache.remove(uuid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error deletePlayer", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(UUID uuid) {
        if (plugin.getConfigManager().isCacheEnabled() && cache.containsKey(uuid)) {
            CacheEntry e = cache.get(uuid);
            if (!e.expired()) return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tableName + " WHERE " + c("uuid") + " = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error isRegistered", e);
                return false;
            }
        }, executor);
    }

    // ─── Cache helpers ────────────────────────────────────────────────────────

    protected void cachePlayer(PlayerData data) {
        if (!plugin.getConfigManager().isCacheEnabled()) return;
        long expiry = System.currentTimeMillis() + (plugin.getConfigManager().getCacheExpiry() * 1000L);
        cache.put(data.uuid(), new CacheEntry(data, expiry));
    }

    public void invalidateCache(UUID uuid) { cache.remove(uuid); }

    private void bindPlayerData(PreparedStatement ps, PlayerData d) throws SQLException {
        int i = 1;
        ps.setString(i++, d.uuid().toString());
        ps.setString(i++, d.username());
        ps.setString(i++, d.passwordHash());
        ps.setString(i++, d.email());
        ps.setBoolean(i++, d.emailVerified());
        ps.setString(i++, d.lastIp());
        ps.setLong(i++, d.lastLogin() != null ? d.lastLogin().getEpochSecond() : 0);
        ps.setLong(i++, d.registeredAt().getEpochSecond());
        ps.setString(i++, d.totpSecret());
        ps.setString(i++, d.sessionToken());
        ps.setLong(i++, d.sessionExpiry() != null ? d.sessionExpiry().getEpochSecond() : 0);
        ps.setBoolean(i++, d.premium());
        ps.setString(i++, d.countryCode());
        ps.setBoolean(i++, d.restricted());
        ps.setString(i++, d.restrictedIp());
        ps.setBoolean(i++, d.emailPending());
        ps.setString(i++, d.emailPendingToken());
        ps.setLong(i, d.emailPendingExpiry() != null ? d.emailPendingExpiry().getEpochSecond() : 0);
    }

    @Override
    public String getTableName() { return tableName; }

    // ─── Inner cache entry ────────────────────────────────────────────────────

    private record CacheEntry(PlayerData data, long expiryMs) {
        boolean expired() { return System.currentTimeMillis() > expiryMs; }
    }
}
