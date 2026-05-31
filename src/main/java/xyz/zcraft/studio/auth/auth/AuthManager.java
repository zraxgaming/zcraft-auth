package xyz.zcraft.studio.auth.auth;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.api.PlayerAuthEvent;
import xyz.zcraft.studio.auth.api.PlayerLogoutEvent;
import xyz.zcraft.studio.auth.api.ZCraftAuthAPI;
import xyz.zcraft.studio.auth.database.PlayerData;
import xyz.zcraft.studio.auth.i18n.LanguageManager;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central authentication state machine for Paper 1.21.x.
 */
public class AuthManager {

    private final ZCraftAuth plugin;
    private final PasswordEngine passwordEngine;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending2FA = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> attemptBans = new ConcurrentHashMap<>();
    private final Map<UUID, Location> savedLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttempt = new ConcurrentHashMap<>();

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AuthManager(ZCraftAuth plugin) {
        this.plugin = plugin;
        this.passwordEngine = new PasswordEngine(plugin);
        ZCraftAuthAPI.init(plugin);
    }

    public void evaluateOnJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = getIP(player);

        plugin.getDatabase().findByUUID(uuid).thenAccept(opt ->
                plugin.runSync(() -> handleJoin(player, ip, opt)));
    }

    private void handleJoin(Player player, String ip, Optional<PlayerData> opt) {
        if (opt.isEmpty()) {
            placeLimbo(player);
            showRegisterPrompt(player);
            startTimeout(player, false);
            return;
        }

        PlayerData data = opt.get();

        if (plugin.getConfigManager().isRestrictionEnabled() && data.restricted()) {
            String lockIp = data.restrictedIp();
            if (lockIp != null && !lockIp.equals(ip)) {
                player.kick(MM.deserialize(
                        plugin.getLanguageManager().get(player, "restriction.wrong-ip")));
                return;
            }
        }

        if (plugin.getConfigManager().isSessionEnabled() && plugin.getSessionManager().isSessionValid(data, ip)) {
            completeLogin(player, data, PlayerAuthEvent.AuthMethod.SESSION);
            return;
        }

        if (plugin.getConfigManager().isPremiumEnabled() && data.premium()) {
            plugin.getPremiumChecker().checkPremium(player.getName()).thenAccept(result ->
                    plugin.runSync(() -> {
                        if (result.premium()) {
                            completeLogin(player, data, PlayerAuthEvent.AuthMethod.PREMIUM);
                        } else {
                            placeLimbo(player);
                            showLoginPrompt(player);
                            startTimeout(player, true);
                        }
                    })
            );
            return;
        }

        placeLimbo(player);
        showLoginPrompt(player);
        startTimeout(player, true);
    }

    public CompletableFuture<LoginResult> attemptLoginAsync(Player player, String password) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = getIP(player);
        var cfg = plugin.getConfigManager();

        Long banExpiry = attemptBans.get(uuid);
        if (banExpiry != null && banExpiry > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(LoginResult.ATTEMPT_BANNED);
        }

        Long last = lastAttempt.get(uuid);
        if (last != null && System.currentTimeMillis() - last < 500L) {
            return CompletableFuture.completedFuture(LoginResult.COOLDOWN);
        }
        lastAttempt.put(uuid, System.currentTimeMillis());

        return plugin.getDatabase().findByUUID(uuid)
                .thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        return CompletableFuture.completedFuture(LoginResult.NOT_REGISTERED);
                    }

                    PlayerData data = opt.get();
                    PasswordEngine.VerifyResult verify = passwordEngine.verify(password, data.passwordHash());

                    if (!verify.matched()) {
                        int count = attempts.merge(uuid, 1, Integer::sum);
                        int max = cfg.getMaxLoginAttempts();

                        if (count >= 3) {
                            plugin.getDiscordLogger().logFailedLogin(name, ip, count, max);
                        }

                        if (count >= max) {
                            int banMins = cfg.getAttemptBanDuration();
                            if (banMins > 0) {
                                attemptBans.put(uuid, System.currentTimeMillis() + (banMins * 60_000L));
                            }
                            attempts.remove(uuid);
                            plugin.getDiscordLogger().logSecurityAlert(
                                    name, ip, "Max login attempts reached - temp banned " + banMins + "min");
                            plugin.runSync(() -> player.kick(MM.deserialize(
                                    plugin.getLanguageManager().get(player, "login.max-attempts",
                                            Map.of("time", String.valueOf(banMins))))));
                            return CompletableFuture.completedFuture(LoginResult.MAX_ATTEMPTS);
                        }

                        return CompletableFuture.completedFuture(LoginResult.WRONG_PASSWORD);
                    }

                    attempts.remove(uuid);

                    if (data.has2FA() && cfg.is2FAEnabled()) {
                        pending2FA.add(uuid);
                        return CompletableFuture.completedFuture(LoginResult.NEEDS_2FA);
                    }

                    PlayerData loginData = data;
                    if (verify.needsRehash() && cfg.isAutoRehash()) {
                        String newHash = passwordEngine.hash(password);
                        loginData = data.toBuilder().passwordHash(newHash).build();
                        plugin.getDatabase().updatePlayer(loginData);
                    }

                    PlayerData finalLoginData = loginData;
                    plugin.runSync(() -> completeLogin(player, finalLoginData, PlayerAuthEvent.AuthMethod.PASSWORD));
                    return CompletableFuture.completedFuture(LoginResult.SUCCESS);
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Login error: " + ex.getMessage());
                    return LoginResult.ERROR;
                });
    }

    public CompletableFuture<Boolean> attempt2FAAsync(Player player, String code) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = getIP(player);

        if (!pending2FA.contains(uuid)) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getDatabase().findByUUID(uuid)
                .thenApply(opt -> {
                    if (opt.isEmpty()) {
                        return false;
                    }

                    PlayerData data = opt.get();
                    boolean ok = plugin.getTwoFactorManager().verifyCode(data.totpSecret(), code);
                    if (ok) {
                        plugin.runSync(() -> {
                            pending2FA.remove(uuid);
                            completeLogin(player, data, PlayerAuthEvent.AuthMethod.PASSWORD);
                        });
                    } else {
                        plugin.getDiscordLogger().logSecurityAlert(name, ip, "Invalid 2FA code entered");
                    }
                    return ok;
                })
                .exceptionally(ex -> false);
    }

    public CompletableFuture<RegisterResult> registerAsync(Player player, String password, String confirm) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String ip = getIP(player);
        var cfg = plugin.getConfigManager();

        if (!password.equals(confirm)) {
            return CompletableFuture.completedFuture(RegisterResult.MISMATCH);
        }
        if (password.length() < cfg.getPasswordMinLength()) {
            return CompletableFuture.completedFuture(RegisterResult.TOO_SHORT);
        }
        if (password.length() > cfg.getPasswordMaxLength()) {
            return CompletableFuture.completedFuture(RegisterResult.TOO_LONG);
        }
        if (cfg.getUnsafePasswords().contains(password.toLowerCase())) {
            return CompletableFuture.completedFuture(RegisterResult.UNSAFE);
        }
        if (!name.matches(cfg.getUsernameRegex())) {
            return CompletableFuture.completedFuture(RegisterResult.INVALID_NAME);
        }

        return plugin.getDatabase().isRegistered(uuid)
                .thenCompose(registered -> {
                    if (registered) {
                        return CompletableFuture.completedFuture(RegisterResult.ALREADY_REGISTERED);
                    }

                    return CompletableFuture.supplyAsync(() -> passwordEngine.hash(password))
                            .thenCompose(hash -> {
                                PlayerData data = PlayerData.builder(uuid, name)
                                        .passwordHash(hash)
                                        .lastIp(ip)
                                        .lastLogin(Instant.now())
                                        .build();

                                return plugin.getDatabase().savePlayer(data)
                                        .thenApply(v -> {
                                            plugin.getDiscordLogger().logRegister(name, ip);
                                            plugin.runSync(() -> completeLogin(player, data, PlayerAuthEvent.AuthMethod.PASSWORD));
                                            return RegisterResult.SUCCESS;
                                        });
                            });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Register error: " + ex.getMessage());
                    return RegisterResult.ERROR;
                });
    }

    public void completeLogin(Player player, PlayerData data, PlayerAuthEvent.AuthMethod method) {
        UUID uuid = player.getUniqueId();
        authenticated.add(uuid);
        pending2FA.remove(uuid);
        cancelTimeout(uuid);
        removeBossBar(uuid);

        String ip = getIP(player);
        PlayerData updated = data.toBuilder()
                .lastIp(ip)
                .lastLogin(Instant.now())
                .build();

        if (plugin.getConfigManager().isSessionEnabled()) {
            plugin.getSessionManager().createSession(updated).thenAccept(withSession ->
                    plugin.getDatabase().updatePlayer(withSession));
        } else {
            plugin.getDatabase().updatePlayer(updated);
        }

        Location saved = savedLocations.remove(uuid);
        if (plugin.getConfigManager().isTeleportToLastLocation() && saved != null) {
            player.teleport(saved);
        }

        LanguageManager lang = plugin.getLanguageManager();
        Component msg = switch (method) {
            case SESSION -> MM.deserialize(lang.get(player, "session.restored",
                    Map.of("player", player.getName())));
            case PREMIUM -> MM.deserialize(lang.get(player, "premium.auto-login"));
            case FORCE -> MM.deserialize(lang.get(player, "forcelogin.player-notified"));
            default -> MM.deserialize(lang.get(player, "login.success",
                    Map.of("player", player.getName())));
        };
        player.sendMessage(msg);

        plugin.getServer().getOnlinePlayers().forEach(p -> {
            p.showPlayer(plugin, player);
            player.showPlayer(plugin, p);
        });
        player.updateInventory();

        plugin.getServer().getPluginManager().callEvent(new PlayerAuthEvent(player, updated, method));
        plugin.getDiscordLogger().logLogin(player.getName(), ip, method.name());
    }

    public void logout(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        plugin.getSessionManager().invalidateSession(uuid);
        savedLocations.remove(uuid);
        cancelTimeout(uuid);
        removeBossBar(uuid);
        plugin.getServer().getPluginManager().callEvent(
                new PlayerLogoutEvent(player, PlayerLogoutEvent.LogoutReason.COMMAND));
        plugin.getDiscordLogger().logLogout(player.getName(), getIP(player), "command");
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        boolean wasAuthed = authenticated.remove(uuid);
        pending2FA.remove(uuid);
        attempts.remove(uuid);
        lastAttempt.remove(uuid);
        cancelTimeout(uuid);
        removeBossBar(uuid);
        savedLocations.remove(uuid);
        if (wasAuthed) {
            plugin.getServer().getPluginManager().callEvent(
                    new PlayerLogoutEvent(player, PlayerLogoutEvent.LogoutReason.DISCONNECT));
        }
    }

    public void onShutdown() {
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            BossBar bar = bossBars.remove(p.getUniqueId());
            if (bar != null) {
                p.hideBossBar(bar);
            }
        });
    }

    public void reload() {
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public boolean isPending2FA(UUID uuid) {
        return pending2FA.contains(uuid);
    }

    public void placeLimbo(Player player) {
        savedLocations.put(player.getUniqueId(), player.getLocation());
        if (plugin.getConfigManager().isLimboEnabled()) {
            var cfg = plugin.getConfigManager();
            var world = plugin.getServer().getWorld(cfg.getLimboWorld());
            if (world != null) {
                Location limbo = new Location(world, cfg.getLimboX(), cfg.getLimboY(),
                        cfg.getLimboZ(), cfg.getLimboYaw(), cfg.getLimboPitch());
                player.teleport(limbo);
            }
        }
        if (plugin.getConfigManager().isInvisibleBeforeAuth()) {
            plugin.getServer().getOnlinePlayers().forEach(p -> {
                if (!p.equals(player)) {
                    p.hidePlayer(plugin, player);
                }
            });
        }
    }

    private void showLoginPrompt(Player player) {
        var lang = plugin.getLanguageManager();
        if (plugin.getConfigManager().isTitlePromptEnabled()) {
            player.showTitle(Title.title(
                    MM.deserialize(lang.get(player, "titles.login-title")),
                    MM.deserialize(lang.get(player, "titles.login-subtitle"))
            ));
        }
        player.sendMessage(MM.deserialize(lang.get(player, "login.prompt")));
        showBossBar(player, lang.get(player, "bossbar.login"));
    }

    private void showRegisterPrompt(Player player) {
        var lang = plugin.getLanguageManager();
        if (plugin.getConfigManager().isTitlePromptEnabled()) {
            player.showTitle(Title.title(
                    MM.deserialize(lang.get(player, "titles.register-title")),
                    MM.deserialize(lang.get(player, "titles.register-subtitle"))
            ));
        }
        player.sendMessage(MM.deserialize(lang.get(player, "register.prompt")));
        showBossBar(player, lang.get(player, "bossbar.register"));
    }

    private void showBossBar(Player player, String message) {
        if (!plugin.getConfigManager().isBossBarEnabled()) {
            return;
        }

        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(plugin.getConfigManager().getBossBarColor().toUpperCase());
        } catch (Exception e) {
            color = BossBar.Color.BLUE;
        }
        BossBar bar = BossBar.bossBar(MM.deserialize(message), 1.0f, color, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        bossBars.put(player.getUniqueId(), bar);
    }

    private void removeBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar == null) {
            return;
        }
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) {
            p.hideBossBar(bar);
        }
    }

    private void startTimeout(Player player, boolean isLogin) {
        int seconds = isLogin ? plugin.getConfigManager().getLoginTimeout()
                : plugin.getConfigManager().getRegisterTimeout();
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kick(MM.deserialize(
                            plugin.getLanguageManager().get(player, "login.timeout")));
                }
            }
        }.runTaskLater(plugin, seconds * 20L).getTaskId();
        timeoutTasks.put(player.getUniqueId(), taskId);
    }

    private void cancelTimeout(UUID uuid) {
        Integer taskId = timeoutTasks.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    public String getIP(Player player) {
        var addr = player.getAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "127.0.0.1";
    }

    public enum LoginResult {
        SUCCESS, WRONG_PASSWORD, NOT_REGISTERED, NEEDS_2FA,
        ATTEMPT_BANNED, MAX_ATTEMPTS, COOLDOWN, ERROR
    }

    public enum RegisterResult {
        SUCCESS, MISMATCH, TOO_SHORT, TOO_LONG, UNSAFE, INVALID_NAME, ALREADY_REGISTERED, ERROR
    }
}
