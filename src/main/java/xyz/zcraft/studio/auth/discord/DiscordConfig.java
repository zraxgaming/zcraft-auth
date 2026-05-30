package xyz.zcraft.studio.auth.discord;

import org.bukkit.configuration.file.FileConfiguration;
import xyz.zcraft.studio.auth.ZCraftAuth;

/**
 * Typed wrapper for the discord.* section of config.yml.
 * All webhook URLs are blank by default so no requests are sent until configured.
 */
public class DiscordConfig {

    private final ZCraftAuth plugin;
    private FileConfiguration cfg;

    public DiscordConfig(ZCraftAuth plugin) {
        this.plugin = plugin;
        this.cfg    = plugin.getConfig();
    }

    public void reload() { this.cfg = plugin.getConfig(); }

    // ─── Global ───────────────────────────────────────────────────────────────

    public boolean isEnabled()    { return plugin.getConfigManager().isDiscordLoggingEnabled() && cfg.getBoolean("discord.enabled", false); }
    public String  getServerName(){ return cfg.getString("discord.server-name", "Server"); }

    // ─── Per-event toggles ────────────────────────────────────────────────────

    public boolean isLoginEnabled()    { return isEnabled() && cfg.getBoolean("discord.events.login",    true); }
    public boolean isRegisterEnabled() { return isEnabled() && cfg.getBoolean("discord.events.register", true); }
    public boolean isLogoutEnabled()   { return isEnabled() && cfg.getBoolean("discord.events.logout",   false); }
    public boolean isFailedEnabled()   { return isEnabled() && cfg.getBoolean("discord.events.failed",   true); }
    public boolean isAdminEnabled()    { return isEnabled() && cfg.getBoolean("discord.events.admin",    true); }
    public boolean isSecurityEnabled() { return isEnabled() && cfg.getBoolean("discord.events.security", true); }

    // ─── Webhook URLs ─────────────────────────────────────────────────────────
    // Each event category can use its own webhook, or fall back to the main one.

    private String get(String key) {
        String specific = cfg.getString(key, "");
        if (specific != null && !specific.isBlank()) return specific;
        return cfg.getString("discord.webhook-url", "");
    }

    public String getLoginWebhook()    { return get("discord.webhooks.login"); }
    public String getRegisterWebhook() { return get("discord.webhooks.register"); }
    public String getLogoutWebhook()   { return get("discord.webhooks.logout"); }
    public String getFailedWebhook()   { return get("discord.webhooks.failed"); }
    public String getAdminWebhook()    { return get("discord.webhooks.admin"); }
    public String getSecurityWebhook() { return get("discord.webhooks.security"); }
}
