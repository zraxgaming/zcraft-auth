package xyz.zcraft.studio.auth.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.util.List;

/**
 * Core authentication gate — fully 1.21.x compatible.
 * Uses Paper's AsyncChatEvent and Adventure kick() API throughout.
 */
public class AuthListener implements Listener {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public AuthListener(ZCraftAuth plugin) { this.plugin = plugin; }

    // ─── Async pre-login (country block) ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        try {
            boolean allowed = plugin.getCountryManager().isAllowed(ip).get();
            if (!allowed) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        mm.deserialize(plugin.getCountryManager().getKickMessage()));
                plugin.getDiscordLogger().logSecurityAlert(
                    event.getName(), ip, "Blocked by country restriction");
            }
        } catch (Exception ignored) {}
    }

    // ─── PlayerLoginEvent — AntiBot + Spoofing ────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String ip     = event.getAddress().getHostAddress();
        if (isNPC(player)) return;

        // AntiBot
        if (plugin.getAntiBotManager().checkJoin(event)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    mm.deserialize(plugin.getAntiBotManager().getKickMessage()));
            return;
        }

        // Spoofing protection (async result applied after join via onJoin)
        plugin.getSpoofingProtection().validate(player.getName()).thenAccept(result -> {
            switch (result) {
                case INVALID_FORMAT -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(mm.deserialize(
                            plugin.getLanguageManager().get(player, "register.invalid-username")));
                        plugin.getDiscordLogger().logSecurityAlert(
                            player.getName(), ip, "Username failed regex check");
                    }
                });
                case COLLISION -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.kick(mm.deserialize(
                            plugin.getLanguageManager().get(player, "spoofing.blocked")));
                        plugin.getDiscordLogger().logSecurityAlert(
                            player.getName(), ip, "Username spoofing attempt detected");
                    }
                });
                default -> {}
            }
        });
    }

    // ─── Join ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player)) return;

        if (plugin.getConfigManager().isHideInTablist()) {
            plugin.getServer().getOnlinePlayers().forEach(other -> {
                if (!other.equals(player)) other.hidePlayer(plugin, player);
            });
        }
        plugin.getAuthManager().evaluateOnJoin(player);
    }

    // ─── Quit ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAuthManager().onQuit(player);
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "127.0.0.1";
        plugin.getAntiBotManager().onQuit(ip);
    }

    // ─── Movement ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        var from = event.getFrom();
        var to   = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()))
            event.setCancelled(true);
    }

    // ─── Commands ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        if (player.hasPermission("zcraftauth.bypass.antibot")) return;

        String raw     = event.getMessage().toLowerCase().split(" ")[0];
        List<String> allowed = plugin.getConfigManager().getAllowedCommands();
        boolean ok = allowed.stream().anyMatch(cmd ->
                raw.equals(cmd.toLowerCase()) || raw.equals("/" + cmd.toLowerCase().replace("/", "")));

        if (!ok) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
        }
    }

    // ─── Chat (Paper 1.21.x uses AsyncChatEvent) ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) {
            return;
        }
        if (!plugin.getConfigManager().isAllowChatBeforeAuth()) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
        }
    }

    // ─── Inventory ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        if (!plugin.getConfigManager().isInventoryProtectionEnabled()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        if (!plugin.getConfigManager().isInventoryProtectionEnabled()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        if (!plugin.getConfigManager().isInventoryProtectionEnabled()) return;
        event.setCancelled(true);
    }

    // ─── Damage ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && !plugin.getAuthManager().isAuthenticated(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player victim && !plugin.getAuthManager().isAuthenticated(victim)) {
            event.setCancelled(true);
        }
    }

    // ─── Ender pearls ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearlThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        if (!plugin.getConfigManager().isReturnEnderPearls()) return;
        if (event.getEntityType() == org.bukkit.entity.EntityType.ENDER_PEARL) {
            event.setCancelled(true);
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_PEARL));
        }
    }

    // ─── Interact ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isNPC(player) || plugin.getAuthManager().isAuthenticated(player)) return;
        event.setCancelled(true);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private boolean isNPC(Player player) {
        return plugin.getConfigManager().isCitizensSupport() && player.hasMetadata("NPC");
    }
}
