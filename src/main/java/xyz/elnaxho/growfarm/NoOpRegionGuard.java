package xyz.elnaxho.growfarm;
import org.bukkit.Location;

/**
 * Used when WorldGuard is not installed, or {@code worldguard.enabled: false}
 * in config.yml. Never restricts AutoGrow/AutoPlant anywhere.
 *
 * Deliberately has no WorldGuard imports at all, so it is always 100% safe
 * to load and construct regardless of what's installed on the server.
 */
public final class NoOpRegionGuard implements RegionGuard {
    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isAutoGrowAllowed(Location location) {
        return true;
    }

    @Override
    public boolean isAutoPlantAllowed(Location location) {
        return true;
    }
}
