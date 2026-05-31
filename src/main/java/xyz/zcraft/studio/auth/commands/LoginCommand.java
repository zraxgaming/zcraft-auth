package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.auth.AuthManager;

import java.util.List;
import java.util.Map;

public class LoginCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoginCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }
        if (!plugin.getConfigManager().isLoginCommandEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }
        if (!player.hasPermission("zcraftauth.login")) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "error.no-permission")));
            return true;
        }

        // Check if awaiting 2FA
        if (plugin.getAuthManager().isPending2FA(player.getUniqueId())) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "two-factor.prompt")));
            return true;
        }

        if (plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "already-authenticated")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <password>"))));
            return true;
        }

        String password = args[0];
        plugin.getAuthManager().attemptLoginAsync(player, password).thenAccept(result ->
                plugin.runSync(() -> {
                    switch (result) {
                        case SUCCESS -> {
                        }
                        case WRONG_PASSWORD -> {
                            int max = plugin.getConfigManager().getMaxLoginAttempts();
                            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                    .get(player, "login.wrong-password", Map.of("attempt", "?", "max", String.valueOf(max)))));
                        }
                        case NOT_REGISTERED -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "not-registered")));
                        case NEEDS_2FA -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "two-factor.prompt")));
                        case ATTEMPT_BANNED -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "login.max-attempts", Map.of("time", "?"))));
                        case COOLDOWN -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "login.cooldown", Map.of("time", "0.5"))));
                        case ERROR -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "error.database")));
                        default -> {
                        }
                    }
                })
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return List.of();  // Never suggest passwords
    }
}
