package xyz.zcraft.studio.auth.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread whenever a player's authentication session ends.
 * This includes /logout, disconnect, and admin-forced logout.
 */
public class PlayerLogoutEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LogoutReason reason;

    public PlayerLogoutEvent(Player player, LogoutReason reason) {
        this.player = player;
        this.reason = reason;
    }

    public Player getPlayer()      { return player; }
    public LogoutReason getReason(){ return reason; }

    @Override public HandlerList getHandlers()     { return HANDLERS; }
    public static HandlerList    getHandlerList()  { return HANDLERS; }

    public enum LogoutReason {
        COMMAND,    // /logout
        DISCONNECT, // Player left the server
        FORCED,     // API / admin action
        SESSION_EXPIRED
    }
}
