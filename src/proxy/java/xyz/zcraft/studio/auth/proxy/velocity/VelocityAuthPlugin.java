package xyz.zcraft.studio.auth.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import xyz.zcraft.studio.auth.proxy.AuthConfig;
import xyz.zcraft.studio.auth.proxy.AuthStateMessages;
import xyz.zcraft.studio.auth.proxy.ProxyAuthService;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "zcraftauth",
        name = "Auth",
        version = "1.0.0",
        description = "Proxy-side authentication for Velocity",
        authors = {"ZCraft Studios"}
)
public final class VelocityAuthPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MinecraftChannelIdentifier channel =
            MinecraftChannelIdentifier.from(AuthStateMessages.CHANNEL);
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private ProxyAuthService auth;
    private boolean channelRegistered;

    @Inject
    public VelocityAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        initializeAuth();
        if (auth != null) {
            registerCommands();
            logger.info("Auth proxy loaded for Velocity.");
        }
    }

    private synchronized ProxyAuthService initializeAuth() {
        if (auth != null) {
            return auth;
        }
        if (!channelRegistered) {
            server.getChannelRegistrar().register(channel);
            channelRegistered = true;
        }
        InputStream defaults = getClass().getClassLoader().getResourceAsStream("config.yml");
        try {
            auth = new ProxyAuthService(new AuthConfig(dataDirectory, defaults));
            return auth;
        } catch (Exception ex) {
            logger.error("Auth proxy failed to initialize. Check config.yml and database settings.", ex);
            return null;
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (auth != null) {
            server.getAllPlayers().forEach(this::clearPrompt);
            bossBars.clear();
            auth.close();
        }
    }

    @Subscribe
    public void onPostLogin(com.velocitypowered.api.event.connection.PostLoginEvent event) {
        ProxyAuthService service = initializeAuth();
        if (service == null) {
            event.getPlayer().disconnect(Component.text("Auth is not ready. Please try again later."));
            return;
        }
        service.handleJoin(view(event.getPlayer()), this::sendState);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        ProxyAuthService service = auth;
        if (service != null) {
            service.handleDisconnect(event.getPlayer().getUniqueId());
        }
        clearPrompt(event.getPlayer());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ProxyAuthService service = initializeAuth();
        if (service != null) {
            sendState(event.getPlayer().getUniqueId(), service.isAuthenticated(event.getPlayer().getUniqueId()));
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String action = in.readUTF();
            UUID uuid = UUID.fromString(in.readUTF());
            ProxyAuthService service = initializeAuth();
            if (AuthStateMessages.BACKEND_HELLO.equals(action)
                    && connection.getPlayer().getUniqueId().equals(uuid)
                    && service != null) {
                sendState(uuid, service.isAuthenticated(uuid));
            }
        } catch (Exception ex) {
            logger.warn("Ignored invalid backend auth message: {}", ex.getMessage());
        }
    }

    private void registerCommands() {
        var commands = server.getCommandManager();
        commands.register(commands.metaBuilder("login").aliases("l").build(), new LoginCommand());
        commands.register(commands.metaBuilder("register").aliases("reg", "signup").build(), new RegisterCommand());
        commands.register(commands.metaBuilder("logout").aliases("lo").build(), new LogoutCommand());
        commands.register(commands.metaBuilder("changepass").aliases("changepassword", "cp").build(), new ChangePassCommand());
        commands.register(commands.metaBuilder("2fa").aliases("totp", "authenticator").build(), new TwoFactorCommand());
        commands.register(commands.metaBuilder("zauth").aliases("authadmin").build(), new AdminCommand());
    }

    private void sendState(UUID uuid, boolean loggedIn) {
        Optional<Player> player = server.getPlayer(uuid);
        if (player.isEmpty() || player.get().getCurrentServer().isEmpty()) {
            return;
        }
        player.get().getCurrentServer().get().sendPluginMessage(channel, AuthStateMessages.authState(uuid, loggedIn));
    }

    private ProxyAuthService.PlayerView view(Player player) {
        return new ProxyAuthService.PlayerView() {
            @Override
            public UUID uuid() {
                return player.getUniqueId();
            }

            @Override
            public String username() {
                return player.getUsername();
            }

            @Override
            public String ip() {
                return player.getRemoteAddress().getAddress().getHostAddress();
            }

            @Override
            public void message(String message) {
                player.sendMessage(Component.text(message));
            }

            @Override
            public void prompt(String message) {
                BossBar old = bossBars.remove(player.getUniqueId());
                if (old != null) {
                    player.hideBossBar(old);
                }
                BossBar bar = BossBar.bossBar(Component.text(message), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                bossBars.put(player.getUniqueId(), bar);
                player.showBossBar(bar);
            }

            @Override
            public void clearPrompt() {
                BossBar bar = bossBars.remove(player.getUniqueId());
                if (bar != null) {
                    player.hideBossBar(bar);
                }
            }

            @Override
            public void disconnect(String message) {
                clearPrompt();
                player.disconnect(Component.text(message));
            }

            @Override
            public boolean hasPermission(String permission) {
                return player.hasPermission(permission);
            }
        };
    }

    private void clearPrompt(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private final class LoginCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("Only players can use this command."));
                return;
            }
            if (invocation.arguments().length < 1) {
                player.sendMessage(Component.text("Usage: /login <password>"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service == null) {
                player.disconnect(Component.text("Auth is not ready. Please try again later."));
                return;
            }
            service.login(view(player), invocation.arguments()[0], VelocityAuthPlugin.this::sendState);
        }
    }

    private final class RegisterCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("Only players can use this command."));
                return;
            }
            if (invocation.arguments().length < 2) {
                player.sendMessage(Component.text("Usage: /register <password> <password>"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service == null) {
                player.disconnect(Component.text("Auth is not ready. Please try again later."));
                return;
            }
            service.register(view(player), invocation.arguments()[0], invocation.arguments()[1],
                    VelocityAuthPlugin.this::sendState);
        }
    }

    private final class LogoutCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (invocation.source() instanceof Player player) {
                ProxyAuthService service = initializeAuth();
                if (service != null) service.logout(view(player), VelocityAuthPlugin.this::sendState);
            }
        }
    }

    private final class ChangePassCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) return;
            if (invocation.arguments().length < 2) {
                player.sendMessage(Component.text("Usage: /changepass <old> <new>"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service != null) service.changePassword(view(player), invocation.arguments()[0], invocation.arguments()[1]);
        }
    }

    private final class TwoFactorCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) return;
            if (invocation.arguments().length < 1) {
                player.sendMessage(Component.text("Usage: /2fa <enable|disable|verify> [code]"));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service == null) return;
            String sub = invocation.arguments()[0].toLowerCase();
            if ("enable".equals(sub)) {
                service.enable2fa(view(player));
            } else if ("disable".equals(sub) && invocation.arguments().length >= 2) {
                service.disable2fa(view(player), invocation.arguments()[1]);
            } else if ("verify".equals(sub) && invocation.arguments().length >= 2) {
                service.verify2fa(view(player), invocation.arguments()[1], VelocityAuthPlugin.this::sendState);
            } else {
                player.sendMessage(Component.text("Usage: /2fa <enable|disable|verify> [code]"));
            }
        }
    }

    private final class AdminCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("Use /zauth in-game for now."));
                return;
            }
            ProxyAuthService service = initializeAuth();
            if (service != null) service.admin(view(player), invocation.arguments(), VelocityAuthPlugin.this::sendState);
        }
    }
}
