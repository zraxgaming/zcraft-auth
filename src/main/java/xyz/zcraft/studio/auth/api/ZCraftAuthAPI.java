package xyz.zcraft.studio.auth.api;

import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for the auth plugin
 *
 * Other plugins can use this to check authentication state,
 * retrieve player data, and hook into auth events.
 *
 * Usage:
 * <pre>
 *   ZCraftAuthAPI api = ZCraftAuthAPI.get();
 *   boolean authed = api.isAuthenticated(player);
 * </pre>
 */
public final class ZCraftAuthAPI {

    private static ZCraftAuthAPI instance;
    private final ZCraftAuth plugin;

    private ZCraftAuthAPI(ZCraftAuth plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialise and register the API instance. Called by the main plugin on enable.
     */
    public static void init(ZCraftAuth plugin) {
        instance = new ZCraftAuthAPI(plugin);
    }

    /**
     * Get the API instance.
     * @throws IllegalStateException if the auth plugin is not loaded
     */
    public static ZCraftAuthAPI get() {
        if (instance == null)
            throw new IllegalStateException("Auth plugin is not loaded.");
        return instance;
    }

    // ─── State queries ────────────────────────────────────────────────────────

    /** Returns true if the given player is currently authenticated. */
    public boolean isAuthenticated(Player player) {
        return plugin.getAuthManager().isAuthenticated(player);
    }

    /** Returns true if the given UUID is currently authenticated. */
    public boolean isAuthenticated(UUID uuid) {
        return plugin.getAuthManager().isAuthenticated(uuid);
    }

    /** Returns true if the player is awaiting a 2FA code. */
    public boolean isPending2FA(UUID uuid) {
        return plugin.getAuthManager().isPending2FA(uuid);
    }

    // ─── Database queries ─────────────────────────────────────────────────────

    /**
     * Look up a player's stored data by UUID.
     * Returns an empty Optional if not registered.
     */
    public CompletableFuture<Optional<PlayerData>> getPlayerData(UUID uuid) {
        return plugin.getDatabase().findByUUID(uuid);
    }

    /**
     * Look up a player's stored data by username (exact, case-sensitive).
     */
    public CompletableFuture<Optional<PlayerData>> getPlayerData(String username) {
        return plugin.getDatabase().findByUsername(username);
    }

    /**
     * Check whether a UUID is registered.
     */
    public CompletableFuture<Boolean> isRegistered(UUID uuid) {
        return plugin.getDatabase().isRegistered(uuid);
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    /**
     * Force-authenticate a player programmatically (e.g. Floodgate integration).
     * The player must be online. Fires the same post-auth logic as a normal login.
     */
    public void forceAuthenticate(Player player) {
        plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
            if (opt.isPresent()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.getAuthManager().completeLogin(player, opt.get(), false));
            }
        });
    }

    /**
     * Force-deauthenticate a player (e.g. kick from session).
     */
    public void forceLogout(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getAuthManager().logout(player));
    }

    // ─── Plugin info ──────────────────────────────────────────────────────────

    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    public String getDatabaseType() {
        return plugin.getDatabase().getProviderType();
    }
}
