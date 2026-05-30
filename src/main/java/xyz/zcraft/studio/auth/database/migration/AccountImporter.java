package xyz.zcraft.studio.auth.database.migration;

import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Imports player accounts from other authentication plugins.
 *
 * Supported sources:
 *   authplus    — Auth+ (SQLite/MySQL, SHA256 hashes)
 *   librelogin  — LibreLogin (SQLite/H2, BCrypt)
 *   limboauth   — LimboAuth (SQLite/MySQL, BCrypt)
 *   nlogin      — nLogin (SQLite/MySQL, BCrypt/SHA256)
 *   openlogin   — OpeNLogin (SQLite/MySQL)
 *   tiauth      — tiAuth (SQLite)
 *
 * Passwords are imported AS-IS. The legacy-migration system in PasswordEngine
 * will automatically re-hash them on first login if auto-rehash is enabled.
 */
public class AccountImporter {

    private final ZCraftAuth plugin;

    public AccountImporter(ZCraftAuth plugin) { this.plugin = plugin; }

    /**
     * Run the import from the given source.
     * @param source  plugin identifier (case-insensitive)
     * @param path    path to the source database file or connection string
     * @return number of accounts imported
     */
    public int importFrom(String source, String path) {
        return switch (source.toLowerCase()) {
            case "authplus", "authme"   -> importAuthMe(path);
            case "librelogin"           -> importLibreLogin(path);
            case "limboauth"            -> importLimboAuth(path);
            case "nlogin"               -> importNLogin(path);
            case "openlogin" -> importOpenLogin(path);
            case "tiauth"               -> importTiAuth(path);
            default -> {
                plugin.getLogger().warning("Unknown import source: " + source);
                yield 0;
            }
        };
    }

    // ─── AuthMe / Auth+ ───────────────────────────────────────────────────────

