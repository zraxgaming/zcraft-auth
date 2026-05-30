package xyz.zcraft.studio.auth.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.AbstractDatabaseProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class SQLiteProvider extends AbstractDatabaseProvider {

    private final File dbFile;

    public SQLiteProvider(ZCraftAuth plugin) {
        super(plugin);
        this.dbFile    = new File(plugin.getDataFolder(), plugin.getConfigManager().getSQLiteFile());
        this.tableName = "zcraft_accounts";

        // Default column names for SQLite (no custom column config)
        col.put("uuid",             "uuid");
        col.put("username",         "username");
        col.put("password",         "password");
        col.put("email",            "email");
        col.put("ip",               "last_ip");
        col.put("last_login",       "last_login");
        col.put("registered_at",    "registered_at");
        col.put("two_factor_secret","totp_secret");
        col.put("session_token",    "session_token");
        col.put("session_expiry",   "session_expiry");
        col.put("is_premium",       "is_premium");
        col.put("country_code",     "country_code");
        col.put("is_restricted",    "is_restricted");
        col.put("restricted_ip",    "restricted_ip");
    }

    @Override
    public void initialize() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(1);          // SQLite = single writer
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30_000);
        cfg.setPoolName("ZCraftAuth-SQLite");
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("synchronous",  "NORMAL");
        cfg.addDataSourceProperty("foreign_keys", "true");

        dataSource = new HikariDataSource(cfg);

        try {
            createTableIfAbsent();
            enableWAL();
        } catch (SQLException e) {
            throw new RuntimeException("SQLite init failed", e);
        }
    }

    private void enableWAL() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-64000");
            stmt.execute("PRAGMA temp_store=MEMORY");
        }
    }

    @Override
    public CompletableFuture<String> backup(String targetDir) {
        return CompletableFuture.supplyAsync(() -> {
            File dir = new File(targetDir);
            dir.mkdirs();
            String ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File   dst = new File(dir, "auth_backup_" + ts + ".db");
            try {
                // WAL checkpoint to flush before copy
                try (Connection conn = dataSource.getConnection();
                     Statement  stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                Files.copy(dbFile.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("SQLite backup saved: " + dst.getName());

                // Prune old backups
                pruneBackups(dir, plugin.getConfigManager().getBackupKeepCount());

                return dst.getAbsolutePath();
            } catch (IOException | SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Backup failed", e);
                return null;
            }
        }, executor);
    }

    private void pruneBackups(File dir, int keep) {
        File[] files = dir.listFiles((d, n) -> n.startsWith("auth_backup_") && n.endsWith(".db"));
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - keep; i++) {
            files[i].delete();
            plugin.getLogger().info("Pruned old backup: " + files[i].getName());
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public String getProviderType() { return "SQLite"; }
}
