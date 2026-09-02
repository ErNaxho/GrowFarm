package xyz.elnaxho.growfarm;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Loads and exposes every configurable value from config.yml.
 * All feature classes read from here - nothing should call
 * plugin.getConfig() directly outside of this class.
 */
public class ConfigManager {
    private final JavaPlugin plugin;

    // --- Dependencies ---
    private boolean worldGuardEnabled = true;
    private boolean luckPermsEnabled = true;

    // --- General ---
    private boolean debug = false;
    private boolean exclusiveMode = true;

    // --- Farmlands (Crops) ---
    private boolean farmlondsEnabled = true;
    private int farmlandGrowArea = 4;
    private int farmlandGrowChance = 50;
    private boolean farmlandGrowStageGrowing = true;
    private boolean farmlandGrowBoneMealUse = false;
    private boolean farmlandGrowHoeUse = false;
    private boolean farmlandGrowCustomItemUse = false;
    private final Map<Material, Integer> farmlandGrowCustomItems = new HashMap<>();
    private final Set<Material> farmlandCrops = EnumSet.noneOf(Material.class);

    private int farmlandPlantArea = 4;
    private boolean farmlandPlantEnabled = true;
    // LinkedHashSet, not EnumSet: order matters here (config priority order
    // for which seed gets used first), and EnumSet always iterates in
    // Material's own ordinal order, silently ignoring the order written in
    // config.yml.
    private final Set<Material> farmlandSeeds = new LinkedHashSet<>();

    // --- Netherland (Nether Crops) ---
    private boolean netherlandEnabled = true;
    private int netherlandGrowArea = 4;
    private int netherlandGrowChance = 50;
    private boolean netherlandGrowStageGrowing = true;
    private boolean netherlandGrowBoneMealUse = false;
    private boolean netherlandGrowHoeUse = false;
    private boolean netherlandGrowCustomItemUse = false;
    private final Map<Material, Integer> netherlandGrowCustomItems = new HashMap<>();
    private final Set<Material> netherlandCrops = EnumSet.noneOf(Material.class);

    private int netherlandPlantArea = 4;
    private boolean netherlandPlantEnabled = true;
    // Same reasoning as farmlandSeeds - must preserve config order.
    private final Set<Material> netherlandSeeds = new LinkedHashSet<>();

    // --- Saplings ---
    private boolean saplingsEnabled = true;
    private boolean instantSaplings = true;
    private int saplingGrowChance = 50;
    private boolean saplingBoneMealUse = false;
    private boolean saplingHoeUse = false;
    private boolean saplingCustomItemUse = false;
    private final Map<Material, Integer> saplingCustomItems = new HashMap<>();
    private final Map<String, Boolean> enabledSaplingTypes = new HashMap<>();

