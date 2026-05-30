package xyz.zcraft.studio.auth.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Checks whether a given username/UUID belongs to a premium (Mojang-authenticated) account.
 * Results are cached to avoid hammering the Mojang API.
 */
public class PremiumChecker {

    private static final String MOJANG_API =
            "https://api.mojang.com/users/profiles/minecraft/";

    private final ZCraftAuth plugin;
    private final OkHttpClient http;

    // Cache: lowercase username -> CacheEntry
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PremiumChecker(ZCraftAuth plugin) {
        this.plugin = plugin;
        int timeout = plugin.getConfigManager().getPremiumCheckTimeout();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Returns true if the username is a valid Mojang-registered premium account.
     * Always resolves on a virtual thread — never blocks the main thread.
     */
    public CompletableFuture<PremiumResult> checkPremium(String username) {
        if (!plugin.getConfigManager().isPremiumEnabled())
            return CompletableFuture.completedFuture(new PremiumResult(false, null));

        String key = username.toLowerCase();

        // Serve from cache if still fresh
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.expired())
            return CompletableFuture.completedFuture(cached.result);

        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder()
                    .url(MOJANG_API + username)
                    .get()
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.code() == 200 && response.body() != null) {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    String rawId = json.get("id").getAsString();
                    // Mojang returns UUID without dashes
                    UUID uuid = UUID.fromString(
                            rawId.replaceFirst(
                                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                                    "$1-$2-$3-$4-$5"
                            )
                    );
                    PremiumResult result = new PremiumResult(true, uuid);
                    cache.put(key, new CacheEntry(result,
                            System.currentTimeMillis() + (plugin.getConfigManager().getPremiumCacheDuration() * 1000L)));
                    return result;
                } else if (response.code() == 204 || response.code() == 404) {
                    // Cracked account — cache negative result for shorter time
                    PremiumResult result = new PremiumResult(false, null);
                    cache.put(key, new CacheEntry(result, System.currentTimeMillis() + 60_000));
                    return result;
                } else {
                    // API error — fall back to config behavior
                    return fallback();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Mojang API unreachable: " + e.getMessage());
                return fallback();
            }
        });
    }

    private PremiumResult fallback() {
        return switch (plugin.getConfigManager().getPremiumApiBehavior().toLowerCase()) {
            case "premium" -> new PremiumResult(true, null);
            case "kick"    -> new PremiumResult(false, null);  // caller handles kick
            default        -> new PremiumResult(false, null);  // cracked
        };
    }

    /** Evict a cached entry (e.g. after player is force-set premium/cracked). */
    public void invalidate(String username) { cache.remove(username.toLowerCase()); }

    public record PremiumResult(boolean premium, UUID mojangUUID) {}

    private record CacheEntry(PremiumResult result, long expiryMs) {
        boolean expired() { return System.currentTimeMillis() > expiryMs; }
    }
}
