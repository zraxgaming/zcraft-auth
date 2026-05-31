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
    private final TotpManager totpManager = new TotpManager();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AuthDatabase.Account> pending2fa = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    public ProxyAuthService(AuthConfig config) {
        this.config = config;
        this.database = new AuthDatabase(config);
        database.initialize();
    }

    public void handleJoin(PlayerView player, StateSender sender) {
        authenticated.remove(player.uuid());
        pending2fa.remove(player.uuid());
        sender.sendState(player.uuid(), false);
        CompletableFuture.runAsync(() -> {
            try {
                var account = database.find(player.uuid());
                if (account.isPresent()) {
                    if (config.sessionEnabled() && player.ip().equals(account.get().lastIp())) {
                        completeLogin(player, sender);
                        player.message(config.prefix() + "Session restored.");
                        return;
                    }
                    player.message(config.prefix() + "Please login with /login <password>");
                    player.prompt("Login required: /login <password>");
                    startTimeout(player, sender, config.loginTimeoutSeconds());
                } else {
                    player.message(config.prefix() + "Please register with /register <password> <password>");
                    player.prompt("Register required: /register <password> <password>");
                    startTimeout(player, sender, config.registerTimeoutSeconds());
                }
            } catch (Exception ex) {
                player.disconnect(config.prefix() + "Authentication is not ready. Please try again later.");
            }
        });
    }

    public void handleDisconnect(UUID uuid) {
        authenticated.remove(uuid);
        pending2fa.remove(uuid);
        cancelTimeout(uuid);
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
                if (has2fa(account.get())) {
                    pending2fa.put(player.uuid(), account.get());
                    player.message(config.prefix() + "Enter your authenticator code with /2fa verify <code>");
                    player.prompt("2FA required: /2fa verify <code>");
                    return;
                }
                completeLogin(player, sender);
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
                if (!validPassword(player, password)) {
                    return;
                }
                if (database.exists(player.uuid())) {
                    player.message(config.prefix() + "You are already registered. Use /login <password>");
                    return;
                }
                database.register(player.uuid(), player.username(), passwordHasher.hash(password), player.ip());
                completeLogin(player, sender);
                player.message(config.prefix() + "Registered and logged in.");
            } catch (Exception ex) {
                player.message(config.prefix() + "Registration failed because the auth database is not ready.");
            }
        });
    }

    public void verify2fa(PlayerView player, String code, StateSender sender) {
        CompletableFuture.runAsync(() -> {
            AuthDatabase.Account account = pending2fa.get(player.uuid());
            if (account == null) {
                player.message(config.prefix() + "No 2FA verification is pending.");
                return;
            }
            if (!totpManager.verify(account.totpSecret(), code)) {
                player.message(config.prefix() + "Invalid authenticator code.");
                return;
            }
            pending2fa.remove(player.uuid());
            completeLogin(player, sender);
            player.message(config.prefix() + "2FA verified. Logged in.");
        });
    }

    public void enable2fa(PlayerView player) {
        CompletableFuture.runAsync(() -> {
            if (!authenticated.contains(player.uuid())) {
                player.message(config.prefix() + "Log in before enabling 2FA.");
                return;
            }
            String secret = totpManager.generateSecret();
            database.setTotpSecret(player.uuid(), secret);
            player.message(config.prefix() + "2FA secret: " + secret);
            player.message(config.prefix() + "Authenticator URL: "
                    + totpManager.otpauthUrl("Auth", player.username(), secret));
        });
    }

    public void disable2fa(PlayerView player, String code) {
        CompletableFuture.runAsync(() -> {
            var account = database.find(player.uuid());
            if (account.isEmpty() || !has2fa(account.get())) {
                player.message(config.prefix() + "2FA is not enabled.");
                return;
            }
            if (!totpManager.verify(account.get().totpSecret(), code)) {
                player.message(config.prefix() + "Invalid authenticator code.");
                return;
            }
            database.setTotpSecret(player.uuid(), null);
            pending2fa.remove(player.uuid());
            player.message(config.prefix() + "2FA disabled.");
        });
    }

    public void changePassword(PlayerView player, String oldPassword, String newPassword) {
        CompletableFuture.runAsync(() -> {
            var account = database.find(player.uuid());
            if (account.isEmpty() || !passwordHasher.verify(oldPassword, account.get().passwordHash())) {
                player.message(config.prefix() + "Wrong current password.");
                return;
            }
            if (!validPassword(player, newPassword)) {
                return;
            }
            database.setPassword(player.uuid(), passwordHasher.hash(newPassword));
            player.message(config.prefix() + "Password changed.");
        });
    }

    public void logout(PlayerView player, StateSender sender) {
        authenticated.remove(player.uuid());
        pending2fa.remove(player.uuid());
        sender.sendState(player.uuid(), false);
        player.prompt("Login required: /login <password>");
        player.message(config.prefix() + "Logged out.");
    }

    public void admin(AdminView admin, String[] args, StateSender sender) {
        CompletableFuture.runAsync(() -> {
            if (!admin.hasPermission("zcraftauth.admin")) {
                admin.message(config.prefix() + "No permission.");
                return;
            }
            if (args.length == 0) {
                admin.message(config.prefix() + "Usage: /zauth <status|unregister|setpassword|disable2fa|forcelogin|logout> [player] [value]");
                return;
            }
            String action = args[0].toLowerCase();
            if ("status".equals(action)) {
                admin.message(config.prefix() + "Authenticated players: " + authenticated.size());
                return;
            }
            if (args.length < 2) {
                admin.message(config.prefix() + "Player name required.");
                return;
            }
            var account = database.findByUsername(args[1]);
            if (account.isEmpty()) {
                admin.message(config.prefix() + "Account not found.");
                return;
            }
            UUID target = account.get().uuid();
            switch (action) {
                case "unregister", "delete" -> {
                    database.delete(target);
                    authenticated.remove(target);
                    pending2fa.remove(target);
                    sender.sendState(target, false);
                    admin.message(config.prefix() + "Deleted account " + account.get().username() + ".");
                }
                case "setpassword", "changepass" -> {
                    if (args.length < 3) {
                        admin.message(config.prefix() + "New password required.");
                        return;
                    }
                    database.setPassword(target, passwordHasher.hash(args[2]));
                    admin.message(config.prefix() + "Changed password for " + account.get().username() + ".");
                }
                case "disable2fa" -> {
                    database.setTotpSecret(target, null);
                    pending2fa.remove(target);
                    admin.message(config.prefix() + "Disabled 2FA for " + account.get().username() + ".");
                }
                case "forcelogin" -> {
                    authenticated.add(target);
                    pending2fa.remove(target);
                    sender.sendState(target, true);
                    admin.message(config.prefix() + "Force logged in " + account.get().username() + ".");
                }
                case "logout" -> {
                    authenticated.remove(target);
                    pending2fa.remove(target);
                    sender.sendState(target, false);
                    admin.message(config.prefix() + "Logged out " + account.get().username() + ".");
                }
                default -> admin.message(config.prefix() + "Unknown admin action.");
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

    private void completeLogin(PlayerView player, StateSender sender) {
        database.markLogin(player.uuid(), player.ip());
        authenticated.add(player.uuid());
        cancelTimeout(player.uuid());
        player.clearPrompt();
        sender.sendState(player.uuid(), true);
        player.message(config.prefix() + "Logged in.");
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

    private boolean validPassword(PlayerView player, String password) {
        if (password.length() < config.minPasswordLength() || password.length() > config.maxPasswordLength()) {
            player.message(config.prefix() + "Password length must be "
                    + config.minPasswordLength() + "-" + config.maxPasswordLength() + " characters.");
            return false;
        }
        if (config.unsafePasswords().contains(password.toLowerCase())) {
            player.message(config.prefix() + "That password is too easy to guess.");
            return false;
        }
        return true;
    }

    private boolean has2fa(AuthDatabase.Account account) {
        return account.totpSecret() != null && !account.totpSecret().isBlank();
    }

    public interface AdminView {
        void message(String message);

        boolean hasPermission(String permission);
    }

    public interface PlayerView extends AdminView {
        UUID uuid();

        String username();

        String ip();

        void prompt(String message);

        void clearPrompt();

        void disconnect(String message);

    }

    public interface StateSender {
        void sendState(UUID uuid, boolean authenticated);
    }
}
