package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.auth.PasswordEngine;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.util.List;
import java.util.Map;

public class ChangePasswordCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ChangePasswordCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }
        if (!plugin.getConfigManager().isChangePassEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }
        if (!plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <oldPassword> <newPassword>"))));
            return true;
        }

        String oldPw = args[0];
        String newPw = args[1];

        plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
            if (opt.isEmpty()) {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "error.generic")));
                return;
            }
            PlayerData data = opt.get();
            PasswordEngine engine = new PasswordEngine(plugin);

            if (!engine.verify(oldPw, data.passwordHash()).matched()) {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "changepassword.wrong-old")));
                return;
            }
            if (oldPw.equals(newPw)) {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "changepassword.same-as-old")));
                return;
            }
            if (newPw.length() < plugin.getConfigManager().getPasswordMinLength()) {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "register.password-too-short",
                                Map.of("min", String.valueOf(plugin.getConfigManager().getPasswordMinLength())))));
                return;
            }
            if (newPw.length() > plugin.getConfigManager().getPasswordMaxLength()) {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "register.password-too-long",
                                Map.of("max", String.valueOf(plugin.getConfigManager().getPasswordMaxLength())))));
                return;
            }

            String newHash = engine.hash(newPw);
            PlayerData updated = data.toBuilder().passwordHash(newHash).build();
            plugin.getDatabase().updatePlayer(updated).thenRun(() ->
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "changepassword.success")))
            );
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return List.of(); }
}
