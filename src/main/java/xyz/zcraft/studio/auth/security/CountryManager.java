package xyz.zcraft.studio.auth.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Resolves country codes from IP addresses.
 * Providers: ip-api.com (free, no key) | MaxMind GeoLite2 (local DB, requires mmdb file)
 *
 * Results are cached to avoid repeated API calls for the same IP.
 * Enforces the whitelist/blacklist from config.
 */
public class CountryManager {

    private final ZCraftAuth  plugin;
    private final OkHttpClient http;

    // IP -> CacheEntry
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CountryManager(ZCraftAuth plugin) {
        this.plugin = plugin;
        this.http   = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Check whether this IP is allowed to join.
     * Returns true if allowed, false if blocked.
     */
    public CompletableFuture<Boolean> isAllowed(String ip) {
        if (!plugin.getConfigManager().isCountryEnabled())
            return CompletableFuture.completedFuture(true);

        // Localhost / private ranges are always allowed
        if (isPrivate(ip)) return CompletableFuture.completedFuture(true);

        return getCountryCode(ip).thenApply(code -> {
            if (code == null) return true;  // unknown = allow

            var codes = plugin.getConfigManager().getCountryCodes();
            String mode = plugin.getConfigManager().getCountryMode();

            return switch (mode.toLowerCase()) {
                case "whitelist" -> codes.contains(code.toUpperCase());
                case "blacklist" -> !codes.contains(code.toUpperCase());
                default          -> true;
            };
        });
    }

    /**
     * Resolve country code for an IP address.
     */
    public CompletableFuture<String> getCountryCode(String ip) {
        // Check cache
        CacheEntry entry = cache.get(ip);
        if (entry != null && !entry.expired())
            return CompletableFuture.completedFuture(entry.code);

        String provider = plugin.getConfigManager().getGeoIPProvider();
        return switch (provider.toLowerCase()) {
            case "maxmind" -> lookupMaxMind(ip);
            default        -> lookupIpApi(ip);
        };
    }

    // ─── ip-api.com provider ─────────────────────────────────────────────────

    private CompletableFuture<String> lookupIpApi(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            String url = "http://ip-api.com/json/" + ip + "?fields=countryCode,status";
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.body() == null) return null;
                JsonObject json = JsonParser.parseString(resp.body().string()).getAsJsonObject();
                if (!"success".equals(json.get("status").getAsString())) return null;
                String code = json.get("countryCode").getAsString();
                cacheCode(ip, code);
                return code;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "ip-api lookup failed: " + e.getMessage());
                return null;
            }
        });
    }

    // ─── MaxMind GeoLite2 provider ────────────────────────────────────────────

    private CompletableFuture<String> lookupMaxMind(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            File dbFile = new File(plugin.getDataFolder(),
                    plugin.getConfigManager().getMaxMindDbPath());
            if (!dbFile.exists()) {
                plugin.getLogger().warning("MaxMind DB not found: " + dbFile.getPath()
                        + " — falling back to ip-api");
                return null;
            }
            // MaxMind GeoIP2 Java API (optional dependency — use reflection to avoid hard dep)
            try {
                Class<?> dbReaderClass = Class.forName("com.maxmind.geoip2.DatabaseReader");
                Object reader = dbReaderClass.getDeclaredConstructor(File.class)
                        .newInstance(dbFile);
                InetAddress address = InetAddress.getByName(ip);
                Object response = dbReaderClass.getMethod("country", InetAddress.class)
                        .invoke(reader, address);
                Object country = response.getClass().getMethod("getCountry").invoke(response);
                String code = (String) country.getClass().getMethod("getIsoCode").invoke(country);
                cacheCode(ip, code);
                return code;
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("MaxMind GeoIP2 not on classpath — falling back to ip-api");
                return null;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "MaxMind lookup failed", e);
                return null;
            }
        });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void cacheCode(String ip, String code) {
        long exp = System.currentTimeMillis()
                + (plugin.getConfigManager().getGeoIPCacheDuration() * 1000L);
        cache.put(ip, new CacheEntry(code, exp));
    }

    private boolean isPrivate(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) { return false; }
    }

    public String getKickMessage() { return plugin.getConfigManager().getCountryKickMessage(); }

    private record CacheEntry(String code, long expiryMs) {
        boolean expired() { return System.currentTimeMillis() > expiryMs; }
    }
}
