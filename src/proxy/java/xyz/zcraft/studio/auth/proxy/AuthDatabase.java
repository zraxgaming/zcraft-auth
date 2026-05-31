package xyz.zcraft.studio.auth.proxy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class AuthDatabase implements AutoCloseable {

    private final AuthConfig config;
    private final String table;
    private Connection connection;

    public AuthDatabase(AuthConfig config) {
        this.config = config;
        this.table = config.table();
    }

    public synchronized void initialize() {
        try {
            connection = config.databaseType().equals("sqlite")
                    ? DriverManager.getConnection(jdbcUrl())
                    : DriverManager.getConnection(jdbcUrl(), config.externalUsername(), config.externalPassword());
            createTable();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not initialize auth database", ex);
        }
    }

    public synchronized Optional<Account> find(UUID uuid) {
        String sql = "SELECT uuid, username, password, last_ip, last_login, registered_at FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Account(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("last_ip"),
                        rs.getLong("last_login"),
                        rs.getLong("registered_at")
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load account", ex);
        }
    }

    public synchronized boolean exists(UUID uuid) {
        String sql = "SELECT 1 FROM " + table + " WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not check account", ex);
        }
    }

    public synchronized void register(UUID uuid, String username, String passwordHash, String ip) {
        String sql = "INSERT INTO " + table + " (uuid, username, password, last_ip, last_login, registered_at) VALUES (?,?,?,?,?,?)";
        long now = Instant.now().getEpochSecond();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setString(4, ip);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not register account", ex);
        }
    }

    public synchronized void markLogin(UUID uuid, String ip) {
        String sql = "UPDATE " + table + " SET last_ip = ?, last_login = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update login", ex);
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private String jdbcUrl() {
        return switch (config.databaseType()) {
            case "mysql" -> "jdbc:mysql://" + config.externalHost() + ":" + config.externalPort() + "/"
                    + config.externalDatabase() + "?useSSL=" + config.externalSsl() + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            case "mariadb" -> "jdbc:mariadb://" + config.externalHost() + ":" + config.externalPort() + "/"
                    + config.externalDatabase() + "?useSSL=" + config.externalSsl();
            case "postgresql" -> "jdbc:postgresql://" + config.externalHost() + ":" + config.externalPort() + "/"
                    + config.externalDatabase() + "?sslmode=" + (config.externalSsl() ? "require" : "disable");
            default -> {
                Path file = config.dataDirectory().resolve(config.sqliteFile());
                yield "jdbc:sqlite:" + file.toAbsolutePath();
            }
        };
    }

    private void createTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                        username VARCHAR(16) NOT NULL UNIQUE,
                        password VARCHAR(255),
                        last_ip VARCHAR(45),
                        last_login BIGINT,
                        registered_at BIGINT
                    )
                    """.formatted(table));
        }
    }

    public record Account(UUID uuid, String username, String passwordHash, String lastIp, long lastLogin, long registeredAt) {
    }
}
