package xyz.zcraft.studio.auth.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import xyz.zcraft.studio.auth.database.PlayerData;

/**
 * Fired on the main thread whenever a player successfully authenticates.
 * Other plugins can listen to this to know when a player is fully logged in.
 */
public class PlayerAuthEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player     player;
    private final PlayerData data;
    private final AuthMethod method;

    public PlayerAuthEvent(Player player, PlayerData data, AuthMethod method) {
        this.player = player;
        this.data   = data;
        this.method = method;
    }

    /** The player who authenticated. */
    public Player getPlayer() { return player; }

    /** The player's stored account data. */
    public PlayerData getPlayerData() { return data; }

    /** How the player authenticated. */
    public AuthMethod getMethod() { return method; }

    @Override public HandlerList getHandlers()            { return HANDLERS; }
    public static HandlerList    getHandlerList()         { return HANDLERS; }

    public enum AuthMethod {
        PASSWORD,   // Normal /login
        SESSION,    // IP session restored
        PREMIUM,    // Mojang premium auto-login
        FORCE       // /forcelogin or API call
    }
}
