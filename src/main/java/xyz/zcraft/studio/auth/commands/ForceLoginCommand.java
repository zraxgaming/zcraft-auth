package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.api.PlayerAuthEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ForceLoginCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ForceLoginCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean isConsole = !(sender instanceof Player);
        if (!plugin.getConfigManager().isForceLoginEnabled()) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("feature.disabled")));
            return true;
        }
        if (!isConsole && !sender.hasPermission("zcraftauth.forcelogin")) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("admin.no-permission")));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .getDefault("error.usage", Map.of("usage", "/" + label + " <player>"))));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("forcelogin.not-found")));
            return true;
        }

        plugin.getDatabase().findByUUID(target.getUniqueId()).thenAccept(opt ->
                plugin.runSync(() -> {
                    if (opt.isEmpty()) {
                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .getDefault("forcelogin.not-registered", Map.of("player", args[0]))));
                        return;
                    }

                    plugin.getAuthManager().completeLogin(target, opt.get(), PlayerAuthEvent.AuthMethod.FORCE);
                    sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .getDefault("forcelogin.success", Map.of("player", target.getName()))));
                    plugin.getLogger().info("[ForceLogin] " + sender.getName() + " -> " + target.getName());
                    plugin.getDiscordLogger().logAdminAction(sender.getName(), "FORCE LOGIN",
                            target.getName(), "Force-logged in by " + sender.getName());
                })
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isForceLoginEnabled()) return List.of();
        if (args.length == 1)
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        return List.of();
    }
}
