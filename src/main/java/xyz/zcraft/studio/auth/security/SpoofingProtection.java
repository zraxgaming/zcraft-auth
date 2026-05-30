package xyz.zcraft.studio.auth.security;

import xyz.zcraft.studio.auth.ZCraftAuth;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Username spoofing protection.
 *
 * Checks:
 * 1. Regex format validation (configured pattern)
 * 2. Case-insensitive collision with existing accounts
 * 3. Confusable character substitution (l/1, 0/O, etc.)
 */
public class SpoofingProtection {

    private final ZCraftAuth plugin;

    // Common confusable substitutions (source -> normalized)
    private static final Map<Character, Character> CONFUSABLES = Map.ofEntries(
            Map.entry('0', 'o'), Map.entry('1', 'l'), Map.entry('3', 'e'),
            Map.entry('4', 'a'), Map.entry('5', 's'), Map.entry('6', 'g'),
            Map.entry('7', 't'), Map.entry('8', 'b'), Map.entry('@', 'a'),
            Map.entry('!', 'i'), Map.entry('|', 'i'), Map.entry('$', 's'),
            Map.entry('²', '2'), Map.entry('³', '3')
    );

    private Pattern usernamePattern;

    public SpoofingProtection(ZCraftAuth plugin) {
        this.plugin  = plugin;
        this.usernamePattern = Pattern.compile(plugin.getConfigManager().getUsernameRegex());
    }

    /**
     * Validate a joining username.
     * @return SpoofResult.ALLOWED, INVALID_FORMAT, or COLLISION
     */
    public CompletableFuture<SpoofResult> validate(String username) {
        if (!plugin.getConfigManager().isSpoofingProtectionEnabled())
            return CompletableFuture.completedFuture(SpoofResult.ALLOWED);

        // 1. Regex check
        if (!usernamePattern.matcher(username).matches())
            return CompletableFuture.completedFuture(SpoofResult.INVALID_FORMAT);

        // 2. Case-insensitive DB lookup
        if (plugin.getConfigManager().isCaseInsensitiveCheck()) {
            return plugin.getDatabase().findByUsernameCaseInsensitive(username)
                    .thenCompose(opt -> {
                        if (opt.isPresent() && !opt.get().username().equals(username))
                            return CompletableFuture.completedFuture(SpoofResult.COLLISION);

                        // 3. Confusable check
                        if (plugin.getConfigManager().isConfusableCheck()) {
                            return checkConfusable(username);
                        }
                        return CompletableFuture.completedFuture(SpoofResult.ALLOWED);
                    });
        }

        // 3. Confusable check (standalone)
        if (plugin.getConfigManager().isConfusableCheck()) return checkConfusable(username);

        return CompletableFuture.completedFuture(SpoofResult.ALLOWED);
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private CompletableFuture<SpoofResult> checkConfusable(String username) {
        String normalized = normalize(username);
        // Look for any existing account whose normalized form matches
        return plugin.getDatabase().findByUsernameCaseInsensitive(username)
                .thenApply(opt -> {
                    if (opt.isPresent()) {
                        String existingNorm = normalize(opt.get().username());
                        if (existingNorm.equals(normalized) && !opt.get().username().equals(username))
                            return SpoofResult.COLLISION;
                    }
                    return SpoofResult.ALLOWED;
                });
    }

    /**
     * Normalize a username by lowercasing and substituting confusable characters.
     */
    public String normalize(String username) {
        StringBuilder sb = new StringBuilder();
        for (char c : username.toLowerCase().toCharArray())
            sb.append(CONFUSABLES.getOrDefault(c, c));
        return sb.toString();
    }

    public enum SpoofResult { ALLOWED, INVALID_FORMAT, COLLISION }
}
