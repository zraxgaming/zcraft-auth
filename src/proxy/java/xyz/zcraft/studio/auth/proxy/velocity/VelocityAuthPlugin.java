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
    private ProxyAuthService auth;

    @Inject
    public VelocityAuthPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(channel);
        InputStream defaults = getClass().getClassLoader().getResourceAsStream("config.yml");
        auth = new ProxyAuthService(new AuthConfig(dataDirectory, defaults));
        registerCommands();
        logger.info("Auth proxy loaded for Velocity.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (auth != null) {
            auth.close();
        }
    }

    @Subscribe
    public void onPostLogin(com.velocitypowered.api.event.connection.PostLoginEvent event) {
        auth.handleJoin(view(event.getPlayer()), this::sendState);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        auth.handleDisconnect(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        sendState(event.getPlayer().getUniqueId(), auth.isAuthenticated(event.getPlayer().getUniqueId()));
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
            if (AuthStateMessages.BACKEND_HELLO.equals(action)
                    && connection.getPlayer().getUniqueId().equals(uuid)) {
                sendState(uuid, auth.isAuthenticated(uuid));
            }
        } catch (Exception ex) {
            logger.warn("Ignored invalid backend auth message: {}", ex.getMessage());
        }
    }

    private void registerCommands() {
        var commands = server.getCommandManager();
        commands.register(commands.metaBuilder("login").aliases("l").build(), new LoginCommand());
        commands.register(commands.metaBuilder("register").aliases("reg").build(), new RegisterCommand());
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
            public void disconnect(String message) {
                player.disconnect(Component.text(message));
            }
        };
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
            auth.login(view(player), invocation.arguments()[0], VelocityAuthPlugin.this::sendState);
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
            auth.register(view(player), invocation.arguments()[0], invocation.arguments()[1],
                    VelocityAuthPlugin.this::sendState);
        }
    }
}
