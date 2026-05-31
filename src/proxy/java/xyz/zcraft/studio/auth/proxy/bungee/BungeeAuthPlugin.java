package xyz.zcraft.studio.auth.proxy.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import xyz.zcraft.studio.auth.proxy.AuthConfig;
import xyz.zcraft.studio.auth.proxy.AuthStateMessages;
import xyz.zcraft.studio.auth.proxy.ProxyAuthService;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.UUID;

public final class BungeeAuthPlugin extends Plugin implements Listener {

    private ProxyAuthService auth;

    @Override
    public void onEnable() {
        getProxy().registerChannel(AuthStateMessages.CHANNEL);
        initializeAuth();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new LoginCommand("login", "l"));
        getProxy().getPluginManager().registerCommand(this, new RegisterCommand("register", "reg"));
        if (auth != null) {
            getLogger().info("Auth proxy loaded for BungeeCord.");
        }
    }

    private synchronized ProxyAuthService initializeAuth() {
        if (auth != null) {
            return auth;
        }
        InputStream defaults = getClass().getClassLoader().getResourceAsStream("config.yml");
        try {
            auth = new ProxyAuthService(new AuthConfig(getDataFolder().toPath(), defaults));
            return auth;
        } catch (Exception ex) {
            getLogger().severe("Auth proxy failed to initialize. Check config.yml and database settings.");
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public void onDisable() {
        if (auth != null) {
            auth.close();
        }
        getProxy().unregisterChannel(AuthStateMessages.CHANNEL);
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxyAuthService service = initializeAuth();
        if (service == null) {
            event.getPlayer().disconnect(TextComponent.fromLegacyText("Auth is not ready. Please try again later."));
            return;
        }
        service.handleJoin(view(event.getPlayer()), this::sendState);
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxyAuthService service = auth;
        if (service != null) {
            service.handleDisconnect(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        ProxyAuthService service = initializeAuth();
        if (service != null) {
            sendState(event.getPlayer().getUniqueId(), service.isAuthenticated(event.getPlayer().getUniqueId()));
        }
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!AuthStateMessages.CHANNEL.equals(event.getTag())) {
            return;
        }
        if (!(event.getSender() instanceof Server) || !(event.getReceiver() instanceof ProxiedPlayer player)) {
            return;
        }
        event.setCancelled(true);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String action = in.readUTF();
            UUID uuid = UUID.fromString(in.readUTF());
            ProxyAuthService service = initializeAuth();
            if (AuthStateMessages.BACKEND_HELLO.equals(action) && player.getUniqueId().equals(uuid) && service != null) {
                sendState(uuid, service.isAuthenticated(uuid));
            }
        } catch (Exception ex) {
            getLogger().warning("Ignored invalid backend auth message: " + ex.getMessage());
        }
    }

    private void sendState(UUID uuid, boolean loggedIn) {
        ProxiedPlayer player = getProxy().getPlayer(uuid);
        if (player == null || player.getServer() == null) {
            return;
        }
        player.getServer().sendData(AuthStateMessages.CHANNEL, AuthStateMessages.authState(uuid, loggedIn));
    }

    private ProxyAuthService.PlayerView view(ProxiedPlayer player) {
        return new ProxyAuthService.PlayerView() {
            @Override
            public UUID uuid() {
                return player.getUniqueId();
            }

            @Override
            public String username() {
                return player.getName();
            }

            @Override
            public String ip() {
                return player.getAddress().getAddress().getHostAddress();
            }

            @Override
            public void message(String message) {
                player.sendMessage(TextComponent.fromLegacyText(ChatColor.stripColor(message)));
            }

            @Override
            public void disconnect(String message) {
                player.disconnect(TextComponent.fromLegacyText(ChatColor.stripColor(message)));
            }
        };
    }

    private final class LoginCommand extends Command {
        private LoginCommand(String name, String... aliases) {
            super(name, null, aliases);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(TextComponent.fromLegacyText("Only players can use this command."));
                return;
            }
            if (args.length < 1) {
                player.sendMessage(TextComponent.fromLegacyText("Usage: /login <password>"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service == null) {
                player.disconnect(TextComponent.fromLegacyText("Auth is not ready. Please try again later."));
                return;
            }
            service.login(view(player), args[0], BungeeAuthPlugin.this::sendState);
        }
    }

    private final class RegisterCommand extends Command {
        private RegisterCommand(String name, String... aliases) {
            super(name, null, aliases);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(TextComponent.fromLegacyText("Only players can use this command."));
                return;
            }
            if (args.length < 2) {
                player.sendMessage(TextComponent.fromLegacyText("Usage: /register <password> <password>"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service == null) {
                player.disconnect(TextComponent.fromLegacyText("Auth is not ready. Please try again later."));
                return;
            }
            service.register(view(player), args[0], args[1], BungeeAuthPlugin.this::sendState);
        }
    }
}
