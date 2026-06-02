package xyz.zcraft.studio.auth.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import xyz.zcraft.studio.auth.ZCraftAuth;
import xyz.zcraft.studio.auth.database.migration.AccountImporter;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthAdminCommand implements CommandExecutor, TabCompleter {

    private final ZCraftAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final List<String> SUBS = List.of(
            "reload", "backup", "import", "unregister", "restrict", "unrestrict", "accounts", "antibot"
    );

    public AuthAdminCommand(ZCraftAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isAdminCommandEnabled()) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("feature.disabled")));
            return true;
        }
        if (!sender.hasPermission("zcraftauth.admin")) {
            sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("admin.no-permission")));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("admin.reload-success")));
            }

            case "backup" -> {
                sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("admin.backup-started")));
                File dir = new File(plugin.getDataFolder(), plugin.getConfigManager().getBackupDirectory());
                plugin.getDatabase().backup(dir.getAbsolutePath()).thenAccept(path ->
                        plugin.runSync(() -> {
                            if (path != null) {
                                sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                        .getDefault("admin.backup-success", Map.of("file", new File(path).getName()))));
                            } else {
                                sender.sendMessage(mm.deserialize(plugin.getLanguageManager().getDefault("admin.backup-failed")));
                            }
                        })
                );
            }

            case "import" -> {
                String source = plugin.getConfigManager().getImportSource();
                String path = plugin.getConfigManager().getImportPath();
                sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                        .getDefault("admin.import-started", Map.of("source", source))));
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    int count = new AccountImporter(plugin).importFrom(source, path);
                    plugin.runSync(() ->
                            sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                    .getDefault("admin.import-complete", Map.of("count", String.valueOf(count)))))
                    );
                });
            }

            case "unregister" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .getDefault("error.usage", Map.of("usage", "/" + label + " unregister <player>"))));
                    return true;
                }
                String name = args[1];
                plugin.getDatabase().findByUsername(name).thenAccept(opt ->
                        plugin.runSync(() -> {
                            if (opt.isEmpty()) {
                                sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                        .getDefault("admin.no-account", Map.of("player", name))));
                                return;
                            }
                            plugin.getDatabase().deletePlayer(opt.get().uuid()).thenRun(() ->
                                    plugin.runSync(() -> {
                                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                                .getDefault("admin.unregistered", Map.of("player", name))));
                                        Player online = plugin.getServer().getPlayerExact(name);
                                        if (online != null && online.isOnline()) {
                                            online.kick(mm.deserialize(plugin.getLanguageManager().getDefault("admin.account-deleted-kick")));
                                        }
                                        plugin.getDiscordLogger().logAdminAction(
                                                sender.getName(), "UNREGISTER", name, "Account deleted");
                                    })
                            );
                        })
                );
            }

            case "restrict" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .getDefault("error.usage", Map.of("usage", "/" + label + " restrict <player> [ip]"))));
                    return true;
                }
                String name = args[1];
                final String lockIp;
                if (args.length >= 3) {
                    lockIp = args[2];
                } else {
                    Player online = plugin.getServer().getPlayerExact(name);
                    if (online != null && online.getAddress() != null) {
                        lockIp = online.getAddress().getAddress().getHostAddress();
                    } else {
                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                .getDefault("admin.specify-ip", Map.of("label", label))));
                        return true;
                    }
                }
                plugin.getDatabase().findByUsername(name).thenAccept(opt ->
                        plugin.runSync(() -> {
                            if (opt.isEmpty()) {
                                sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                        .getDefault("admin.no-account", Map.of("player", name))));
                                return;
                            }
                            plugin.getDatabase().updatePlayer(
                                    opt.get().toBuilder().restricted(true).restrictedIp(lockIp).build()
                            ).thenRun(() ->
                                    plugin.runSync(() -> {
                                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                                .getDefault("admin.restricted", Map.of("player", name, "ip", lockIp))));
                                        plugin.getDiscordLogger().logAdminAction(sender.getName(), "RESTRICT", name, "Locked to " + lockIp);
                                    })
                            );
                        })
                );
            }

            case "unrestrict" -> {
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                            .getDefault("error.usage", Map.of("usage", "/" + label + " unrestrict <player>"))));
                    return true;
                }
                String name = args[1];
                plugin.getDatabase().findByUsername(name).thenAccept(opt ->
                        plugin.runSync(() -> {
                            if (opt.isEmpty()) {
                                sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                        .getDefault("admin.no-account", Map.of("player", name))));
                                return;
                            }
                            plugin.getDatabase().updatePlayer(
                                    opt.get().toBuilder().restricted(false).restrictedIp(null).build()
                            ).thenRun(() ->
                                    plugin.runSync(() -> {
                                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                                .getDefault("admin.unrestricted", Map.of("player", name))));
                                        plugin.getDiscordLogger().logAdminAction(sender.getName(), "UNRESTRICT", name, "IP lock removed");
                                    })
                            );
                        })
                );
            }

            case "accounts" -> sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .getDefault("admin.accounts", Map.of(
                            "online", String.valueOf(plugin.getServer().getOnlinePlayers().size()),
                            "database", plugin.getDatabase().getProviderType(),
                            "table", plugin.getDatabase().getTableName(),
                            "antibot", String.valueOf(plugin.getAntiBotManager().isActive())
                    ))));

            case "antibot" -> sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                    .getDefault("admin.antibot-status", Map.of(
                            "status", plugin.getAntiBotManager().isActive() ? "ACTIVE" : "INACTIVE"
                    ))));

            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                .getDefault("admin.help", Map.of("label", label))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!plugin.getConfigManager().isAdminCommandEnabled()) return List.of();
        if (!sender.hasPermission("zcraftauth.admin")) return List.of();
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "unregister", "restrict", "unrestrict" -> plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                case "antibot" -> List.of("on", "off");
                default -> List.of();
            };
        }
        return List.of();
    }
}
