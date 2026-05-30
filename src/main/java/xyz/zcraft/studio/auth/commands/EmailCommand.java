package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.PlayerData;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * /email <add|change|verify|recover> [args]
 *
 * add <address>    — Register a new email (sends verification code)
 * change <address> — Change existing email (sends verification code)
 * verify <code>    — Confirm email with the code that was sent
 * recover          — Send a temporary password to the registered email
 */
public class EmailCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public EmailCommand(ZCraftAuth plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("error.player-only")));
            return true;
        }

        if (!plugin.getConfigManager().isEmailEnabled()) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "feature.disabled")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <add|change|verify|recover>"))));
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── /email add <address> ──────────────────────────────────────────
            case "add" -> {
                if (!plugin.getAuthManager().isAuthenticated(player)) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.usage", Map.of("usage", "/" + label + " add <address>"))));
                    return true;
                }
                String address = args[1];
                if (!plugin.getEmailManager().isValidEmail(address)) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.invalid")));
                    return true;
                }
                plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                    if (opt.isEmpty()) return;
                    PlayerData data = opt.get();
                    if (data.email() != null && !data.email().isBlank() && data.emailVerified()) {
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.already-set")));
                        return;
                    }
                    sendVerificationAndSave(player, data, address);
                });
            }

            // ── /email change <address> ───────────────────────────────────────
            case "change" -> {
                if (!plugin.getAuthManager().isAuthenticated(player)) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "not-authenticated")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.usage", Map.of("usage", "/" + label + " change <address>"))));
                    return true;
                }
                String address = args[1];
                if (!plugin.getEmailManager().isValidEmail(address)) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.invalid")));
                    return true;
                }
                plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                    if (opt.isEmpty()) return;
                    sendVerificationAndSave(player, opt.get(), address);
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "email.changed", Map.of("email", address))));
                });
            }

            // ── /email verify <code> ──────────────────────────────────────────
            case "verify" -> {
                if (args.length < 2) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.usage", Map.of("usage", "/" + label + " verify <code>"))));
                    return true;
                }
                String code = args[1];
                plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                    if (opt.isEmpty()) return;
                    PlayerData data = opt.get();

                    if (!data.emailPending()) {
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.token-invalid")));
                        return;
                    }
                    // Check expiry
                    if (data.emailPendingExpiry() != null && Instant.now().isAfter(data.emailPendingExpiry())) {
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.token-expired")));
                        return;
                    }
                    if (!code.equals(data.emailPendingToken())) {
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.token-invalid")));
                        return;
                    }
                    // Mark verified
                    PlayerData updated = data.toBuilder()
                            .emailVerified(true)
                            .emailPending(false)
                            .emailPendingToken(null)
                            .emailPendingExpiry(null)
                            .build();
                    plugin.getDatabase().updatePlayer(updated).thenRun(() ->
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.verified")))
                    );
                });
            }

            // ── /email recover ────────────────────────────────────────────────
            case "recover" -> {
                String ip = player.getAddress() != null
                        ? player.getAddress().getAddress().getHostAddress() : "127.0.0.1";
                if (plugin.getEmailManager().isRateLimited(ip)) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.rate-limited")));
                    return true;
                }
                plugin.getDatabase().findByUUID(player.getUniqueId()).thenAccept(opt -> {
                    if (opt.isEmpty() || !opt.get().hasVerifiedEmail()) {
                        player.sendMessage(mm.deserialize(plugin.getLanguageManager().get(player, "email.not-set")));
                        return;
                    }
                    plugin.getEmailManager().sendRecovery(opt.get()).thenAccept(sent -> {
                        if (sent) {
                            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                    .get(player, "email.recovery-success")));
                        } else {
                            player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                    .get(player, "error.generic")));
                        }
                    });
                });
            }

            default -> player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .get(player, "error.usage", Map.of("usage", "/" + label + " <add|change|verify|recover>"))));
        }
        return true;
    }

    private void sendVerificationAndSave(Player player, PlayerData data, String address) {
        String code   = plugin.getEmailManager().generateCode();
        Instant expiry = Instant.now().plusSeconds(plugin.getConfigManager().getEmailTokenExpiry() * 60L);

        PlayerData updated = data.toBuilder()
                .email(address)
                .emailVerified(false)
                .emailPending(true)
                .emailPendingToken(code)
                .emailPendingExpiry(expiry)
                .build();

        plugin.getDatabase().updatePlayer(updated).thenRun(() -> {
            plugin.getEmailManager().sendVerification(updated, address, code).thenAccept(sent -> {
                if (sent) {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "email.added", Map.of("email", address))));
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "email.verify-prompt")));
                } else {
                    player.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .get(player, "error.generic")));
                }
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isEmailEnabled()) return List.of();
        if (args.length == 1)
            return List.of("add", "change", "verify", "recover").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        return List.of();
    }
}
