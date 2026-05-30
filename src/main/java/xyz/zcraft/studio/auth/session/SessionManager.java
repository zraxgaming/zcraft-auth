package xyz.zcraft.studio.auth.session;

import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages IP-based session tokens so players are not prompted to log in
 * again within the session window.
 *
 * Token is stored in the database and validated on next join.
 * Prevents "Logged in from another location" issues by seamlessly updating.
 */
public class SessionManager {

    private final ZCraftAuth plugin;
    private final SecureRandom rng = new SecureRandom();

    public SessionManager(ZCraftAuth plugin) { this.plugin = plugin; }

    /**
     * Generate and attach a new session token to the PlayerData.
     * Returns updated PlayerData — caller must persist it.
     */
    public CompletableFuture<PlayerData> createSession(PlayerData data) {
        if (!plugin.getConfigManager().isSessionEnabled())
            return CompletableFuture.completedFuture(data);

        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        String token  = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long   expiry = plugin.getConfigManager().getSessionDuration();
        Instant exp   = expiry > 0 ? Instant.now().plusSeconds(expiry) : Instant.MAX;

        return CompletableFuture.completedFuture(
                data.toBuilder()
                    .sessionToken(token)
                    .sessionExpiry(exp)
                    .build()
        );
    }

    /**
     * Check whether the stored session is still valid for the given IP.
     */
    public boolean isSessionValid(PlayerData data, String currentIp) {
        if (!plugin.getConfigManager().isSessionEnabled()) return false;
        String token = data.sessionToken();
        if (token == null || token.isBlank()) return false;

        // Expiry check
        Instant expiry = data.sessionExpiry();
        if (expiry != null && Instant.now().isAfter(expiry)) return false;

        // IP check
        String storedIp = data.lastIp();
        if (storedIp == null) return false;

        int tolerance = plugin.getConfigManager().getSessionIpTolerance();
        if (tolerance == 0) return storedIp.equals(currentIp);

        // Allow same /24 subnet
        return sameSubnet(storedIp, currentIp, tolerance);
    }

    /** Invalidate (clear) a player's session token in the database. */
    public void invalidateSession(UUID uuid) {
        plugin.getDatabase().findByUUID(uuid).thenAccept(opt -> {
            if (opt.isEmpty()) return;
            PlayerData updated = opt.get().toBuilder()
                    .sessionToken(null)
                    .sessionExpiry(null)
                    .build();
            plugin.getDatabase().updatePlayer(updated);
        });
    }

    /** Purge all expired sessions from the database (called on reload). */
    public void purgeExpired() {
        // Implemented via SQL would be more efficient;
        // for SQLite this is a background no-op since sessions are validated on join
        plugin.getLogger().info("Session purge skipped (validated lazily on join).");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean sameSubnet(String ip1, String ip2, int tolerance) {
        try {
            String[] p1 = ip1.split("\\.");
            String[] p2 = ip2.split("\\.");
            if (p1.length != 4 || p2.length != 4) return ip1.equals(ip2);
            int match = 4 - tolerance;
            for (int i = 0; i < match; i++) {
                if (!p1[i].equals(p2[i])) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }
}
