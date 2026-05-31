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
                                sender.sendMessage(mm.deserialize("<red>Backup failed. Check the console."));
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
                            sender.sendMessage(mm.deserialize("<green>Import complete: " + count + " accounts."))
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
                                sender.sendMessage(mm.deserialize("<red>No account: " + name));
                                return;
                            }
                            plugin.getDatabase().deletePlayer(opt.get().uuid()).thenRun(() ->
                                    plugin.runSync(() -> {
                                        sender.sendMessage(mm.deserialize(plugin.getLanguageManager()
                                                .getDefault("admin.unregistered", Map.of("player", name))));
                                        Player online = plugin.getServer().getPlayerExact(name);
                                        if (online != null && online.isOnline()) {
                                            online.kick(mm.deserialize("<red>Your account was deleted by an administrator."));
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
                        sender.sendMessage(mm.deserialize("<red>Specify an IP: /" + label + " restrict <player> <ip>"));
                        return true;
                    }
                }
                plugin.getDatabase().findByUsername(name).thenAccept(opt ->
                        plugin.runSync(() -> {
                            if (opt.isEmpty()) {
                                sender.sendMessage(mm.deserialize("<red>No account: " + name));
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
                                sender.sendMessage(mm.deserialize("<red>No account: " + name));
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

            case "accounts" -> sender.sendMessage(mm.deserialize(
                    "<aqua><bold>Auth Stats</bold></aqua>\n" +
                            "<gray>Online Players : </gray><white>" + plugin.getServer().getOnlinePlayers().size() + "\n" +
                            "<gray>DB Provider    : </gray><white>" + plugin.getDatabase().getProviderType() + "\n" +
                            "<gray>Table          : </gray><white>" + plugin.getDatabase().getTableName() + "\n" +
                            "<gray>AntiBot Active : </gray><white>" + plugin.getAntiBotManager().isActive()
            ));

            case "antibot" -> sender.sendMessage(mm.deserialize(
                    "<yellow>AntiBot is <white>"
                            + (plugin.getAntiBotManager().isActive() ? "<green>ACTIVE" : "<red>INACTIVE") + "\n"
                            + "<yellow>Adjust thresholds in <white>config.yml -> antibot</white>."
            ));

            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(mm.deserialize(
                "<dark_gray>[<gradient:#00B4FF:#0077FF>Auth</gradient>]</dark_gray> <gray>Admin Commands\n" +
                        "<aqua>/" + label + " reload</aqua>               <dark_gray>-</dark_gray> <gray>Reload config and languages\n" +
                        "<aqua>/" + label + " backup</aqua>               <dark_gray>-</dark_gray> <gray>Manual DB backup\n" +
                        "<aqua>/" + label + " import</aqua>               <dark_gray>-</dark_gray> <gray>Import accounts\n" +
                        "<aqua>/" + label + " unregister <player></aqua>  <dark_gray>-</dark_gray> <gray>Delete account\n" +
                        "<aqua>/" + label + " restrict <player> [ip]</aqua> <dark_gray>-</dark_gray> <gray>Lock to IP\n" +
                        "<aqua>/" + label + " unrestrict <player></aqua>  <dark_gray>-</dark_gray> <gray>Remove IP lock\n" +
                        "<aqua>/" + label + " accounts</aqua>             <dark_gray>-</dark_gray> <gray>Show stats\n" +
                        "<aqua>/" + label + " antibot</aqua>              <dark_gray>-</dark_gray> <gray>AntiBot status"
        ));
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
