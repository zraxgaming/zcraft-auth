package xyz.zcraft.studio.auth.i18n;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Resolves messages from messages.yml first, then optional locale files.
 *
 * Server owners can customize every user-facing message in plugins/Auth/messages.yml.
 * Bundled language files remain available for optional locale fallback.
 */
public class LanguageManager {

    private static final String[] BUNDLED_LANGS = {"en", "ar"};
    private static final String MESSAGES_FILE = "messages.yml";

    private final ZCraftAuth plugin;
    private final File langDir;
    private final File messagesFile;
    private FileConfiguration messages;
    private FileConfiguration bundledMessages;

    // locale -> parsed config
    private final Map<String, FileConfiguration> langs = new HashMap<>();

    // Minecraft locale -> our lang code mapping (e.g. "en_us" -> "en")
    private static final Map<String, String> LOCALE_MAP = Map.ofEntries(
            Map.entry("en_us", "en"), Map.entry("en_gb", "en"),
            Map.entry("ar_sa", "ar"), Map.entry("ar_eg", "ar"),
            Map.entry("de_de", "de"), Map.entry("fr_fr", "fr"),
            Map.entry("es_es", "es"), Map.entry("es_mx", "es"),
            Map.entry("pt_br", "pt"), Map.entry("pt_pt", "pt"),
            Map.entry("ru_ru", "ru"), Map.entry("zh_cn", "zh"),
            Map.entry("zh_tw", "zh"), Map.entry("tr_tr", "tr"),
            Map.entry("pl_pl", "pl")
    );

    public LanguageManager(ZCraftAuth plugin) {
        this.plugin  = plugin;
        this.langDir = new File(plugin.getDataFolder(), "lang");
        this.messagesFile = new File(plugin.getDataFolder(), MESSAGES_FILE);
        if (!langDir.exists()) langDir.mkdirs();
        extractMessages();
        extractBundled();
        loadAll();
    }

    public void reload() {
        langs.clear();
        extractMessages();
        extractBundled();
        loadAll();
    }

    // ─── Message resolution ───────────────────────────────────────────────────

    /**
     * Get a message for a specific player.
     * Resolves: player locale → default locale → English.
     * Replaces {key} placeholders with provided values.
     */
    public String get(Player player, String key, Map<String, String> placeholders) {
        String msg = resolve(player, key);
        if (msg == null) msg = "<red>[Auth] Missing key: " + key;
        if (placeholders != null) {
            for (var e : placeholders.entrySet()) msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return msg;
    }

    public String get(Player player, String key) {
        return get(player, key, null);
    }

    /** Get a message using the server default language (for console messages). */
    public String getDefault(String key) {
        return getDefault(key, null);
    }

    public String getDefault(String key, Map<String, String> placeholders) {
        String msg = getFromMessages(key);
        if (msg == null) msg = getFromLang(plugin.getConfigManager().getDefaultLanguage(), key);
        if (msg == null) msg = getFromLang("en", key);
        if (msg == null) msg = "<red>[Auth] Missing key: " + key;
        if (placeholders != null) {
            for (var e : placeholders.entrySet()) msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return msg;
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private String resolve(Player player, String key) {
        String configured = getFromMessages(key);
        if (configured != null) return configured;

        // Try player locale
        String mcLocale = player.locale().getLanguage() + "_" + player.locale().getCountry();
        String langCode = LOCALE_MAP.getOrDefault(mcLocale.toLowerCase(),
                          LOCALE_MAP.getOrDefault(player.locale().getLanguage().toLowerCase(), null));

        String msg = null;
        if (langCode != null) msg = getFromLang(langCode, key);
        if (msg == null)       msg = getFromLang(plugin.getConfigManager().getDefaultLanguage(), key);
        if (msg == null)       msg = getFromLang("en", key);
        return msg;
    }

    private String getFromMessages(String key) {
        String configured = messages != null ? messages.getString(key) : null;
        if (configured != null) return configured;
        return bundledMessages != null ? bundledMessages.getString(key) : null;
    }

    private String getFromLang(String code, String key) {
        FileConfiguration cfg = langs.get(code);
        if (cfg == null) return null;
        return cfg.getString(key);
    }

    private void extractBundled() {
        for (String lang : BUNDLED_LANGS) {
            File dest = new File(langDir, lang + ".yml");
            if (!dest.exists()) {
                try (InputStream is = plugin.getResource("lang/" + lang + ".yml")) {
                    if (is == null) continue;
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        is.transferTo(fos);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not extract lang/" + lang + ".yml", e);
                }
            }
        }
    }

    private void extractMessages() {
        if (!messagesFile.exists()) {
            plugin.saveResource(MESSAGES_FILE, false);
        }
        try (InputStream is = plugin.getResource(MESSAGES_FILE)) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    bundledMessages = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled " + MESSAGES_FILE, e);
            bundledMessages = new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(messagesFile), StandardCharsets.UTF_8)) {
            messages = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load " + MESSAGES_FILE, e);
            messages = new YamlConfiguration();
        }
    }

    private void loadAll() {
        File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String code = f.getName().replace(".yml", "").toLowerCase();
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                langs.put(code, YamlConfiguration.loadConfiguration(reader));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load lang file: " + f.getName(), e);
            }
        }
        plugin.getLogger().info("Loaded " + langs.size() + " language(s): " + langs.keySet());
    }
}
