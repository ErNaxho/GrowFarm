package xyz.naxho.growfarm;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Main extends JavaPlugin {
    private ConfigManager configManager;
    private PlayerFeatureState featureState;
    private DebugLogger debugLogger;
    private RegionGuard regionGuard;
    private FaweHook faweHook;
    private LuckPermsHook luckPermsHook;

    @Override
    public void onLoad() {
        // WorldGuard flags must be registered before WorldGuard's own onEnable
        // locks its flag registry, so this has to happen in onLoad(), not onEnable().
        //
        // CRITICAL: the presence check must happen HERE, before WorldGuardHook
        // is referenced at all. Simply calling a static method on WorldGuardHook
        // forces the JVM to load and verify that entire class, which requires
        // resolving WorldGuard's own classes (used in its method signatures and
        // catch blocks) - even for methods that never end up running. On a
        // server without WorldGuard installed, that resolution fails with
        // NoClassDefFoundError, regardless of any "is WorldGuard present" check
        // written inside WorldGuardHook itself: by the time that check would
        // run, the class has already failed to load. Declaring WorldGuard as
        // a softdepend in plugin.yml only affects load order - it does NOT
        // make it safe to reference WorldGuard's classes when it's absent.
        if (isWorldGuardPresent()) {
            WorldGuardHook.registerFlags(this);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        featureState = new PlayerFeatureState();
        debugLogger = new DebugLogger(this, configManager);

        // Same rule as onLoad(): only ever construct WorldGuardHook when
        // WorldGuard is confirmed present. Otherwise use the WorldGuard-free
        // no-op implementation, which is always safe to load.
        regionGuard = (configManager.isWorldGuardEnabled() && isWorldGuardPresent())
                ? new WorldGuardHook(this, true)
                : new NoOpRegionGuard();

        faweHook = new FaweHook(this);

        // Same rule applies to LuckPermsHook (it imports LuckPerms' API
        // directly) - only construct it when LuckPerms is actually installed.
        luckPermsHook = isLuckPermsPresent() ? new LuckPermsHook(this) : null;

        getServer().getPluginManager().registerEvents(
                new GrowListener(configManager, featureState, regionGuard, debugLogger), this);
        getServer().getPluginManager().registerEvents(
                new PlantListener(configManager, featureState, regionGuard, debugLogger), this);

        registerCommand("grow", new GrowCommand(configManager, featureState));
        registerCommand("plant", new PlantCommand(configManager, featureState));

        logIntegrationStatus();
        getLogger().info("GrowFarm has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GrowFarm has been disabled.");
    }

    private boolean isWorldGuardPresent() {
        return getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    private boolean isLuckPermsPresent() {
        return getServer().getPluginManager().getPlugin("LuckPerms") != null;
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Command " + name + " not defined in plugin.yml");
        if (executor instanceof org.bukkit.command.CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void logIntegrationStatus() {
        getLogger().info("WorldGuard integration: " + (regionGuard.isActive() ? "active" : "not active"));
        getLogger().info("FastAsyncWorldEdit integration: " + (faweHook.isAvailable() ? "available" : "not available"));
        getLogger().info("LuckPerms integration: " + (luckPermsHook != null && luckPermsHook.isAvailable() ? "available" : "not available"));
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerFeatureState getFeatureState() {
        return featureState;
    }

    public RegionGuard getRegionGuard() {
        return regionGuard;
    }

    public FaweHook getFaweHook() {
        return faweHook;
    }

    /**
     * Returns the LuckPerms integration hook, or null if LuckPerms is not
     * installed on this server. Always check for null before using it.
     */
    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
}
