package xyz.zcraft.studio.auth.config;

import org.bukkit.configuration.file.FileConfiguration;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.util.List;

/**
 * Typed wrapper around config.yml.
 * All callers should go through this class — never raw getConfig() calls in logic.
 */
public class ConfigManager {

    private final ZCraftAuth plugin;
    private FileConfiguration cfg;

    public ConfigManager(ZCraftAuth plugin) {
        this.plugin = plugin;
        this.cfg    = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    // ─── General ──────────────────────────────────────────────────────────────

    public String getDefaultLanguage()       { return cfg.getString("general.default-language", "en"); }
    public String getPrefix()                { return cfg.getString("general.prefix", "[Auth] "); }
    public int    getLoginTimeout()          { return cfg.getInt("general.login-timeout", 0); }
    public int    getRegisterTimeout()       { return cfg.getInt("general.register-timeout", 0); }
    public int    getMaxLoginAttempts()      { return cfg.getInt("general.max-login-attempts", 5); }
    public int    getAttemptBanDuration()    { return cfg.getInt("general.attempt-ban-duration", 10); }
    public boolean isLimboEnabled()          { return cfg.getBoolean("location.enabled", cfg.getBoolean("general.limbo-spawn.enabled", true)); }
    public String getLimboWorld()            { return cfg.getString("location.world", cfg.getString("general.limbo-spawn.world", "world")); }
    public double getLimboX()                { return cfg.getDouble("location.x", cfg.getDouble("general.limbo-spawn.x", 0)); }
    public double getLimboY()                { return cfg.getDouble("location.y", cfg.getDouble("general.limbo-spawn.y", 64)); }
    public double getLimboZ()                { return cfg.getDouble("location.z", cfg.getDouble("general.limbo-spawn.z", 0)); }
    public float  getLimboYaw()              { return (float) cfg.getDouble("location.yaw", cfg.getDouble("general.limbo-spawn.yaw", 0)); }
    public float  getLimboPitch()            { return (float) cfg.getDouble("location.pitch", cfg.getDouble("general.limbo-spawn.pitch", 0)); }
    public boolean isTeleportToLastLocation(){ return cfg.getBoolean("general.teleport-to-last-location", true); }
    public boolean isHideInTablist()         { return cfg.getBoolean("general.hide-in-tablist", false); }
    public boolean isInvisibleBeforeAuth()   { return cfg.getBoolean("general.invisible-before-auth", false); }
    public boolean isAllowChatBeforeAuth()   { return cfg.getBoolean("general.allow-chat-before-auth", false); }
    public List<String> getAllowedCommands()  {
        List<String> configured = cfg.getStringList("general.allowed-commands-before-auth");
        return configured.isEmpty() ? List.of("/login", "/register", "/l", "/reg", "/2fa") : configured;
    }

    // â”€â”€â”€ Feature toggles â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public boolean isLoginCommandEnabled()   { return cfg.getBoolean("features.login-command", true); }
    public boolean isRegisterCommandEnabled(){ return cfg.getBoolean("features.register-command", true); }
    public boolean isLogoutCommandEnabled()   { return cfg.getBoolean("features.logout-command", true); }
    public boolean isChangePassEnabled()      { return cfg.getBoolean("features.changepass-command", true); }
    public boolean isEmailCommandEnabled()    { return cfg.getBoolean("features.email-command", true); }
    public boolean isTwoFactorCommandEnabled(){ return cfg.getBoolean("features.two-factor-command", true); }
    public boolean isForceLoginEnabled()      { return cfg.getBoolean("features.force-login-command", true); }
    public boolean isAdminCommandEnabled()    { return cfg.getBoolean("features.admin-command", true); }
    public boolean isSessionFeatureEnabled()  { return cfg.getBoolean("features.session-restore", true); }
    public boolean isPremiumFeatureEnabled()  { return cfg.getBoolean("features.premium-auto-login", true); }
    public boolean isAntiBotFeatureEnabled()  { return cfg.getBoolean("features.antibot", true); }
    public boolean isCountryFeatureEnabled()  { return cfg.getBoolean("features.country-restrictions", true); }
    public boolean isSpoofingFeatureEnabled() { return cfg.getBoolean("features.spoofing-protection", true); }
    public boolean isInventoryFeatureEnabled(){ return cfg.getBoolean("features.inventory-protection", true); }
    public boolean isBackupTaskEnabled()      { return cfg.getBoolean("features.backups", true); }
    public boolean isDiscordLoggingEnabled()  { return cfg.getBoolean("features.discord-logging", true); }
    public boolean isCacheFeatureEnabled()    { return cfg.getBoolean("features.cache", true); }
    public boolean isLegacyMigrationFeatureEnabled() { return cfg.getBoolean("features.legacy-hash-migration", true); }
    public boolean isGuiFeatureEnabled()      { return cfg.getBoolean("features.gui-prompts", true); }

    // ─── Database ─────────────────────────────────────────────────────────────

    public String getDatabaseType()          { return cfg.getString("database.type", "sqlite"); }
    public String getSQLiteFile()            { return cfg.getString("database.sqlite.file", "auth.db"); }
    public String getExternalHost()          { return cfg.getString("database.external.host", cfg.getString("database.mysql.host", cfg.getString("database.postgresql.host", "localhost"))); }
    public int    getExternalPort()          { return cfg.getInt("database.external.port", cfg.getInt("database.mysql.port", cfg.getInt("database.postgresql.port", 3306))); }
    public String getExternalDatabase()      { return cfg.getString("database.external.database", cfg.getString("database.mysql.database", cfg.getString("database.postgresql.database", "zcraft_auth"))); }
    public String getExternalUsername()      { return cfg.getString("database.external.username", cfg.getString("database.mysql.username", cfg.getString("database.postgresql.username", "root"))); }
    public String getExternalPassword()      { return cfg.getString("database.external.password", cfg.getString("database.mysql.password", cfg.getString("database.postgresql.password", ""))); }
    public boolean isExternalSSL()           { return cfg.getBoolean("database.external.ssl", cfg.getBoolean("database.mysql.ssl", false)); }
    public String getExternalTable()         { return cfg.getString("database.external.table", cfg.getString("database.mysql.table", cfg.getString("database.postgresql.table", "zcraft_accounts"))); }
    public String getColumnName(String key)  { return cfg.getString("database.external.columns." + key, cfg.getString("database.mysql.columns." + key, key)); }
    public int    getPoolMaxSize()           { return cfg.getInt("database.external.pool.maximum-pool-size", cfg.getInt("database.mysql.pool.maximum-pool-size", 10)); }
    public int    getPoolMinIdle()           { return cfg.getInt("database.external.pool.minimum-idle", cfg.getInt("database.mysql.pool.minimum-idle", 2)); }
    public long   getPoolConnTimeout()       { return cfg.getLong("database.external.pool.connection-timeout", cfg.getLong("database.mysql.pool.connection-timeout", 30000)); }
    public long   getPoolIdleTimeout()       { return cfg.getLong("database.external.pool.idle-timeout", cfg.getLong("database.mysql.pool.idle-timeout", 600000)); }
    public long   getPoolMaxLifetime()       { return cfg.getLong("database.external.pool.max-lifetime", cfg.getLong("database.mysql.pool.max-lifetime", 1800000)); }

    public String getMySQLHost()             { return getExternalHost(); }
    public int    getMySQLPort()             { return getExternalPort(); }
    public String getMySQLDatabase()         { return getExternalDatabase(); }
    public String getMySQLUsername()         { return getExternalUsername(); }
    public String getMySQLPassword()         { return getExternalPassword(); }
    public boolean isMySQLSSL()              { return isExternalSSL(); }
    public String getMySQLTable()            { return getExternalTable(); }
    public String getPgHost()                { return getExternalHost(); }
    public int    getPgPort()                { return cfg.getInt("database.external.port", cfg.getInt("database.postgresql.port", 5432)); }
    public String getPgDatabase()            { return getExternalDatabase(); }
    public String getPgUsername()            { return getExternalUsername(); }
    public String getPgPassword()            { return getExternalPassword(); }
    public String getPgTable()               { return getExternalTable(); }

    public boolean isCacheEnabled()          { return isCacheFeatureEnabled() && cfg.getBoolean("database.cache-enabled", true); }
    public int    getCacheExpiry()           { return cfg.getInt("database.cache-expiry", 300); }
    public boolean isBackupEnabled()         { return isBackupTaskEnabled() && cfg.getBoolean("database.backup.enabled", true); }
    public int    getBackupIntervalMinutes() { return cfg.getInt("database.backup.interval", 60); }
    public int    getBackupKeepCount()       { return cfg.getInt("database.backup.keep", 10); }
    public String getBackupDirectory()       { return cfg.getString("database.backup.directory", "backups"); }

    // ─── Premium ──────────────────────────────────────────────────────────────

    public boolean isPremiumEnabled()        { return isPremiumFeatureEnabled() && cfg.getBoolean("premium.enabled", true); }
    public int    getPremiumCacheDuration()  { return cfg.getInt("premium.cache-duration", 3600); }
    public int    getPremiumCheckTimeout()   { return cfg.getInt("premium.check-timeout", 5000); }
    public String getPremiumApiBehavior()    { return cfg.getString("premium.api-down-behavior", "cracked"); }

    // ─── Password ─────────────────────────────────────────────────────────────

    public String getHashAlgorithm()         { return cfg.getString("password.algorithm", "BCRYPT"); }
    public int    getPasswordMinLength()     { return cfg.getInt("password.min-length", 5); }
    public int    getPasswordMaxLength()     { return cfg.getInt("password.max-length", 64); }
    public List<String> getUnsafePasswords() { return cfg.getStringList("password.unsafe-passwords"); }
    public int    getBcryptCost()            { return cfg.getInt("password.bcrypt-cost", 12); }
    public String getArgon2Type()            { return cfg.getString("password.argon2.type", "ARGON2id"); }
    public int    getArgon2Iterations()      { return cfg.getInt("password.argon2.iterations", 3); }
    public int    getArgon2Memory()          { return cfg.getInt("password.argon2.memory", 65536); }
    public int    getArgon2Parallelism()     { return cfg.getInt("password.argon2.parallelism", 1); }
    public int    getPbkdf2Iterations()      { return cfg.getInt("password.pbkdf2.iterations", 100000); }
    public int    getPbkdf2KeyLength()       { return cfg.getInt("password.pbkdf2.key-length", 256); }
    public boolean isLegacyMigrationEnabled(){ return isLegacyMigrationFeatureEnabled() && cfg.getBoolean("password.legacy-migration.enabled", true); }
    public boolean isAutoRehash()            { return cfg.getBoolean("password.legacy-migration.auto-rehash", true); }

    // ─── Session ──────────────────────────────────────────────────────────────

    public boolean isSessionEnabled()        { return isSessionFeatureEnabled() && cfg.getBoolean("session.enabled", true); }
    public long   getSessionDuration()       { return cfg.getLong("session.duration", 86400); }
    public int    getSessionIpTolerance()    { return cfg.getInt("session.ip-tolerance", 0); }
    public boolean isPreventLocationKick()   { return cfg.getBoolean("session.prevent-location-kick", true); }

    // ─── 2FA ──────────────────────────────────────────────────────────────────

    public boolean is2FAEnabled()            { return isTwoFactorCommandEnabled() && cfg.getBoolean("two-factor.enabled", true); }
    public boolean isForce2FAForStaff()      { return cfg.getBoolean("two-factor.force-for-staff", false); }
    public int    getTotpWindow()            { return cfg.getInt("two-factor.window", 1); }
    public String getTotpIssuer()            { return cfg.getString("two-factor.issuer", "Server"); }

    // ─── Email ────────────────────────────────────────────────────────────────

    public boolean isEmailEnabled()          { return isEmailCommandEnabled() && cfg.getBoolean("email.enabled", true); }
    public boolean isEmailConfirmRequired()  { return cfg.getBoolean("email.require-confirmation", true); }
    public int    getEmailTokenExpiry()      { return cfg.getInt("email.token-expiry", 30); }
    public int    getEmailRateLimit()        { return cfg.getInt("email.rate-limit", 3); }
    public String getSmtpHost()              { return cfg.getString("email.smtp.host", "smtp.gmail.com"); }
    public int    getSmtpPort()              { return cfg.getInt("email.smtp.port", 587); }
    public boolean isSmtpSSL()               { return cfg.getBoolean("email.smtp.ssl", false); }
    public boolean isSmtpStartTLS()          { return cfg.getBoolean("email.smtp.starttls", true); }
    public String getSmtpUsername()          { return cfg.getString("email.smtp.username", ""); }
    public String getSmtpPassword()          { return cfg.getString("email.smtp.password", ""); }
    public String getEmailFromAddress()      { return cfg.getString("email.smtp.from-address", ""); }
    public String getEmailFromName()         { return cfg.getString("email.smtp.from-name", "Server"); }
    public String getEmailTemplate(String type, String part) {
        return cfg.getString("email.templates." + type + "." + part, "");
    }

    // ─── AntiBot ──────────────────────────────────────────────────────────────

    public boolean isAntiBotEnabled()        { return isAntiBotFeatureEnabled() && cfg.getBoolean("antibot.enabled", true); }
    public int    getMaxJoinsPerSecond()     { return cfg.getInt("antibot.max-joins-per-second", 5); }
    public int    getMaxPerIP()              { return cfg.getInt("antibot.max-per-ip", 0); }
    public int    getAntiBotActiveDuration() { return cfg.getInt("antibot.active-duration", 300); }
    public String getAntiBotKickMessage()    { return cfg.getString("antibot.kick-message", "<red>AntiBot active."); }
    public List<String> getAntiBotWhitelist(){ return cfg.getStringList("antibot.whitelisted-ips"); }

    // ─── Country ──────────────────────────────────────────────────────────────

    public boolean isCountryEnabled()        { return isCountryFeatureEnabled() && cfg.getBoolean("country.enabled", false); }
    public String getCountryMode()           { return cfg.getString("country.mode", "blacklist"); }
    public List<String> getCountryCodes()    { return cfg.getStringList("country.codes"); }
    public String getCountryKickMessage()    { return cfg.getString("country.kick-message", "<red>Country blocked."); }
    public String getGeoIPProvider()         { return cfg.getString("country.provider", "ip-api"); }
    public String getMaxMindDbPath()         { return cfg.getString("country.maxmind-db", "GeoLite2-Country.mmdb"); }
    public int    getGeoIPCacheDuration()    { return cfg.getInt("country.cache-duration", 3600); }

    // ─── Spoofing ─────────────────────────────────────────────────────────────

    public boolean isSpoofingProtectionEnabled() { return isSpoofingFeatureEnabled() && cfg.getBoolean("spoofing-protection.enabled", true); }
    public boolean isCaseInsensitiveCheck()      { return cfg.getBoolean("spoofing-protection.case-insensitive-check", true); }
    public String getUsernameRegex()             { return cfg.getString("spoofing-protection.username-regex", "^[a-zA-Z0-9_]{3,16}$"); }
    public boolean isConfusableCheck()           { return cfg.getBoolean("spoofing-protection.confusable-check", true); }

    // ─── Inventory protection ─────────────────────────────────────────────────

    public boolean isInventoryProtectionEnabled() { return isInventoryFeatureEnabled() && cfg.getBoolean("inventory-protection.enabled", true); }
    public boolean isProtectItemDrops()           { return cfg.getBoolean("inventory-protection.protect-item-drops", true); }
    public boolean isReturnEnderPearls()          { return cfg.getBoolean("inventory-protection.return-ender-pearls", true); }

    // ─── GUI ──────────────────────────────────────────────────────────────────

    public boolean isPreJoinDialogEnabled()  { return isGuiFeatureEnabled() && cfg.getBoolean("gui.pre-join-dialog", true); }
    public boolean isBossBarEnabled()        { return isGuiFeatureEnabled() && cfg.getBoolean("prompts.bossbar", cfg.getBoolean("gui.bossbar-prompt", true)); }
    public String getBossBarColor()          { return cfg.getString("gui.bossbar-color", "BLUE"); }
    public boolean isActionBarHintEnabled()  { return isGuiFeatureEnabled() && cfg.getBoolean("prompts.actionbar-fallback", cfg.getBoolean("gui.actionbar-hint", true)); }
    public boolean isTitlePromptEnabled()    { return isGuiFeatureEnabled() && cfg.getBoolean("gui.title-prompt", true); }

    // ─── Compat ───────────────────────────────────────────────────────────────

    public boolean isRestrictionEnabled() { return cfg.getBoolean("restrictions.enabled", true); }

    public boolean isCitizensSupport()       { return cfg.getBoolean("compatibility.citizens-support", true); }
    public boolean isCombatTagSupport()      { return cfg.getBoolean("compatibility.combattag-support", true); }
    public boolean isFloodgateSupport()      { return cfg.getBoolean("compatibility.floodgate-support", false); }

    // ─── Import ───────────────────────────────────────────────────────────────

    public String getImportSource()          { return cfg.getString("import.source", "authplus"); }
    public String getImportPath()            { return cfg.getString("import.path", ""); }
}
