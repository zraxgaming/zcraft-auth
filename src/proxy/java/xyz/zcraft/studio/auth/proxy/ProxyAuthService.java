package xyz.zcraft.studio.auth.proxy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ProxyAuthService implements AutoCloseable {

    private final AuthConfig config;
    private final AuthDatabase database;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    public ProxyAuthService(AuthConfig config) {
        this.config = config;
        this.database = new AuthDatabase(config);
        database.initialize();
    }

    public void handleJoin(PlayerView player, StateSender sender) {
        authenticated.remove(player.uuid());
        sender.sendState(player.uuid(), false);
        CompletableFuture.runAsync(() -> {
            try {
                boolean registered = database.exists(player.uuid());
                if (registered) {
                    player.message(config.prefix() + "Please login with /login <password>");
                    startTimeout(player, sender, config.loginTimeoutSeconds());
                } else {
                    player.message(config.prefix() + "Please register with /register <password> <password>");
                    startTimeout(player, sender, config.registerTimeoutSeconds());
                }
            } catch (Exception ex) {
                player.disconnect(config.prefix() + "Authentication is not ready. Please try again later.");
            }
        });
    }

    public void handleDisconnect(UUID uuid) {
        authenticated.remove(uuid);
        cancelTimeout(uuid);
    }

    public void sendCurrentState(UUID uuid, StateSender sender) {
        sender.sendState(uuid, authenticated.contains(uuid));
    }

    public void login(PlayerView player, String password, StateSender sender) {
        CompletableFuture.runAsync(() -> {
            try {
                var account = database.find(player.uuid());
                if (account.isEmpty()) {
                    player.message(config.prefix() + "You are not registered. Use /register <password> <password>");
                    return;
                }
                if (!passwordHasher.verify(password, account.get().passwordHash())) {
                    player.message(config.prefix() + "Wrong password.");
                    return;
                }
                database.markLogin(player.uuid(), player.ip());
                authenticated.add(player.uuid());
                cancelTimeout(player.uuid());
                sender.sendState(player.uuid(), true);
                player.message(config.prefix() + "Logged in.");
            } catch (Exception ex) {
                player.message(config.prefix() + "Login failed because the auth database is not ready.");
            }
        });
    }

    public void register(PlayerView player, String password, String confirm, StateSender sender) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!password.equals(confirm)) {
                    player.message(config.prefix() + "Passwords do not match.");
                    return;
                }
                if (password.length() < config.minPasswordLength() || password.length() > config.maxPasswordLength()) {
                    player.message(config.prefix() + "Password length must be "
                            + config.minPasswordLength() + "-" + config.maxPasswordLength() + " characters.");
                    return;
                }
                if (config.unsafePasswords().contains(password.toLowerCase())) {
                    player.message(config.prefix() + "That password is too easy to guess.");
                    return;
                }
                if (database.exists(player.uuid())) {
                    player.message(config.prefix() + "You are already registered. Use /login <password>");
                    return;
                }
                database.register(player.uuid(), player.username(), passwordHasher.hash(password), player.ip());
                authenticated.add(player.uuid());
                cancelTimeout(player.uuid());
                sender.sendState(player.uuid(), true);
                player.message(config.prefix() + "Registered and logged in.");
            } catch (Exception ex) {
                player.message(config.prefix() + "Registration failed because the auth database is not ready.");
            }
        });
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    @Override
    public void close() {
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();
        scheduler.shutdownNow();
        database.close();
    }

    private void startTimeout(PlayerView player, StateSender sender, int seconds) {
        if (seconds <= 0) {
            return;
        }
        cancelTimeout(player.uuid());
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            timeoutTasks.remove(player.uuid());
            if (!authenticated.contains(player.uuid())) {
                sender.sendState(player.uuid(), false);
                player.disconnect(config.prefix() + "You took too long to authenticate.");
            }
        }, seconds, TimeUnit.SECONDS);
        timeoutTasks.put(player.uuid(), task);
    }

    private void cancelTimeout(UUID uuid) {
        ScheduledFuture<?> task = timeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel(false);
        }
    }

    public interface PlayerView {
        UUID uuid();

        String username();

        String ip();

        void message(String message);

        void disconnect(String message);
    }

    public interface StateSender {
        void sendState(UUID uuid, boolean authenticated);
    }
}
