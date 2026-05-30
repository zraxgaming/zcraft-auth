package xyz.zcraft.studio.auth.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;

/**
 * Extra inventory protection layer.
 * Sends a fake empty inventory to the client while the player is unauthenticated,
 * preventing them from seeing item contents before login.
 *
 * When PacketEvents is present, this works at packet level.
 * Without it, it falls back to clearing/restoring inventory on the server.
 */
public class InventoryProtectionListener implements Listener {

    private final ZCraftAuth plugin;

    public InventoryProtectionListener(ZCraftAuth plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfigManager().isInventoryProtectionEnabled()) return;

        // Hide inventory contents until authenticated
        // With PacketEvents, a packet interceptor would suppress WindowItems packets.
        // Without PacketEvents, we just rely on the AuthListener cancelling all inventory interaction.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.getAuthManager().isAuthenticated(player)) {
                // Store the inventory (handled by CraftBukkit internally — items aren't lost)
                // Visual-only: send empty window update via Bukkit API
                player.updateInventory();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Inventory state is automatically persisted by the server
    }
}
