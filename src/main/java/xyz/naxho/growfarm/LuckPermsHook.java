package xyz.naxho.growfarm;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Isolated LuckPerms integration.
 *
 * All permission checks in the plugin (commands, features) use the standard
 * {@code Player#hasPermission} Bukkit API, which LuckPerms transparently
 * hooks into - so no LuckPerms-specific code is required for permissions to
 * work correctly with LuckPerms installed.
 *
 * This hook exposes the raw LuckPerms API (when present) for anything that
 * benefits from talking to LuckPerms directly, e.g. reading a player's
 * primary group for messages/placeholders, or context-aware permission
 * checks in future features - without forcing a hard dependency on the API
 * being present.
 */
public final class LuckPermsHook {
    private final LuckPerms api;

    public LuckPermsHook(Plugin plugin) {
        LuckPerms resolved = null;
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                resolved = LuckPermsProvider.get();
            } catch (IllegalStateException notReady) {
                resolved = null;
            }
        }
        this.api = resolved;
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * Returns the player's LuckPerms primary group name, or null if
     * LuckPerms is not installed or the player has no cached data yet.
     */
    public String getPrimaryGroup(Player player) {
        if (api == null) {
            return null;
        }
        var user = api.getUserManager().getUser(player.getUniqueId());
        return user != null ? user.getPrimaryGroup() : null;
    }
}
