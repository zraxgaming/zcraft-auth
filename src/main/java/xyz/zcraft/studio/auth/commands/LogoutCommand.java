package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.util.List;

public class LogoutCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LogoutCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }
        if (!plugin.getConfigManager().isLogoutCommandEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }
        if (!plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "logout.not-logged-in")));
            return true;
        }
        plugin.getAuthManager().logout(player);
        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "logout.success")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return List.of(); }
}
