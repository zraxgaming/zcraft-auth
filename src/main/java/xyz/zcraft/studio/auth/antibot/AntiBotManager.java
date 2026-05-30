package xyz.zcraft.studio.auth.antibot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.player.PlayerLoginEvent;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Built-in AntiBot system — 1.21.x compatible.
 * Monitors join rate per second and per-IP connection counts.
 * Auto-activates / deactivates and fires Discord alerts.
 */
public class AntiBotManager {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final AtomicInteger joinsThisSecond = new AtomicInteger(0);
    private volatile long currentSecond = System.currentTimeMillis() / 1000;

    private final Map<String, AtomicInteger> ipCounts = new ConcurrentHashMap<>();

    private volatile boolean active      = false;
    private volatile long    activatedAt = 0;

    public AntiBotManager(ZCraftAuth plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::tickSecond, 20L, 20L);
    }

    /**
     * Check whether an incoming connection should be blocked.
     * @return true = block the player
     */
    public boolean checkJoin(PlayerLoginEvent event) {
        if (!plugin.getConfigManager().isAntiBotEnabled()) return false;

        String ip = event.getAddress().getHostAddress();
        if (plugin.getConfigManager().getAntiBotWhitelist().contains(ip)) return false;
        if (event.getPlayer().hasPermission("zcraftauth.bypass.antibot")) return false;

        // Check if active and still within window
        if (active) {
            long activeSecs = plugin.getConfigManager().getAntiBotActiveDuration();
            if ((System.currentTimeMillis() - activatedAt) > activeSecs * 1000L) {
                deactivate();
            } else {
                return true;
            }
        }

        // Count join
        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec != currentSecond) { joinsThisSecond.set(0); currentSecond = nowSec; }
        int joins = joinsThisSecond.incrementAndGet();

        if (joins > plugin.getConfigManager().getMaxJoinsPerSecond()) {
            activate();
            return true;
        }

        // Per-IP check
        AtomicInteger ipCount = ipCounts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (ipCount.get() >= plugin.getConfigManager().getMaxPerIP()) {
            plugin.getDiscordLogger().logSecurityAlert(
                event.getPlayer().getName(), ip,
                "Per-IP connection limit exceeded (" + ipCount.get() + " connections)");
            return true;
        }
        ipCount.incrementAndGet();

        return false;
    }

    public void onQuit(String ip) {
        AtomicInteger count = ipCounts.get(ip);
        if (count != null && count.decrementAndGet() <= 0) ipCounts.remove(ip);
    }

    public boolean isActive() { return active; }

    public String getKickMessage() {
        return plugin.getConfigManager().getAntiBotKickMessage();
    }

    private void activate() {
        if (active) return;
        active      = true;
        activatedAt = System.currentTimeMillis();
        plugin.getLogger().warning("[AntiBot] ACTIVATED - high join rate detected!");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().broadcast(mm.deserialize(
                plugin.getLanguageManager().getDefault("antibot.activated")));
        });
        plugin.getDiscordLogger().logAntibot(true);
    }

    private void deactivate() {
        active = false;
        plugin.getLogger().info("[AntiBot] Deactivated.");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().broadcast(mm.deserialize(
                plugin.getLanguageManager().getDefault("antibot.deactivated")));
        });
        plugin.getDiscordLogger().logAntibot(false);
    }

    private void tickSecond() {
        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec != currentSecond) { joinsThisSecond.set(0); currentSecond = nowSec; }
    }
}
