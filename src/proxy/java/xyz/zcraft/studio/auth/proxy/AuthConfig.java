package xyz.zcraft.studio.auth.proxy;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuthConfig {

    private final Path dataDirectory;
    private final Map<String, Object> root;

    public AuthConfig(Path dataDirectory, InputStream defaults) {
        this.dataDirectory = dataDirectory;
        try {
            Files.createDirectories(dataDirectory);
            Path config = dataDirectory.resolve("config.yml");
            Yaml yaml = new Yaml();
            if (defaults != null) {
                try (defaults) {
                    Object defaultLoaded = yaml.load(defaults);
                    Map<String, Object> defaultMap = defaultLoaded instanceof Map<?, ?> map
                            ? new LinkedHashMap<>(castMap(map)) : new LinkedHashMap<>();
                    if (Files.notExists(config)) {
                        try (Writer writer = Files.newBufferedWriter(config)) {
                            yaml.dump(defaultMap, writer);
                        }
                    } else {
                        try (Reader reader = Files.newBufferedReader(config)) {
                            Object currentLoaded = yaml.load(reader);
                            Map<String, Object> currentMap = currentLoaded instanceof Map<?, ?> map
                                    ? new LinkedHashMap<>(castMap(map)) : new LinkedHashMap<>();
                            if (mergeMissing(currentMap, defaultMap)) {
                                try (Writer writer = Files.newBufferedWriter(config)) {
                                    yaml.dump(currentMap, writer);
                                }
                            }
                        }
                    }
                }
            }
            try (Reader reader = Files.newBufferedReader(config)) {
                Object loaded = yaml.load(reader);
                this.root = loaded instanceof Map<?, ?> map ? castMap(map) : Map.of();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load config.yml", ex);
        }
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public String databaseType() {
        return string("database.type", "sqlite").toLowerCase();
    }

    public String sqliteFile() {
        return string("database.sqlite.file", "auth.db");
    }

    public String externalHost() {
        return string("database.external.host", "localhost");
    }

    public int externalPort() {
        String type = databaseType();
        int fallback = type.equals("postgresql") ? 5432 : 3306;
        return integer("database.external.port", fallback);
    }

    public String externalDatabase() {
        return string("database.external.database", "zcraft_auth");
    }

    public String externalUsername() {
        return string("database.external.username", "root");
    }

    public String externalPassword() {
        return string("database.external.password", "");
    }

    public boolean externalSsl() {
        return bool("database.external.ssl", false);
    }

    public String table() {
        return string("database.external.table", "zcraft_accounts");
    }

    public int loginTimeoutSeconds() {
        return integer("general.login-timeout", 0);
    }

    public int registerTimeoutSeconds() {
        return integer("general.register-timeout", 0);
    }

    public boolean sessionEnabled() {
        return bool("session.enabled", true);
    }

    public int minPasswordLength() {
        return integer("password.min-length", 5);
    }

    public int maxPasswordLength() {
        return integer("password.max-length", 64);
    }

    public List<String> unsafePasswords() {
        Object value = get("password.unsafe-passwords");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::toLowerCase).toList();
        }
        return List.of("password", "123456", "qwerty", "minecraft", "password123");
    }

    public String prefix() {
        return plain(string("general.prefix", "[Auth] "));
    }

    private String string(String path, String fallback) {
        Object value = get(path);
        return value == null ? fallback : String.valueOf(value);
    }

    private int integer(String path, int fallback) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private boolean bool(String path, boolean fallback) {
        Object value = get(path);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private Object get(String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String plain(String message) {
        return message.replaceAll("<[^>]+>", "");
    }

    @SuppressWarnings("unchecked")
    private static boolean mergeMissing(Map<String, Object> target, Map<String, Object> defaults) {
        boolean changed = false;
        for (var entry : defaults.entrySet()) {
            Object existing = target.get(entry.getKey());
            Object defaultValue = entry.getValue();
            if (existing == null) {
                target.put(entry.getKey(), defaultValue);
                changed = true;
            } else if (existing instanceof Map<?, ?> existingMap && defaultValue instanceof Map<?, ?> defaultMap) {
                changed |= mergeMissing((Map<String, Object>) existingMap, (Map<String, Object>) defaultMap);
            }
        }
        return changed;
    }
}
