package xyz.naxho.growfarm;

import org.bukkit.plugin.Plugin;

/**
 * Isolated FastAsyncWorldEdit integration point.
 *
 * GrowFarm's block edits are small, localized, and already event-driven, so
 * there is no correctness-critical dependency on FAWE for AutoGrow/AutoPlant
 * themselves. This hook exists so:
 *   1) FAWE is a properly declared/configured dependency (see pom.xml), and
 *   2) future features that DO need bulk/async region edits (mass replanting,
 *      undo-friendly area operations, etc.) have a single, isolated place to
 *      add WorldEdit EditSession-based logic instead of scattering
 *      WorldEdit/FAWE calls throughout the codebase.
 *
 * Detection only touches the plugin manager (no reflection needed) - if FAWE
 * ever needs to be called directly, do it from here so the rest of the
 * plugin never has to worry about FAWE being absent.
 */
public final class FaweHook {
    private final boolean available;

    public FaweHook(Plugin plugin) {
        this.available = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    public boolean isAvailable() {
        return available;
    }

    // Extension point: add EditSession-backed bulk operations here as needed, e.g.
    //
    // public void bulkReplant(World world, List<BlockVector3> positions, BlockState state) {
    //     if (!available) { ... fallback to plain Bukkit block edits ... return; }
    //     try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
    //             .world(BukkitAdapter.adapt(world)).build()) {
    //         for (BlockVector3 pos : positions) {
    //             editSession.setBlock(pos, state);
    //         }
    //     }
    // }
}
