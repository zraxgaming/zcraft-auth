package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.auth.AuthManager;

import java.util.List;
import java.util.Map;

public class RegisterCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public RegisterCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }
        if (!plugin.getConfigManager().isRegisterCommandEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }

        if (plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "already-authenticated")));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <password> <confirmPassword>"))));
            return true;
        }

        plugin.getAuthManager().registerAsync(player, args[0], args[1]).thenAccept(result ->
                plugin.runSync(() -> {
                    switch (result) {
                        case SUCCESS -> {
                        }
                        case MISMATCH -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "register.password-mismatch")));
                        case TOO_SHORT -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "register.password-too-short",
                                        Map.of("min", String.valueOf(plugin.getConfigManager().getPasswordMinLength())))));
                        case TOO_LONG -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "register.password-too-long",
                                        Map.of("max", String.valueOf(plugin.getConfigManager().getPasswordMaxLength())))));
                        case UNSAFE -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "register.password-unsafe")));
                        case INVALID_NAME -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "register.invalid-username")));
                        case ALREADY_REGISTERED -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .get(player, "already-registered")));
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
        return List.of();
    }
}
