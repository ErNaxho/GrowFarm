package xyz.elnaxho.growfarm;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin wrapper around the plugin logger that only prints when
 * {@code debug: true} is set in config.yml. Every message is prefixed with
 * "[GrowFarm Debug]" so it is easy to filter in console/log files.
 *
 * Callers should only log meaningful, non-spammy events (see SKILL usage in
 * GrowListener / PlantListener) - this class intentionally does not rate
 * limit itself, that responsibility belongs to the caller.
 */
public final class DebugLogger {
    private static final String PREFIX = "[GrowFarm Debug] ";

    private final JavaPlugin plugin;
    private final ConfigManager config;

    public DebugLogger(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void log(String message) {
        if (!config.isDebug()) {
            return;
        }
        plugin.getLogger().info(PREFIX + message);
    }
}