    // --- Messages ---
    private String messageNoBoneMeal = "<red>You don't have enough bone meal to grow the crops.";
    private String messageNoHoe = "<red>You need a hoe to grow the crops.";
    private String messageNoCustomItems = "<red>You need %items% to grow the crops.";
    private String messageGrowBothOn = "<green>AutoGrow enabled (Sneak + Move).";
    private String messageGrowBothOff = "<red>AutoGrow disabled (Sneak + Move).";
    private String messageGrowSneakOn = "<green>Sneak Grow enabled.";
    private String messageGrowSneakOff = "<red>Sneak Grow disabled.";
    private String messageGrowMoveOn = "<green>Move Grow enabled.";
    private String messageGrowMoveOff = "<red>Move Grow disabled.";
    private String messagePlantOn = "<green>AutoPlant enabled.";
    private String messagePlantOff = "<red>AutoPlant disabled.";
    private String messageAutoGrowDisabledByAutoPlant = "<red>AutoGrow was disabled because AutoPlant is enabled.";
    private String messageAutoPlantDisabledByAutoGrow = "<red>AutoPlant was disabled because AutoGrow is enabled.";
    private String messageReloadSuccess = "<green>Configuration reloaded successfully.";
    private String messageNoPermission = "<red>You don't have permission to do this.";
    private String messagePlayersOnly = "<red>This command can only be used by a player.";

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeSaplingDefaults();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        try {
            // --- Dependencies ---
            worldGuardEnabled = config.getBoolean("worldguard.enabled", true);
            luckPermsEnabled = config.getBoolean("luckperms.enabled", true);

            // --- General ---
            exclusiveMode = config.getBoolean("general.exclusive-mode", true);
            debug = config.getBoolean("debug", false);

            // --- Farmlands ---
            loadFarmlandConfig(config);

            // --- Netherland ---
            loadNetherlandConfig(config);

            // --- Saplings ---
            loadSaplingsConfig(config);

            // --- Messages ---
            loadMessagesConfig(config);

        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to load GrowFarm configuration. Using last known good values.", exception);
        }
    }

    private void loadFarmlandConfig(FileConfiguration config) {
        ConfigurationSection farmlands = config.getConfigurationSection("farmlands");
        if (farmlands == null) return;

        farmlondsEnabled = farmlands.getBoolean("enabled", true);

        // AutoGrow
        ConfigurationSection autogrow = farmlands.getConfigurationSection("autogrow");
        if (autogrow != null) {
            farmlandGrowArea = Math.max(1, autogrow.getInt("area", 4));
            farmlandGrowChance = clamp(autogrow.getInt("growth-chance", 50), 0, 100);
            farmlandGrowStageGrowing = autogrow.getBoolean("stage-growing", true);
            farmlandGrowBoneMealUse = autogrow.getBoolean("bone-meal-use", false);
            farmlandGrowHoeUse = autogrow.getBoolean("hoe-use", false);
            farmlandGrowCustomItemUse = autogrow.getBoolean("custom-item-use", false);

            loadCustomItems(farmlandGrowCustomItems, autogrow.getConfigurationSection("custom-item-list"));

            farmlandCrops.clear();
            ConfigurationSection cropsSection = autogrow.getConfigurationSection("crops");
            if (cropsSection != null) {
                for (String key : cropsSection.getKeys(false)) {
                    Material material = Material.matchMaterial(key);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid farmland crop material in config.yml: " + key);
                        continue;
                    }
                    if (cropsSection.getBoolean(key, false)) {
                        farmlandCrops.add(material);
                    }
                }
            }
        }

        // AutoPlant
        ConfigurationSection autoplant = farmlands.getConfigurationSection("autoplant");
        if (autoplant != null) {
            farmlandPlantArea = Math.max(1, autoplant.getInt("area", 4));
            farmlandPlantEnabled = autoplant.getBoolean("plant", true);

            farmlandSeeds.clear();
            addMaterials(farmlandSeeds, autoplant.getStringList("farmland-seeds"));
        }
    }

    private void loadNetherlandConfig(FileConfiguration config) {
        ConfigurationSection netherland = config.getConfigurationSection("netherland");
        if (netherland == null) return;

        netherlandEnabled = netherland.getBoolean("enabled", true);

        // AutoGrow
        ConfigurationSection autogrow = netherland.getConfigurationSection("autogrow");
        if (autogrow != null) {
            netherlandGrowArea = Math.max(1, autogrow.getInt("area", 4));
            netherlandGrowChance = clamp(autogrow.getInt("growth-chance", 50), 0, 100);
            netherlandGrowStageGrowing = autogrow.getBoolean("stage-growing", true);
            netherlandGrowBoneMealUse = autogrow.getBoolean("bone-meal-use", false);
            netherlandGrowHoeUse = autogrow.getBoolean("hoe-use", false);
            netherlandGrowCustomItemUse = autogrow.getBoolean("custom-item-use", false);

            loadCustomItems(netherlandGrowCustomItems, autogrow.getConfigurationSection("custom-item-list"));

            netherlandCrops.clear();
            ConfigurationSection cropsSection = autogrow.getConfigurationSection("crops");
            if (cropsSection != null) {
                for (String key : cropsSection.getKeys(false)) {
                    Material material = Material.matchMaterial(key);
                    if (material == null) {
                        plugin.getLogger().warning("Invalid netherland crop material in config.yml: " + key);
                        continue;
                    }
                    if (cropsSection.getBoolean(key, false)) {
                        netherlandCrops.add(material);
                    }
                }
            }
        }

        // AutoPlant
        ConfigurationSection autoplant = netherland.getConfigurationSection("autoplant");
        if (autoplant != null) {
            netherlandPlantArea = Math.max(1, autoplant.getInt("area", 4));
            netherlandPlantEnabled = autoplant.getBoolean("plant", true);

            netherlandSeeds.clear();
            addMaterials(netherlandSeeds, autoplant.getStringList("soul-sand-seeds"));
        }
    }

    private void loadSaplingsConfig(FileConfiguration config) {
        ConfigurationSection saplings = config.getConfigurationSection("saplings");
        if (saplings == null) return;

        saplingsEnabled = saplings.getBoolean("enabled", true);
        instantSaplings = saplings.getBoolean("insta-saplings", true);
        saplingGrowChance = clamp(saplings.getInt("growth-chance", 50), 0, 100);
        saplingBoneMealUse = saplings.getBoolean("bone-meal-use", false);
        saplingHoeUse = saplings.getBoolean("hoe-use", false);
        saplingCustomItemUse = saplings.getBoolean("custom-item-use", false);

        loadCustomItems(saplingCustomItems, saplings.getConfigurationSection("custom-item-list"));

        // Load individual sapling types
        ConfigurationSection saplingTypes = saplings.getConfigurationSection("sapling-types");
        if (saplingTypes != null) {
            for (String key : saplingTypes.getKeys(false)) {
                enabledSaplingTypes.put(key, saplingTypes.getBoolean(key, false));
            }
        }
    }

    private void loadMessagesConfig(FileConfiguration config) {
        ConfigurationSection messages = config.getConfigurationSection("messages");
        if (messages == null) return;

        messageNoBoneMeal = translate(messages.getString("no-bone-meal", messageNoBoneMeal));
        messageNoHoe = translate(messages.getString("no-hoe", messageNoHoe));
        // Keep %items% as a literal placeholder - it gets substituted at
        // send-time (per block type) with the actual configured items/amounts,
        // so translate() must not touch it.
        messageNoCustomItems = translate(messages.getString("no-custom-items", messageNoCustomItems));
        messageGrowBothOn = translate(messages.getString("grow-both-on", messageGrowBothOn));
        messageGrowBothOff = translate(messages.getString("grow-both-off", messageGrowBothOff));
        messageGrowSneakOn = translate(messages.getString("grow-sneak-on", messageGrowSneakOn));
        messageGrowSneakOff = translate(messages.getString("grow-sneak-off", messageGrowSneakOff));
        messageGrowMoveOn = translate(messages.getString("grow-move-on", messageGrowMoveOn));
        messageGrowMoveOff = translate(messages.getString("grow-move-off", messageGrowMoveOff));
        messagePlantOn = translate(messages.getString("plant-on", messagePlantOn));
        messagePlantOff = translate(messages.getString("plant-off", messagePlantOff));
        messageAutoGrowDisabledByAutoPlant = translate(messages.getString("autogrow-disabled-by-autoplant", messageAutoGrowDisabledByAutoPlant));
        messageAutoPlantDisabledByAutoGrow = translate(messages.getString("autoplant-disabled-by-autogrow", messageAutoPlantDisabledByAutoGrow));
        messageReloadSuccess = translate(messages.getString("reload-success", messageReloadSuccess));
        messageNoPermission = translate(messages.getString("no-permission", messageNoPermission));
        messagePlayersOnly = translate(messages.getString("players-only", messagePlayersOnly));
    }

    private void loadCustomItems(Map<Material, Integer> target, ConfigurationSection section) {
        target.clear();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Invalid custom item material in config.yml: " + key);
                continue;
            }
            int amount = section.getInt(key, 1);
            target.put(material, Math.max(1, amount));
        }
    }

    private void initializeSaplingDefaults() {
        enabledSaplingTypes.put("OAK", true);
        enabledSaplingTypes.put("SPRUCE", true);
        enabledSaplingTypes.put("BIRCH", true);
        enabledSaplingTypes.put("JUNGLE", true);
        enabledSaplingTypes.put("ACACIA", true);
        enabledSaplingTypes.put("DARK_OAK", true);
        enabledSaplingTypes.put("CHERRY", true);
        enabledSaplingTypes.put("MANGROVE", true);
    }

    private void addMaterials(Set<Material> target, List<String> names) {
        for (String entry : names) {
            Material material = Material.matchMaterial(entry);
            if (material == null) {
                plugin.getLogger().warning("Invalid material in config.yml: " + entry);
                continue;
            }
            target.add(material);
        }
    }

    // --- Dependencies ---
    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    // --- General ---
    public boolean isDebug() {
        return debug;
    }

    public boolean isExclusiveMode() {
        return exclusiveMode;
    }

    // --- Farmlands AutoGrow ---
    public boolean isFarmlandsEnabled() {
        return farmlondsEnabled;
    }

    public int getFarmlandGrowArea() {
        return farmlandGrowArea;
    }

    public int getFarmlandGrowChance() {
        return farmlandGrowChance;
    }

    public boolean isFarmlandGrowStageGrowing() {
        return farmlandGrowStageGrowing;
    }

    public boolean isFarmlandGrowBoneMealUse() {
        return farmlandGrowBoneMealUse;
    }

    public boolean isFarmlandGrowHoeUse() {
        return farmlandGrowHoeUse;
    }

    public boolean isFarmlandGrowCustomItemUse() {
        return farmlandGrowCustomItemUse;
    }

    public Map<Material, Integer> getFarmlandGrowCustomItems() {
        return farmlandGrowCustomItems;
    }

    public boolean isFarmlandCrop(Material material) {
        return farmlandCrops.contains(material);
    }

    public Set<Material> getFarmlandCrops() {
        return farmlandCrops;
    }

    // --- Farmlands AutoPlant ---
    public int getFarmlandPlantArea() {
        return farmlandPlantArea;
    }

    public boolean isFarmlandPlantEnabled() {
        return farmlandPlantEnabled;
    }

    // --- Netherland AutoGrow ---
    public boolean isNetherlandEnabled() {
        return netherlandEnabled;
    }

    public int getNetherlandGrowArea() {
        return netherlandGrowArea;
    }

    public int getNetherlandGrowChance() {
        return netherlandGrowChance;
    }

    public boolean isNetherlandGrowStageGrowing() {
        return netherlandGrowStageGrowing;
    }

    public boolean isNetherlandGrowBoneMealUse() {
        return netherlandGrowBoneMealUse;
    }

    public boolean isNetherlandGrowHoeUse() {
        return netherlandGrowHoeUse;
    }

    public boolean isNetherlandGrowCustomItemUse() {
        return netherlandGrowCustomItemUse;
    }

    public Map<Material, Integer> getNetherlandGrowCustomItems() {
        return netherlandGrowCustomItems;
    }

    public boolean isNetherlandCrop(Material material) {
        return netherlandCrops.contains(material);
    }

    public Set<Material> getNetherlandCrops() {
        return netherlandCrops;
    }

    // --- Netherland AutoPlant ---
    public int getNetherlandPlantArea() {
        return netherlandPlantArea;
    }

    public boolean isNetherlandPlantEnabled() {
        return netherlandPlantEnabled;
    }

    public Set<Material> getNetherlandSeeds() {
        return netherlandSeeds;
    }

    // --- Saplings ---
    public boolean isSaplingsEnabled() {
        return saplingsEnabled;
    }

    public boolean isInstaSaplings() {
        return instantSaplings;
    }

    public int getSaplingGrowChance() {
        return saplingGrowChance;
    }

    public boolean isSaplingBoneMealUse() {
        return saplingBoneMealUse;
    }

    public boolean isSaplingHoeUse() {
        return saplingHoeUse;
    }

    public boolean isSaplingCustomItemUse() {
        return saplingCustomItemUse;
    }

    public Map<Material, Integer> getSaplingCustomItems() {
        return saplingCustomItems;
    }

    public boolean isSaplingTypeEnabled(String saplingType) {
        return enabledSaplingTypes.getOrDefault(saplingType, false);
    }

    public Map<String, Boolean> getEnabledSaplingTypes() {
        return enabledSaplingTypes;
    }

    // --- Farmland & Netherland Seeds ---
    public Set<Material> getFarmlandSeeds() {
        return farmlandSeeds;
    }

    // --- Messages ---
    public String getMessageNoBoneMeal() {
        return messageNoBoneMeal;
    }

    public String getMessageNoHoe() {
        return messageNoHoe;
    }

    public String getMessageNoCustomItems() {
        return messageNoCustomItems;
    }

    public String getMessageGrowBothOn() {
        return messageGrowBothOn;
    }

    public String getMessageGrowBothOff() {
        return messageGrowBothOff;
    }

    public String getMessageGrowSneakOn() {
        return messageGrowSneakOn;
    }

    public String getMessageGrowSneakOff() {
        return messageGrowSneakOff;
    }

    public String getMessageGrowMoveOn() {
        return messageGrowMoveOn;
    }

    public String getMessageGrowMoveOff() {
        return messageGrowMoveOff;
    }

    public String getMessagePlantOn() {
        return messagePlantOn;
    }

    public String getMessagePlantOff() {
        return messagePlantOff;
    }

    public String getMessageAutoGrowDisabledByAutoPlant() {
        return messageAutoGrowDisabledByAutoPlant;
    }

    public String getMessageAutoPlantDisabledByAutoGrow() {
        return messageAutoPlantDisabledByAutoGrow;
    }

    public String getMessageReloadSuccess() {
        return messageReloadSuccess;
    }

    public String getMessageNoPermission() {
        return messageNoPermission;
    }

    public String getMessagePlayersOnly() {
        return messagePlayersOnly;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Deserializes a color-coded message from config.yml into a legacy
     * '&sect;'-coded String, so the rest of the plugin can keep using plain
     * Strings for chat while still supporting real color.
     *
     * Two formats are accepted so existing configs keep working either way:
     *  - Legacy ampersand codes, e.g. "&cHello" (this is what config.yml ships with)
     *  - MiniMessage tags, e.g. "<red>Hello" (only used if no '&' is present)
     */
    private static String translate(String message) {
        Component component = message.indexOf('&') >= 0
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(message)
                : MiniMessage.miniMessage().deserialize(message);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}