    private int importAuthMe(String path) {
        plugin.getLogger().info("[Import] Starting AuthMe/Auth+ import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            // AuthMe schema: id, username, realName, password, ip, lastlogin, x, y, z, world, regdate, regip, email, isLogged, hasSession, totp
            String sql = "SELECT realName, password, ip, lastlogin, email FROM authme";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String  username = rs.getString("realName");
                        String  hash     = rs.getString("password");
                        String  ip       = rs.getString("ip");
                        long    lastLogin = rs.getLong("lastlogin");
                        String  email    = rs.getString("email");

                        UUID uuid = generateOfflineUUID(username);

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(normalizeHash(hash))
                                .lastIp(ip)
                                .lastLogin(lastLogin > 0 ? Instant.ofEpochMilli(lastLogin) : null)
                                .email(email)
                                .emailVerified(email != null && !email.isBlank())
                                .registeredAt(Instant.now())
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] Skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] AuthMe import failed", e);
        }

        plugin.getLogger().info("[Import] AuthMe/Auth+: imported " + count + " accounts.");
        return count;
    }

    // ─── LibreLogin ───────────────────────────────────────────────────────────

    private int importLibreLogin(String path) {
        plugin.getLogger().info("[Import] Starting LibreLogin import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            // LibreLogin schema: uuid, username, hashed_password, last_login, last_ip, registration_date, is_premium, ...
            String sql = "SELECT uuid, username, hashed_password, last_login, last_ip, registration_date, email FROM librepanel_players";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        UUID   uuid     = UUID.fromString(rs.getString("uuid"));
                        String username = rs.getString("username");
                        String hash     = rs.getString("hashed_password");
                        String ip       = rs.getString("last_ip");
                        long   lastLogin = rs.getLong("last_login");
                        long   regDate  = rs.getLong("registration_date");
                        String email    = rs.getString("email");

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(hash)
                                .lastIp(ip)
                                .lastLogin(lastLogin > 0 ? Instant.ofEpochSecond(lastLogin) : null)
                                .registeredAt(regDate > 0 ? Instant.ofEpochSecond(regDate) : Instant.now())
                                .email(email)
                                .emailVerified(email != null && !email.isBlank())
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] LibreLogin skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] LibreLogin import failed", e);
        }

        plugin.getLogger().info("[Import] LibreLogin: imported " + count + " accounts.");
        return count;
    }

    // ─── LimboAuth ───────────────────────────────────────────────────────────

    private int importLimboAuth(String path) {
        plugin.getLogger().info("[Import] Starting LimboAuth import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            String sql = "SELECT nickname, lowercaseNickname, hash, ip, loginDate, regDate, totpToken FROM limboauth";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String username = rs.getString("nickname");
                        String hash     = rs.getString("hash");
                        String ip       = rs.getString("ip");
                        long   loginDate = rs.getLong("loginDate");
                        long   regDate  = rs.getLong("regDate");
                        String totp     = rs.getString("totpToken");

                        UUID uuid = generateOfflineUUID(username);

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(hash)
                                .lastIp(ip)
                                .lastLogin(loginDate > 0 ? Instant.ofEpochMilli(loginDate) : null)
                                .registeredAt(regDate > 0 ? Instant.ofEpochMilli(regDate) : Instant.now())
                                .totpSecret(totp)
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] LimboAuth skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] LimboAuth import failed", e);
        }

        plugin.getLogger().info("[Import] LimboAuth: imported " + count + " accounts.");
        return count;
    }

    // ─── nLogin ───────────────────────────────────────────────────────────────

    private int importNLogin(String path) {
        plugin.getLogger().info("[Import] Starting nLogin import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            String sql = "SELECT nickname, password, ip, last_login, registration_date, email FROM nlogin";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String username = rs.getString("nickname");
                        String hash     = rs.getString("password");
                        String ip       = rs.getString("ip");
                        long   lastLogin = rs.getLong("last_login");
                        long   regDate  = rs.getLong("registration_date");
                        String email    = rs.getString("email");

                        UUID uuid = generateOfflineUUID(username);

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(hash)
                                .lastIp(ip)
                                .lastLogin(lastLogin > 0 ? Instant.ofEpochMilli(lastLogin) : null)
                                .registeredAt(regDate > 0 ? Instant.ofEpochMilli(regDate) : Instant.now())
                                .email(email)
                                .emailVerified(email != null && !email.isBlank())
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] nLogin skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] nLogin import failed", e);
        }

        plugin.getLogger().info("[Import] nLogin: imported " + count + " accounts.");
        return count;
    }

    // ─── OpeNLogin ────────────────────────────────────────────────────────────

    private int importOpenLogin(String path) {
        plugin.getLogger().info("[Import] Starting OpeNLogin import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            String sql = "SELECT name, password, ip, lastlogin, email FROM openlogin";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String username = rs.getString("name");
                        String hash     = rs.getString("password");
                        String ip       = rs.getString("ip");
                        long   lastLogin = rs.getLong("lastlogin");
                        String email    = rs.getString("email");

                        UUID uuid = generateOfflineUUID(username);

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(hash)
                                .lastIp(ip)
                                .lastLogin(lastLogin > 0 ? Instant.ofEpochMilli(lastLogin) : null)
                                .registeredAt(Instant.now())
                                .email(email)
                                .emailVerified(email != null && !email.isBlank())
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] OpeNLogin skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] OpeNLogin import failed", e);
        }

        plugin.getLogger().info("[Import] OpeNLogin: imported " + count + " accounts.");
        return count;
    }

    // ─── tiAuth ───────────────────────────────────────────────────────────────

    private int importTiAuth(String path) {
        plugin.getLogger().info("[Import] Starting tiAuth import from: " + path);
        int count = 0;

        try (Connection conn = openSQLite(path)) {
            String sql = "SELECT username, password, lastIP, lastLogin FROM tiauth_players";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        String username = rs.getString("username");
                        String hash     = rs.getString("password");
                        String ip       = rs.getString("lastIP");
                        long   lastLogin = rs.getLong("lastLogin");

                        UUID uuid = generateOfflineUUID(username);

                        PlayerData data = PlayerData.builder(uuid, username)
                                .passwordHash(hash)
                                .lastIp(ip)
                                .lastLogin(lastLogin > 0 ? Instant.ofEpochMilli(lastLogin) : null)
                                .registeredAt(Instant.now())
                                .build();

                        plugin.getDatabase().savePlayer(data).get();
                        count++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Import] tiAuth skipped row: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Import] tiAuth import failed", e);
        }

        plugin.getLogger().info("[Import] tiAuth: imported " + count + " accounts.");
        return count;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Connection openSQLite(String path) throws SQLException {
        File f = new File(path);
        if (!f.exists()) {
            // Try relative to server root
            f = new File(plugin.getServer().getWorldContainer().getParentFile(), path);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
    }

    /**
     * Generate an offline-mode UUID matching Bukkit's algorithm.
     * Used when the source DB stores only usernames (no UUIDs).
     */
    private UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Normalize legacy AuthMe hash format ($SHA$salt$hash → keep as-is for PasswordEngine detection).
     */
    private String normalizeHash(String raw) {
        if (raw == null) return null;
        // AuthMe SHA256: $SHA$salt$hash — keep as-is; PasswordEngine will detect it
        // BCrypt:        $2a$... — keep as-is
        return raw;
    }
}
