package xyz.zcraft.studio.auth.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.config.ConfigManager;
import xyz.zcraft.studio.auth.database.AbstractDatabaseProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLProvider extends AbstractDatabaseProvider {

    private final boolean isMariaDB;

    public MySQLProvider(ZCraftAuth plugin) {
        super(plugin);
        ConfigManager cfg = plugin.getConfigManager();
        this.isMariaDB = cfg.getDatabaseType().equalsIgnoreCase("mariadb");
        this.tableName = cfg.getMySQLTable();

        // Load column names from config (supports forum DB integration)
        for (String key : new String[]{
            "uuid","username","password","email","ip","last_login","registered_at",
            "two_factor_secret","session_token","session_expiry","is_premium",
            "country_code","is_restricted","restricted_ip"
        }) {
            col.put(key, cfg.getColumnName(key));
        }
    }

    @Override
    public void initialize() {
        ConfigManager cfg = plugin.getConfigManager();

        String driver = isMariaDB ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver";
        String scheme = isMariaDB ? "jdbc:mariadb" : "jdbc:mysql";
        String url    = String.format("%s://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                scheme, cfg.getMySQLHost(), cfg.getMySQLPort(), cfg.getMySQLDatabase(), cfg.isMySQLSSL());

        HikariConfig hcfg = new HikariConfig();
        hcfg.setJdbcUrl(url);
        hcfg.setDriverClassName(driver);
        hcfg.setUsername(cfg.getMySQLUsername());
        hcfg.setPassword(cfg.getMySQLPassword());
        hcfg.setMaximumPoolSize(cfg.getPoolMaxSize());
        hcfg.setMinimumIdle(cfg.getPoolMinIdle());
        hcfg.setConnectionTimeout(cfg.getPoolConnTimeout());
        hcfg.setIdleTimeout(cfg.getPoolIdleTimeout());
        hcfg.setMaxLifetime(cfg.getPoolMaxLifetime());
        hcfg.setPoolName("ZCraftAuth-MySQL");
        hcfg.addDataSourceProperty("cachePrepStmts",          "true");
        hcfg.addDataSourceProperty("prepStmtCacheSize",        "250");
        hcfg.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
        hcfg.addDataSourceProperty("useServerPrepStmts",       "true");
        hcfg.addDataSourceProperty("rewriteBatchedStatements", "true");

        dataSource = new HikariDataSource(hcfg);

        try {
            createTableIfAbsent();
        } catch (SQLException e) {
            throw new RuntimeException("MySQL/MariaDB init failed", e);
        }
    }

    @Override
    protected String buildCreateTableSQL() {
        // Override for MySQL-specific types
        return """
            CREATE TABLE IF NOT EXISTS `%s` (
                `%s` VARCHAR(36)  NOT NULL,
                `%s` VARCHAR(16)  NOT NULL,
                `%s` VARCHAR(255) DEFAULT NULL,
                `%s` VARCHAR(255) DEFAULT NULL,
                `email_verified` TINYINT(1) NOT NULL DEFAULT 0,
                `%s` VARCHAR(45)  DEFAULT NULL,
                `%s` BIGINT       DEFAULT NULL,
                `%s` BIGINT       NOT NULL,
                `%s` VARCHAR(128) DEFAULT NULL,
                `%s` VARCHAR(128) DEFAULT NULL,
                `%s` BIGINT       DEFAULT NULL,
                `%s` TINYINT(1)   NOT NULL DEFAULT 0,
                `%s` VARCHAR(10)  DEFAULT NULL,
                `%s` TINYINT(1)   NOT NULL DEFAULT 0,
                `%s` VARCHAR(45)  DEFAULT NULL,
                `email_pending`         TINYINT(1) NOT NULL DEFAULT 0,
                `email_pending_token`   VARCHAR(64) DEFAULT NULL,
                `email_pending_expiry`  BIGINT      DEFAULT NULL,
                PRIMARY KEY (`%s`),
                UNIQUE KEY `uk_username` (`%s`),
                INDEX `idx_session` (`%s`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.formatted(
                tableName,
                c("uuid"), c("username"), c("password"), c("email"),
                c("ip"), c("last_login"), c("registered_at"),
                c("two_factor_secret"), c("session_token"), c("session_expiry"),
                c("is_premium"), c("country_code"),
                c("is_restricted"), c("restricted_ip"),
                c("uuid"), c("username"), c("session_token")
        );
    }

    @Override
    public CompletableFuture<String> backup(String targetDir) {
        return CompletableFuture.supplyAsync(() -> {
            File dir = new File(targetDir);
            dir.mkdirs();
            String ts  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File   dst = new File(dir, "auth_backup_" + ts + ".sql");

            try (Connection conn = dataSource.getConnection();
                 FileWriter fw   = new FileWriter(dst)) {

                fw.write("-- Auth backup - " + ts + "\n");
                fw.write("-- Provider: " + getProviderType() + "\n\n");

                // Dump CREATE TABLE
                try (Statement stmt = conn.createStatement();
                     ResultSet rs   = stmt.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
                    if (rs.next()) {
                        fw.write(rs.getString(2));
                        fw.write(";\n\n");
                    }
                }

                // Dump rows
                try (Statement stmt = conn.createStatement();
                     ResultSet rs   = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO `").append(tableName).append("` VALUES (");
                        for (int i = 1; i <= cols; i++) {
                            String v = rs.getString(i);
                            sb.append(v == null ? "NULL" : "'" + v.replace("'", "\\'") + "'");
                            if (i < cols) sb.append(",");
                        }
                        sb.append(");\n");
                        fw.write(sb.toString());
                    }
                }

                plugin.getLogger().info("MySQL backup saved: " + dst.getName());
                pruneBackups(dir, plugin.getConfigManager().getBackupKeepCount());
                return dst.getAbsolutePath();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "MySQL backup failed", e);
                return null;
            }
        }, executor);
    }

    private void pruneBackups(File dir, int keep) {
        File[] files = dir.listFiles((d, n) -> n.startsWith("auth_backup_") && n.endsWith(".sql"));
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(java.io.File::lastModified));
        for (int i = 0; i < files.length - keep; i++) files[i].delete();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public String getProviderType() { return isMariaDB ? "MariaDB" : "MySQL"; }
}
