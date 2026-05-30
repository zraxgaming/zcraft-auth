package xyz.zcraft.studio.auth.scheduler;

import org.bukkit.scheduler.BukkitRunnable;
import xyz.zcraft.studio.auth.ZCraftAuth;

import java.io.File;

/**
 * Scheduled task that triggers an automatic database backup at the configured interval.
 * Runs asynchronously to avoid blocking the main thread.
 */
public class BackupTask extends BukkitRunnable {

    private final ZCraftAuth plugin;

    public BackupTask(ZCraftAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        File backupDir = new File(plugin.getDataFolder(),
                plugin.getConfigManager().getBackupDirectory());

        plugin.getDatabase().backup(backupDir.getAbsolutePath())
                .thenAccept(path -> {
                    if (path != null) {
                        plugin.getLogger().info("[Backup] Automatic backup saved: "
                                + new File(path).getName());
                    } else {
                        plugin.getLogger().warning("[Backup] Automatic backup failed. Check logs.");
                    }
                });
    }
}
