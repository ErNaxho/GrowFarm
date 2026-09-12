package xyz.naxho.growfarm;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Isolated WorldGuard integration.
 *
 * Registers two custom StateFlags - "autogrow" and "autoplant" - that region
 * owners can set with /rg flag &lt;region&gt; autogrow deny (etc). Both flags
 * default to ALLOW, so behaviour outside any flagged region (or on servers
 * without WorldGuard installed) is completely unaffected.
 *
 * Registration MUST happen from JavaPlugin#onLoad() (see Main#onLoad),
 * before WorldGuard locks its flag registry during its own onEnable().
 * The rest of this class is safe to use at any time after that.
 */
public final class WorldGuardHook implements RegionGuard {
    private static StateFlag autoGrowFlag;
    private static StateFlag autoPlantFlag;
    private static boolean registrationAttempted = false;

    private final boolean active;

    /**
     * Call once from Main#onLoad(), before any region queries happen.
     */
    public static void registerFlags(Plugin plugin) {
        if (registrationAttempted) {
            return;
        }
        registrationAttempted = true;

        if (!plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")
                && plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }

        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            autoGrowFlag = registerOrFetch(registry, new StateFlag("autogrow", true));
            autoPlantFlag = registerOrFetch(registry, new StateFlag("autoplant", true));
        } catch (Throwable throwable) {
            // WorldGuard not available / API mismatch - fail safe, feature simply won't restrict anything.
            plugin.getLogger().warning("Could not register WorldGuard flags (autogrow/autoplant): "
                    + throwable.getMessage());
        }
    }

    private static StateFlag registerOrFetch(FlagRegistry registry, StateFlag flag) {
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException alreadyRegistered) {
            Object existing = registry.get(flag.getName());
            return existing instanceof StateFlag stateFlag ? stateFlag : null;
        }
    }

    public WorldGuardHook(Plugin plugin, boolean configEnabled) {
        boolean present = configEnabled && plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
        this.active = present && autoGrowFlag != null && autoPlantFlag != null;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isAutoGrowAllowed(Location location) {
        return isAllowed(location, autoGrowFlag);
    }

    @Override
    public boolean isAutoPlantAllowed(Location location) {
        return isAllowed(location, autoPlantFlag);
    }

    private boolean isAllowed(Location location, StateFlag flag) {
        if (!active || flag == null || location.getWorld() == null) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        StateFlag.State state = query.queryState(BukkitAdapter.adapt(location), null, flag);
        return state != StateFlag.State.DENY;
    }
}
