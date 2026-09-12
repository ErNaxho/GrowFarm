package xyz.naxho.growfarm;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared "Area" resolution logic used by both AutoGrow and AutoPlant.
 *
 * Area = 1 -> only the block the player is standing on/at the given center.
 * Area = N -> a spherical region of radius (N - 1) around the center,
 *             always including the center block.
 * Area has no hardcoded maximum - server owners are responsible for picking
 * sane values in config.yml since huge areas scale O(n^3).
 *
 * Blocks are visited NEAREST-FIRST (sorted by distance from center). This
 * matters whenever a requirement consumes a limited resource (bone meal,
 * hoe durability, custom items): with a large area and a scarce resource,
 * visiting blocks in arbitrary order could exhaust that resource on distant
 * blocks before ever reaching the block the player is actually standing on.
 * Nearest-first guarantees the player's own tile always gets first claim on
 * whatever they're carrying.
 */
public final class AreaUtil {

    private AreaUtil() {
    }

    public static void forEachInArea(Location center, int area, Consumer<Block> consumer) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int radius = Math.max(0, area - 1);
        int radiusSquared = radius * radius;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            int dxSquared = dx * dx;
            for (int dy = -radius; dy <= radius; dy++) {
                int dxdySquared = dxSquared + dy * dy;
                if (dxdySquared > radiusSquared) {
                    continue;
                }
                for (int dz = -radius; dz <= radius; dz++) {
                    int distSquared = dxdySquared + dz * dz;
                    if (distSquared > radiusSquared) {
                        continue;
                    }
                    offsets.add(new int[]{dx, dy, dz, distSquared});
                }
            }
        }

        offsets.sort(Comparator.comparingInt(offset -> offset[3]));

        for (int[] offset : offsets) {
            consumer.accept(world.getBlockAt(cx + offset[0], cy + offset[1], cz + offset[2]));
        }
    }
}
