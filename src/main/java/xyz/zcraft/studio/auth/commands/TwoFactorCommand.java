package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.util.List;
import java.util.Map;

/**
 * /2fa <enable|disable|verify> [code]
 */
public class TwoFactorCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TwoFactorCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }
        if (!plugin.getConfigManager().is2FAEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }
        if (!player.hasPermission("zcraftauth.2fa")) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "error.no-permission")));
            return true;
        }

        // If player is pending 2FA verification, /2fa verify <code> is the flow
        if (plugin.getAuthManager().isPending2FA(player.getUniqueId())) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("verify")) {
                handlePendingVerify(player, args[1]);
            } else {
                player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .get(player, "two-factor.prompt")));
            }
            return true;
        }

        if (!plugin.getAuthManager().isAuthenticated(player)) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <enable|disable|verify> [code]"))));
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /2fa enable ─────────────────────────────────────────────────────
            case "enable" -> plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                if (opt.isEmpty()) return;
                PlayerData data = opt.get();
                if (data.has2FA()) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.already-enabled")));
                    return;
                }
                String secret = plugin.getTwoFactorManager().generateSecret();
                String qr     = plugin.getTwoFactorManager().generateOtpAuthUrl(player.getName(), secret);

                // Store secret temporarily — only made permanent after first successful verify
                // For simplicity, we store immediately and the player uses /2fa verify to confirm
                PlayerData updated = data.toBuilder().totpSecret(secret).build();
                plugin.getDatabase().updatePlayer(updated).thenRun(() -> {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.setup-info")));
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.setup-key", Map.of("key", secret))));
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.setup-qr", Map.of("url", qr))));
                });
            });

            // /2fa disable ────────────────────────────────────────────────────
            case "disable" -> plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                if (opt.isEmpty()) return;
                PlayerData data = opt.get();
                if (!data.has2FA()) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.not-enabled")));
                    return;
                }
                // Require code to disable
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.usage", Map.of("usage", "/" + label + " disable <code>"))));
                    return;
                }
                if (!plugin.getTwoFactorManager().verifyCode(data.totpSecret(), args[1])) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.invalid-code")));
                    return;
                }
                PlayerData updated = data.toBuilder().totpSecret(null).build();
                plugin.getDatabase().updatePlayer(updated).thenRun(() ->
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "two-factor.disabled")))
                );
            });

            // /2fa verify <code> ──────────────────────────────────────────────
            case "verify" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.usage", Map.of("usage", "/" + label + " verify <code>"))));
                    return true;
                }
                handlePendingVerify(player, args[1]);
            }

            default -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <enable|disable|verify>"))));
        }
        return true;
    }

    private void handlePendingVerify(Player player, String code) {
        boolean ok = plugin.getAuthManager().attempt2FA(player, code);
        if (!ok) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "two-factor.invalid-code")));
        }
        // Success message handled by completeLogin inside AuthManager
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!plugin.getConfigManager().is2FAEnabled()) return List.of();
        if (a.length == 1)
            return List.of("enable", "disable", "verify").stream()
                    .filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        return List.of();
    }
}
