package xyz.naxho.growfarm;
import org.bukkit.Location;

/**
 * Abstraction over "is AutoGrow/AutoPlant allowed at this location".
 *
 * This interface has ZERO WorldGuard imports on purpose. GrowListener and
 * PlantListener depend only on this type, never on {@link WorldGuardHook}
 * directly - that way those classes (and the JVM's verification of them)
 * never need WorldGuard's classes to exist, whether or not WorldGuard is
 * installed on the server.
 *
 * Two implementations exist:
 *  - {@link WorldGuardHook}   - real region-flag-backed checks, only ever
 *                               constructed by Main when WorldGuard is
 *                               confirmed present.
 *  - {@link NoOpRegionGuard}  - always allows everything, used when
 *                               WorldGuard is absent or disabled in config.
 */
public interface RegionGuard {
    boolean isActive();

    boolean isAutoGrowAllowed(Location location);

    boolean isAutoPlantAllowed(Location location);
}
