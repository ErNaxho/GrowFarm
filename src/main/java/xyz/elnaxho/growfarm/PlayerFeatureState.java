package xyz.elnaxho.growfarm;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which per-player toggles are currently active.
 * Kept as simple in-memory sets (not persisted) matching the original
 * plugin's behaviour - toggles reset on rejoin/restart unless you decide
 * to extend this class to persist to a file/database.
 */
public final class PlayerFeatureState {
    private final Set<UUID> sneakGrowEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> moveGrowEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> autoPlantEnabled = ConcurrentHashMap.newKeySet();

    public boolean isSneakGrow(UUID id) {
        return sneakGrowEnabled.contains(id);
    }

    public boolean isMoveGrow(UUID id) {
        return moveGrowEnabled.contains(id);
    }

    public boolean isAutoPlant(UUID id) {
        return autoPlantEnabled.contains(id);
    }

    public void setSneakGrow(UUID id, boolean value) {
        set(sneakGrowEnabled, id, value);
    }

    public void setMoveGrow(UUID id, boolean value) {
        set(moveGrowEnabled, id, value);
    }

    public void setAutoPlant(UUID id, boolean value) {
        set(autoPlantEnabled, id, value);
    }

    public boolean toggleAutoPlant(UUID id) {
        return toggle(autoPlantEnabled, id);
    }

    public boolean toggleSneakGrow(UUID id) {
        return toggle(sneakGrowEnabled, id);
    }

    public boolean toggleMoveGrow(UUID id) {
        return toggle(moveGrowEnabled, id);
    }

    /** Clears all toggles for a player, e.g. on quit if you want a fresh state on rejoin. */
    public void clear(UUID id) {
        sneakGrowEnabled.remove(id);
        moveGrowEnabled.remove(id);
        autoPlantEnabled.remove(id);
    }

    private void set(Set<UUID> set, UUID id, boolean value) {
        if (value) {
            set.add(id);
        } else {
            set.remove(id);
        }
    }

    private boolean toggle(Set<UUID> set, UUID id) {
        boolean newValue = !set.contains(id);
        set(set, id, newValue);
        return newValue;
    }
}
