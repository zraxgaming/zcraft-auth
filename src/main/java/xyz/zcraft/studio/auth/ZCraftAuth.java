package xyz.zcraft.studio.auth;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.zcraft.studio.auth.antibot.AntiBotManager;
import xyz.zcraft.studio.auth.auth.AuthManager;
import xyz.zcraft.studio.auth.auth.PremiumChecker;
import xyz.zcraft.studio.auth.commands.*;
import xyz.zcraft.studio.auth.config.ConfigManager;
import xyz.zcraft.studio.auth.database.DatabaseProvider;
import xyz.zcraft.studio.auth.database.impl.MySQLProvider;
import xyz.zcraft.studio.auth.database.impl.PostgreSQLProvider;
import xyz.zcraft.studio.auth.database.impl.SQLiteProvider;
import xyz.zcraft.studio.auth.discord.DiscordLogger;
import xyz.zcraft.studio.auth.email.EmailManager;
import xyz.zcraft.studio.auth.i18n.LanguageManager;
import xyz.zcraft.studio.auth.listeners.AuthListener;
import xyz.zcraft.studio.auth.listeners.InventoryProtectionListener;
import xyz.zcraft.studio.auth.scheduler.BackupTask;
import xyz.zcraft.studio.auth.security.CountryManager;
import xyz.zcraft.studio.auth.security.SpoofingProtection;
import xyz.zcraft.studio.auth.session.SessionManager;
import xyz.zcraft.studio.auth.twofactor.TwoFactorManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Minecraft authentication plugin.
 * Based on Z-FFA Core by ZCraft Studios (https://github.com/zraxgaming/ffa-plugin)
 * Compatible with Paper 1.21.1+ and the wider 1.21.x line.
 */
public final class ZCraftAuth extends JavaPlugin {

    private static ZCraftAuth instance;

    private ConfigManager      configManager;
    private DatabaseProvider   database;
    private AuthManager        authManager;
    private SessionManager     sessionManager;
    private PremiumChecker     premiumChecker;
    private AntiBotManager     antiBotManager;
    private CountryManager     countryManager;
    private SpoofingProtection spoofingProtection;
    private TwoFactorManager   twoFactorManager;
    private EmailManager       emailManager;
    private LanguageManager    languageManager;
    private DiscordLogger      discordLogger;

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        migrateLegacyDataFolder();
        saveDefaultConfig();

        this.configManager   = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.discordLogger   = new DiscordLogger(this);

        if (!initDatabase()) {
            getLogger().severe("Database failed - disabling the auth plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.sessionManager     = new SessionManager(this);
        this.premiumChecker     = new PremiumChecker(this);
        this.antiBotManager     = new AntiBotManager(this);
        this.countryManager     = new CountryManager(this);
        this.spoofingProtection = new SpoofingProtection(this);
        this.twoFactorManager   = new TwoFactorManager(this);
        this.emailManager       = new EmailManager(this);
        this.authManager        = new AuthManager(this);

        initMetrics();
        registerCommands();
        registerListeners();

        if (configManager.isBackupEnabled()) {
            long interval = configManager.getBackupIntervalMinutes() * 60L * 20L;
            new BackupTask(this).runTaskTimerAsynchronously(this, interval, interval);
        }

        getServer().getOnlinePlayers().forEach(p -> authManager.evaluateOnJoin(p));

        printStartupSummary();

        discordLogger.logInfo(
                "Auth Enabled",
                "Version **" + getDescription().getVersion() + "** loaded on Paper 1.21.x\n"
                        + "Database: **" + database.getProviderType() + "**"
        );

        getLogger().info("Auth " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (authManager != null) authManager.onShutdown();
        if (database != null) database.close();
        if (discordLogger != null) discordLogger.logInfo("Auth Disabled", "Plugin shut down gracefully.");
        getLogger().info("Auth disabled.");
    }

    private boolean initDatabase() {
        database = switch (configManager.getDatabaseType().toLowerCase()) {
            case "mysql", "mariadb" -> new MySQLProvider(this);
            case "postgresql" -> new PostgreSQLProvider(this);
            default -> new SQLiteProvider(this);
        };
        try {
            database.initialize();
            getLogger().info("Database [" + database.getProviderType() + "] ready.");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Database init error", e);
            return false;
        }
    }

    private void initMetrics() {
        Metrics metrics = new Metrics(this, 31667);
        metrics.addCustomChart(new Metrics.SimplePie("database_type",
                () -> database.getProviderType().toLowerCase(Locale.ROOT)));
        metrics.addCustomChart(new Metrics.SimplePie("premium_auto_login",
                () -> configManager.isPremiumEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new Metrics.SimplePie("session_restore",
                () -> configManager.isSessionEnabled() ? "enabled" : "disabled"));
        metrics.addCustomChart(new Metrics.SimplePie("two_factor",
                () -> configManager.is2FAEnabled() ? "enabled" : "disabled"));
    }

    private void registerCommands() {
        bindCmd("login", new LoginCommand(this));
        bindCmd("register", new RegisterCommand(this));
        bindCmd("logout", new LogoutCommand(this));
        bindCmd("changepass", new ChangePasswordCommand(this));
        bindCmd("email", new EmailCommand(this));
        bindCmd("2fa", new TwoFactorCommand(this));
        bindCmd("forcelogin", new ForceLoginCommand(this));
        bindCmd("zauth", new AuthAdminCommand(this));
    }

    private void bindCmd(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command not found in plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) cmd.setTabCompleter(tc);
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this), this);
        if (configManager.isInventoryProtectionEnabled()) {
            pm.registerEvents(new InventoryProtectionListener(this), this);
        }
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        languageManager.reload();
        discordLogger.reload();
        authManager.reload();
        sessionManager.purgeExpired();
        discordLogger.logInfo("Auth Reloaded", "Config and languages reloaded successfully.");
        getLogger().info("Auth reloaded.");
    }

    public void runSync(Runnable task) {
        if (getServer().isPrimaryThread()) {
            task.run();
        } else {
            getServer().getScheduler().runTask(this, task);
        }
    }

    private void printBanner() {
        getLogger().info("==============================================");
        getLogger().info(" Auth v" + getDescription().getVersion());
        getLogger().info(" Paper 1.21.1+ and 1.21.x");
        getLogger().info("==============================================");
    }

    private void printStartupSummary() {
        getLogger().info("[Startup] Database  : " + database.getProviderType());
        getLogger().info("[Startup] Commands  : login, register, logout, changepass, email, 2fa, forcelogin, zauth");
        getLogger().info("[Startup] Modules   : "
                + enabledLabel("session", configManager.isSessionEnabled()) + ", "
                + enabledLabel("premium", configManager.isPremiumEnabled()) + ", "
                + enabledLabel("antibot", configManager.isAntiBotEnabled()) + ", "
                + enabledLabel("country", configManager.isCountryEnabled()) + ", "
                + enabledLabel("spoofing", configManager.isSpoofingProtectionEnabled()) + ", "
                + enabledLabel("inventory", configManager.isInventoryProtectionEnabled()) + ", "
                + enabledLabel("backups", configManager.isBackupEnabled()));
        getLogger().info("[Startup] Discord   : " + (configManager.isDiscordLoggingEnabled() ? "enabled" : "disabled"));
    }

    private String enabledLabel(String name, boolean enabled) {
        return name + "=" + (enabled ? "on" : "off");
    }

    private void migrateLegacyDataFolder() {
        File pluginsDir = getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return;
        }
        File legacy = new File(pluginsDir, "ZCraftAuth");
        File current = getDataFolder();

        if (!legacy.exists()) {
            return;
        }
        if (current.exists() && current.list() != null && current.list().length > 0) {
            return;
        }

        try {
            Files.createDirectories(current.toPath());
            try (var paths = Files.walk(legacy.toPath())) {
                paths.forEach(source -> {
                    try {
                        Path target = current.toPath().resolve(legacy.toPath().relativize(source).toString());
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else if (!Files.exists(target)) {
                            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            getLogger().info("Migrated legacy data folder from ZCraftAuth to Auth.");
        } catch (Exception e) {
            getLogger().warning("Could not migrate legacy data folder: " + e.getMessage());
        }
    }

    public static ZCraftAuth getInstance()          { return instance; }
    public ConfigManager getConfigManager()         { return configManager; }
    public DatabaseProvider getDatabase()           { return database; }
    public AuthManager getAuthManager()             { return authManager; }
    public SessionManager getSessionManager()       { return sessionManager; }
    public PremiumChecker getPremiumChecker()       { return premiumChecker; }
    public AntiBotManager getAntiBotManager()       { return antiBotManager; }
    public CountryManager getCountryManager()       { return countryManager; }
    public SpoofingProtection getSpoofingProtection(){ return spoofingProtection; }
    public TwoFactorManager getTwoFactorManager()   { return twoFactorManager; }
    public EmailManager getEmailManager()           { return emailManager; }
    public LanguageManager getLanguageManager()     { return languageManager; }
    public DiscordLogger getDiscordLogger()         { return discordLogger; }
}
