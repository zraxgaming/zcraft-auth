package xyz.zcraft.studio.auth.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Sends rich embed messages to Discord webhooks on auth events.
 *
 * Separate webhook URLs can be configured for each event category:
 *   login      — successful logins (normal, session, premium)
 *   register   — new account registrations
 *   logout     — player logouts and disconnects
 *   failed     — failed login attempts
 *   admin      — admin actions (forcelogin, restrict, unregister, etc.)
 *   security   — antibot activation, spoofing, country blocks, 2FA failures
 *
 * All sends are asynchronous and non-blocking.
 */
public class DiscordLogger {

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    // Embed colours (hex int)
    private static final int COLOR_SUCCESS  = 0x00B4FF;
    private static final int COLOR_REGISTER = 0xA368FC;
    private static final int COLOR_LOGOUT   = 0x888888;
    private static final int COLOR_FAILED   = 0xFF6B35;
    private static final int COLOR_ADMIN    = 0xFFD700;
    private static final int COLOR_SECURITY = 0xFF2222;
    private static final int COLOR_INFO     = 0x5865F2;

    private final ZCraftAuth plugin;
    private final OkHttpClient http;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private final DiscordConfig cfg;

    public DiscordLogger(ZCraftAuth plugin) {
        this.plugin = plugin;
        this.http   = new OkHttpClient();
        this.cfg    = new DiscordConfig(plugin);
    }

    public void reload() { cfg.reload(); }

    // ─── Public logging methods ───────────────────────────────────────────────

    /** Successful login (normal, session restore, premium bypass, force). */
    public void logLogin(String username, String ip, String method) {
        if (!cfg.isLoginEnabled()) return;
        String title = switch (method.toUpperCase()) {
            case "SESSION" -> "🔄 Session Restored";
            case "PREMIUM" -> "⭐ Premium Auto-Login";
            case "FORCE"   -> "🛡 Force Login";
            default        -> "✅ Player Logged In";
        };
        int color = switch (method.toUpperCase()) {
            case "PREMIUM" -> COLOR_REGISTER;
            case "FORCE"   -> COLOR_ADMIN;
            default        -> COLOR_SUCCESS;
        };
        send(cfg.getLoginWebhook(),
                embed(title, color)
                        .field("Player", username, true)
                        .field("Method", method, true)
                        .field("IP", ip, true)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    /** New player registration. */
    public void logRegister(String username, String ip) {
        if (!cfg.isRegisterEnabled()) return;
        send(cfg.getRegisterWebhook(),
                embed("📝 New Account Registered", COLOR_REGISTER)
                        .field("Player", username, true)
                        .field("IP", ip, true)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    /** Player logout. */
    public void logLogout(String username, String ip, String reason) {
        if (!cfg.isLogoutEnabled()) return;
        send(cfg.getLogoutWebhook(),
                embed("🚪 Player Logged Out", COLOR_LOGOUT)
                        .field("Player", username, true)
                        .field("Reason", reason, true)
                        .field("IP", ip, true)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    /** Failed login attempt. */
    public void logFailedLogin(String username, String ip, int attemptCount, int maxAttempts) {
        if (!cfg.isFailedEnabled()) return;
        send(cfg.getFailedWebhook(),
                embed("⚠️ Failed Login Attempt", COLOR_FAILED)
                        .field("Player", username, true)
                        .field("IP", ip, true)
                        .field("Attempts", attemptCount + " / " + maxAttempts, true)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    /** Admin action (forcelogin, restrict, unregister, etc.). */
    public void logAdminAction(String admin, String action, String target, String details) {
        if (!cfg.isAdminEnabled()) return;
        send(cfg.getAdminWebhook(),
                embed("🛡️ Admin Action", COLOR_ADMIN)
                        .field("Admin", admin, true)
                        .field("Action", action, true)
                        .field("Target", target, true)
                        .field("Details", details, false)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    /** Security events — antibot, spoofing, country block, 2FA failures, attempt bans. */
    public void logSecurityAlert(String username, String ip, String reason) {
        if (!cfg.isSecurityEnabled()) return;
        send(cfg.getSecurityWebhook(),
                embed("🚨 Security Alert", COLOR_SECURITY)
                        .field("Username", username, true)
                        .field("IP", ip, true)
                        .field("Reason", reason, false)
                        .timestamp()
                        .footer("Auth - Security", null)
                        .build());
    }

    /** AntiBot state change. */
    public void logAntibot(boolean activated) {
        if (!cfg.isSecurityEnabled()) return;
        String title = activated ? "🤖 AntiBot ACTIVATED" : "✅ AntiBot Deactivated";
        int    color = activated ? COLOR_SECURITY : COLOR_SUCCESS;
        send(cfg.getSecurityWebhook(),
                embed(title, color)
                        .description(activated
                            ? "High connection rate detected. New joins are being blocked."
                            : "Connection rate normalised. Joins are allowed again.")
                        .timestamp()
                        .footer("Auth - AntiBot", null)
                        .build());
    }

    /** Generic info log — plugin reload, backup, etc. */
    public void logInfo(String title, String description) {
        if (!cfg.isAdminEnabled()) return;
        send(cfg.getAdminWebhook(),
                embed(title, COLOR_INFO)
                        .description(description)
                        .timestamp()
                        .footer("Auth", null)
                        .build());
    }

    // ─── HTTP send ────────────────────────────────────────────────────────────

    private void send(String webhookUrl, JsonObject payload) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        executor.execute(() -> {
            RequestBody body = RequestBody.create(payload.toString(), JSON_TYPE);
            Request req = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Auth/1.0")
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() && resp.code() != 204) {
                    plugin.getLogger().warning("[Discord] Webhook failed: HTTP " + resp.code()
                            + " — " + webhookUrl.substring(0, Math.min(webhookUrl.length(), 60)) + "...");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Discord] Webhook send error: " + e.getMessage());
            }
        });
    }

    // ─── Embed builder ────────────────────────────────────────────────────────

    private EmbedBuilder embed(String title, int color) {
        return new EmbedBuilder(title, color, cfg.getServerName());
    }

    // ─── Inner builder ────────────────────────────────────────────────────────

    private static final class EmbedBuilder {
        private final JsonObject embed = new JsonObject();
        private final JsonArray  fields = new JsonArray();
        private final String     serverName;

        EmbedBuilder(String title, int color, String serverName) {
            this.serverName = serverName;
            embed.addProperty("title", title);
            embed.addProperty("color", color);
        }

        EmbedBuilder description(String desc) {
            embed.addProperty("description", desc);
            return this;
        }

        EmbedBuilder field(String name, String value, boolean inline) {
            JsonObject field = new JsonObject();
            field.addProperty("name",   name);
            field.addProperty("value",  value == null || value.isBlank() ? "—" : value);
            field.addProperty("inline", inline);
            fields.add(field);
            return this;
        }

        EmbedBuilder timestamp() {
            embed.addProperty("timestamp", Instant.now().toString());
            return this;
        }

        EmbedBuilder footer(String text, String iconUrl) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", serverName + " • " + text);
            if (iconUrl != null) footer.addProperty("icon_url", iconUrl);
            embed.add("footer", footer);
            return this;
        }

        JsonObject build() {
            embed.add("fields", fields);
            JsonObject payload = new JsonObject();
            JsonArray  embeds  = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
            // Suppress @everyone pings
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray());
            payload.add("allowed_mentions", allowedMentions);
            return payload;
        }
    }
}
