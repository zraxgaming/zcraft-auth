package xyz.zcraft.studio.auth.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.config.ConfigManager;
import xyz.zcraft.studio.auth.database.AbstractDatabaseProvider;

import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PostgreSQLProvider extends AbstractDatabaseProvider {

    public PostgreSQLProvider(ZCraftAuth plugin) {
        super(plugin);
        ConfigManager cfg = plugin.getConfigManager();
        this.tableName = cfg.getPgTable();

        // PostgreSQL uses default column names (can extend config if needed)
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
        ConfigManager cfg = plugin.getConfigManager();
        String url = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
                cfg.getPgHost(), cfg.getPgPort(), cfg.getPgDatabase(),
                cfg.isMySQLSSL() ? "require" : "disable");

        HikariConfig hcfg = new HikariConfig();
        hcfg.setJdbcUrl(url);
        hcfg.setDriverClassName("org.postgresql.Driver");
        hcfg.setUsername(cfg.getPgUsername());
        hcfg.setPassword(cfg.getPgPassword());
        hcfg.setMaximumPoolSize(cfg.getPoolMaxSize());
        hcfg.setMinimumIdle(cfg.getPoolMinIdle());
        hcfg.setConnectionTimeout(cfg.getPoolConnTimeout());
        hcfg.setIdleTimeout(cfg.getPoolIdleTimeout());
        hcfg.setMaxLifetime(cfg.getPoolMaxLifetime());
        hcfg.setPoolName("ZCraftAuth-PostgreSQL");
        hcfg.addDataSourceProperty("prepareThreshold", "5");

        dataSource = new HikariDataSource(hcfg);

        try {
            createTableIfAbsent();
        } catch (SQLException e) {
            throw new RuntimeException("PostgreSQL init failed", e);
        }
    }

    @Override
    protected String buildCreateTableSQL() {
        return """
            CREATE TABLE IF NOT EXISTS "%s" (
                "%s" VARCHAR(36)  NOT NULL PRIMARY KEY,
                "%s" VARCHAR(16)  NOT NULL UNIQUE,
                "%s" VARCHAR(255),
                "%s" VARCHAR(255),
                "email_verified"        BOOLEAN NOT NULL DEFAULT FALSE,
                "%s" VARCHAR(45),
                "%s" BIGINT,
                "%s" BIGINT       NOT NULL DEFAULT 0,
                "%s" VARCHAR(128),
                "%s" VARCHAR(128),
                "%s" BIGINT,
                "%s" BOOLEAN      NOT NULL DEFAULT FALSE,
                "%s" VARCHAR(10),
                "%s" BOOLEAN      NOT NULL DEFAULT FALSE,
                "%s" VARCHAR(45),
                "email_pending"         BOOLEAN NOT NULL DEFAULT FALSE,
                "email_pending_token"   VARCHAR(64),
                "email_pending_expiry"  BIGINT
            )
            """.formatted(tableName,
                c("uuid"), c("username"), c("password"), c("email"),
                c("ip"), c("last_login"), c("registered_at"),
                c("two_factor_secret"), c("session_token"), c("session_expiry"),
                c("is_premium"), c("country_code"), c("is_restricted"), c("restricted_ip")
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
                fw.write("-- Provider: PostgreSQL\n\n");

                try (Statement stmt = conn.createStatement();
                     ResultSet rs   = stmt.executeQuery("SELECT * FROM \"" + tableName + "\"")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int cols = meta.getColumnCount();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO \"").append(tableName).append("\" VALUES (");
                        for (int i = 1; i <= cols; i++) {
                            String v = rs.getString(i);
                            sb.append(v == null ? "NULL" : "'" + v.replace("'", "''") + "'");
                            if (i < cols) sb.append(",");
                        }
                        sb.append(");\n");
                        fw.write(sb.toString());
                    }
                }
                plugin.getLogger().info("PostgreSQL backup saved: " + dst.getName());
                return dst.getAbsolutePath();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "PostgreSQL backup failed", e);
                return null;
            }
        }, executor);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public String getProviderType() { return "PostgreSQL"; }
}
