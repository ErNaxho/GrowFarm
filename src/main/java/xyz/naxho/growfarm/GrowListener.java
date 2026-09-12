package xyz.naxho.growfarm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles both AutoGrow modes:
 *
 *  - Move Grow: triggers whenever the player moves into a new block.
 *               Only advances Ageable crops (wheat, carrots, stems, etc).
 *               Never touches saplings.
 *
 *  - Sneak Grow: triggers while the player is sneaking (both on the initial
 *                toggle-sneak and on subsequent movement while sneaking).
 *                Advances Ageable crops AND saplings.
 *
 * Sapling growth uses {@link Block#applyBoneMeal(BlockFace)}, which is
 * vanilla's own bone-meal-application logic. This is deliberate: it means
 * every current and future vanilla sapling layout (1x1 AND 2x2 giant trees -
 * dark oak, spruce, jungle, pale oak, etc) is supported automatically,
 * without hardcoding a Material -> TreeType map.
 */
public final class GrowListener implements Listener {
    private static final int FARMLAND_CROP = 0;
    private static final int NETHER_CROP = 1;
    private static final int SAPLING = 2;
    private static final int UNKNOWN = -1;

    private final ConfigManager config;
    private final PlayerFeatureState state;
    private final RegionGuard worldGuard;
    private final DebugLogger debug;
    private final Random random = new Random();

    public GrowListener(ConfigManager config, PlayerFeatureState state, RegionGuard worldGuard, DebugLogger debug) {
        this.config = config;
        this.state = state;
        this.worldGuard = worldGuard;
        this.debug = debug;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        boolean sneaking = player.isSneaking();
        boolean sneakMode = sneaking && state.isSneakGrow(player.getUniqueId());
        boolean moveMode = !sneaking && state.isMoveGrow(player.getUniqueId());

        if (!sneakMode && !moveMode) {
            return;
        }
        if (!hasFeaturePermission(player, sneakMode)) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        debug.log(player.getName() + (sneakMode
                ? " triggered Sneak Grow (moving while sneaking)."
                : " triggered Movement Grow."));
        runGrowth(player, to, sneakMode);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!state.isSneakGrow(player.getUniqueId())) {
            return;
        }
        if (!hasFeaturePermission(player, true)) {
            return;
        }
        debug.log(player.getName() + " triggered Sneak Grow (started sneaking).");
        runGrowth(player, player.getLocation(), true);
    }

    private boolean hasFeaturePermission(Player player, boolean sneakMode) {
        if (!player.hasPermission(Permissions.FEATURE_AUTOGROW)) {
            debug.log(player.getName() + " denied AutoGrow: missing " + Permissions.FEATURE_AUTOGROW);
            return false;
        }
        String specific = sneakMode ? Permissions.FEATURE_AUTOGROW_SNEAK : Permissions.FEATURE_AUTOGROW_MOVE;
        if (!player.hasPermission(specific)) {
            debug.log(player.getName() + " denied AutoGrow: missing " + specific);
            return false;
        }
        return true;
    }

    private void runGrowth(Player player, Location center, boolean allowSaplings) {
        if (!worldGuard.isAutoGrowAllowed(center)) {
            debug.log("Region denied AutoGrow for " + player.getName() + " at " + describe(center));
            return;
        }

        PlayerInventory inventory = player.getInventory();

        // Determine the maximum area needed
        int maxArea = Math.max(
                Math.max(config.getFarmlandGrowArea(), config.getNetherlandGrowArea()),
                4 // default minimum
        );

        AreaUtil.forEachInArea(center, maxArea, block -> {
            Material type = block.getType();
            int blockType = getBlockType(type);

            if (blockType == UNKNOWN) {
                return;
            }

            // Saplings only on Sneak Grow
            if (blockType == SAPLING) {
                if (!allowSaplings || !config.isSaplingsEnabled()) {
                    return;
                }
                tryGrowBlock(player, inventory, block, SAPLING);
                return;
            }

            // Farmland and Netherland crops on both modes
            tryGrowBlock(player, inventory, block, blockType);
        });
    }

    /**
     * Attempts to grow a single block based on its type.
     * Checks configuration, permissions, and requirements before growing.
     */
    private boolean tryGrowBlock(Player player, PlayerInventory inventory, Block block, int blockType) {
        if (random.nextInt(100) >= getGrowthChance(blockType)) {
            return false;
        }

        // Check enable flags
        if (blockType == FARMLAND_CROP && !config.isFarmlandsEnabled()) {
            return false;
        }
        if (blockType == NETHER_CROP && !config.isNetherlandEnabled()) {
            return false;
        }
        if (blockType == SAPLING && !config.isSaplingsEnabled()) {
            return false;
        }

        // Check every resource requirement independently (don't bail out on
        // the first failure) so, e.g., a missing hoe still gets reported even
        // when bone meal is ALSO missing - previously this returned early on
        // the first failed check, so the hoe warning could never fire at all
        // whenever bone meal was also missing.
        boolean requirementsMet = true;
        boolean titleShown = false; // only one on-screen title per call - see showWarningTitle's cooldown notes

        if (needsBoneMeal(blockType) && !hasBoneMeal(inventory)) {
            requirementsMet = false;
            if (!titleShown && canShowWarning(lastBoneMealWarning, player)) {
                showWarningTitle(player, config.getMessageNoBoneMeal());
                titleShown = true;
            }
        }

        if (needsHoe(blockType) && !holdsOrCarriesHoe(inventory)) {
            requirementsMet = false;
            if (!titleShown && canShowWarning(lastHoeWarning, player)) {
                showWarningTitle(player, config.getMessageNoHoe());
                titleShown = true;
            }
        }

        Map<Material, Integer> requiredCustomItems = null;
        if (needsCustomItems(blockType)) {
            requiredCustomItems = getCustomItems(blockType);
            if (!hasAllCustomItems(inventory, requiredCustomItems)) {
                requirementsMet = false;
                if (canShowWarning(lastCustomItemsWarning, player)) {
                    player.sendMessage(buildCustomItemsMessage(requiredCustomItems));
                }
            }
        }

        if (!requirementsMet) {
            return false;
        }

        // For saplings: check if this specific type is enabled
        if (blockType == SAPLING) {
            String saplingType = extractSaplingType(block.getType());
            if (saplingType != null && !config.isSaplingTypeEnabled(saplingType)) {
                return false;
            }
        }

        // Try to grow the block
        boolean grew;
        if (blockType == SAPLING) {
            grew = growSapling(block);
        } else {
            grew = growAgeable(block);
        }

        if (!grew) {
            return false;
        }

        // Consume resources
        if (needsBoneMeal(blockType)) {
            consumeOne(inventory, Material.BONE_MEAL);
        }
        if (needsHoe(blockType)) {
            damageHeldHoe(player, inventory);
        }
        if (requiredCustomItems != null) {
            consumeCustomItems(inventory, requiredCustomItems);
        }

        return true;
    }

    /**
     * Determines the type of block: farmland crop, nether crop, or sapling.
     */
    private int getBlockType(Material material) {
        // Check saplings first
        if (isSapling(material)) {
            return SAPLING;
        }
        // Check farmland crops
        if (config.isFarmlandCrop(material)) {
            return FARMLAND_CROP;
        }
        // Check nether crops
        if (config.isNetherlandCrop(material)) {
            return NETHER_CROP;
        }
        return UNKNOWN;
    }

    private int getGrowthChance(int blockType) {
        return switch (blockType) {
            case FARMLAND_CROP -> config.getFarmlandGrowChance();
            case NETHER_CROP -> config.getNetherlandGrowChance();
            case SAPLING -> config.isInstaSaplings() ? 100 : config.getSaplingGrowChance();
            default -> 0;
        };
    }

    private boolean needsBoneMeal(int blockType) {
        return switch (blockType) {
            case FARMLAND_CROP -> config.isFarmlandGrowBoneMealUse();
            case NETHER_CROP -> config.isNetherlandGrowBoneMealUse();
            case SAPLING -> config.isSaplingBoneMealUse();
            default -> false;
        };
    }

    private boolean needsHoe(int blockType) {
        return switch (blockType) {
            case FARMLAND_CROP -> config.isFarmlandGrowHoeUse();
            case NETHER_CROP -> config.isNetherlandGrowHoeUse();
            case SAPLING -> config.isSaplingHoeUse();
            default -> false;
        };
    }

    private boolean needsCustomItems(int blockType) {
        return switch (blockType) {
            case FARMLAND_CROP -> config.isFarmlandGrowCustomItemUse();
            case NETHER_CROP -> config.isNetherlandGrowCustomItemUse();
            case SAPLING -> config.isSaplingCustomItemUse();
            default -> false;
        };
    }

    private Map<Material, Integer> getCustomItems(int blockType) {
        return switch (blockType) {
            case FARMLAND_CROP -> config.getFarmlandGrowCustomItems();
            case NETHER_CROP -> config.getNetherlandGrowCustomItems();
            case SAPLING -> config.getSaplingCustomItems();
            default -> Map.of();
        };
    }

    private boolean hasAllCustomItems(PlayerInventory inventory, Map<Material, Integer> required) {
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (countItem(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Counts a material across the main storage contents AND the offhand
     * slot. {@code PlayerInventory#getContents()} does NOT include the
     * offhand - only main storage (which does include the main hand, since
     * that's hotbar slot 0-8) - so anything that needs an accurate total
     * must add the offhand in separately, or offhand-only stacks get missed.
     */
    private int countItem(PlayerInventory inventory, Material material) {
        int total = 0;
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            total += offHand.getAmount();
        }
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Builds the "you need X" chat message for a missing custom-item
     * requirement, listing every configured item and amount (not just the
     * ones actually missing - simpler to read, and matches what's in
     * config.yml's custom-item-list for this section).
     */
    private String buildCustomItemsMessage(Map<Material, Integer> required) {
        StringBuilder itemsList = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            if (!itemsList.isEmpty()) {
                itemsList.append(", ");
            }
            itemsList.append("x").append(entry.getValue()).append(' ').append(prettyMaterialName(entry.getKey()));
        }
        return config.getMessageNoCustomItems().replace("%items%", itemsList.toString());
    }

    private String prettyMaterialName(Material material) {
        String[] words = material.name().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private void consumeCustomItems(PlayerInventory inventory, Map<Material, Integer> toConsume) {
        for (Map.Entry<Material, Integer> entry : toConsume.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType() != entry.getKey()) {
                    continue;
                }
                int consumed = Math.min(stack.getAmount(), remaining);
                int newAmount = stack.getAmount() - consumed;
                inventory.setItem(slot, newAmount <= 0 ? null : withAmount(stack, newAmount));
                remaining -= consumed;
            }
            if (remaining > 0) {
                ItemStack offHand = inventory.getItemInOffHand();
                if (offHand != null && offHand.getType() == entry.getKey()) {
                    int consumed = Math.min(offHand.getAmount(), remaining);
                    int newAmount = offHand.getAmount() - consumed;
                    inventory.setItemInOffHand(newAmount <= 0 ? null : withAmount(offHand, newAmount));
                }
            }
        }
    }

    private String extractSaplingType(Material material) {
        String name = material.name();
        if (material == Material.MANGROVE_PROPAGULE) {
            return "MANGROVE";
        }
        if (name.endsWith("_SAPLING")) {
            return name.replace("_SAPLING", "");
        }
        return null;
    }

    private boolean isSapling(Material material) {
        return material.name().endsWith("_SAPLING") || material == Material.MANGROVE_PROPAGULE;
    }

    private boolean growSapling(Block block) {
        if (config.isInstaSaplings()) {
            // Vanilla bone-mealing a sapling has its OWN internal chance to
            // actually finish the tree per application - one call isn't
            // guaranteed to succeed even though our own growth-chance roll
            // already passed (that's why "insta-saplings: true" previously
            // still sometimes took 2+ sneaks: the outer roll passed
            // instantly, but the single applyBoneMeal() call underneath
            // still had to win vanilla's own dice roll). Keep applying until
            // it actually succeeds, bounded so a stubborn RNG streak can't
            // hang anything.
            for (int attempt = 0; attempt < 10; attempt++) {
                if (block.applyBoneMeal(BlockFace.UP)) {
                    debug.log("Sapling instantly grown into a tree at " + describe(block.getLocation()));
                    return true;
                }
            }
            return false;
        }

        boolean grew = block.applyBoneMeal(BlockFace.UP);
        if (grew) {
            debug.log("Sapling successfully grown into a tree at " + describe(block.getLocation()));
        }
        return grew;
    }

    private boolean growAgeable(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }
        int currentAge = ageable.getAge();
        int maxAge = ageable.getMaximumAge();
        if (currentAge >= maxAge) {
            return false;
        }

        boolean stageGrowing = switch (getBlockType(block.getType())) {
            case FARMLAND_CROP -> config.isFarmlandGrowStageGrowing();
            case NETHER_CROP -> config.isNetherlandGrowStageGrowing();
            default -> true;
        };

        ageable.setAge(stageGrowing ? currentAge + 1 : maxAge);
        block.setBlockData(ageable, true);
        debug.log("Crop advanced at " + describe(block.getLocation()));
        return true;
    }

    /**
     * True if the player has bone meal in main storage (which already
     * includes the main hand) OR the offhand specifically - {@code
     * Inventory#contains} does not check the offhand slot on its own.
     */
    private boolean hasBoneMeal(PlayerInventory inventory) {
        ItemStack offHand = inventory.getItemInOffHand();
        return (offHand != null && offHand.getType() == Material.BONE_MEAL) || inventory.contains(Material.BONE_MEAL);
    }

    private boolean holdsOrCarriesHoe(PlayerInventory inventory) {
        if (isHoe(inventory.getItemInMainHand()) || isHoe(inventory.getItemInOffHand())) {
            return true;
        }
        for (ItemStack stack : inventory.getContents()) {
            if (isHoe(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHoe(ItemStack itemStack) {
        return itemStack != null && itemStack.getType().name().endsWith("_HOE");
    }

    private void consumeOne(PlayerInventory inventory, Material material) {
        int slot = inventory.first(material);
        if (slot >= 0) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null) {
                int amount = stack.getAmount() - 1;
                inventory.setItem(slot, amount <= 0 ? null : withAmount(stack, amount));
            }
            return;
        }
        // Not found in main storage - check the offhand before giving up.
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            int amount = offHand.getAmount() - 1;
            inventory.setItemInOffHand(amount <= 0 ? null : withAmount(offHand, amount));
        }
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        stack.setAmount(amount);
        return stack;
    }

    private String describe(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    // --- Warning titles (no chat spam) ---
    // Separate cooldown trackers so a bone-meal warning doesn't suppress a
    // hoe warning (or vice versa) for the same player, and a separate one
    // for the custom-items chat message.
    private static final long WARNING_COOLDOWN_MS = 5000;
    private static final Map<UUID, Long> lastBoneMealWarning = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastHoeWarning = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastCustomItemsWarning = new ConcurrentHashMap<>();

    private boolean canShowWarning(Map<UUID, Long> tracker, Player player) {
        long now = System.currentTimeMillis();
        Long last = tracker.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= WARNING_COOLDOWN_MS) {
            tracker.put(player.getUniqueId(), now);
            return true;
        }
        return false;
    }

    /**
     * Shows a red warning built from a legacy-color-coded string already
     * produced by ConfigManager's message translation.
     *
     * The message is shown as the SUBTITLE, not the title: Minecraft titles
     * render at roughly 4x normal text size, so a full sentence like the
     * default bone-meal/hoe messages overflows past the edges of the screen
     * and gets cut off. The subtitle renders much smaller and comfortably
     * fits a full sentence. The title itself is left empty.
     */
    private void showWarningTitle(Player player, String legacyColoredMessage) {
        Component subtitleText = LegacyComponentSerializer.legacySection().deserialize(legacyColoredMessage);
        Title title = Title.title(
                Component.empty(),
                subtitleText,
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(3000), Duration.ofMillis(300))
        );
        player.showTitle(title);
    }

    // --- Hoe durability ---

    /**
     * Damages whichever hoe was used to satisfy the hoe requirement (main
     * hand first, otherwise the first hoe found anywhere in the inventory),
     * breaking it if it runs out of durability.
     *
     * Note: this runs once per crop successfully grown. With a large grow
     * area and a high growth chance, a single Sneak/Move Grow trigger can
     * advance many crops at once, so the held hoe can wear out much faster
     * than it would from normal vanilla farming. This is expected given the
     * feature, but worth keeping in mind when tuning area/growth-chance.
     */
    private static final int HAND_MAIN = -1;
    private static final int HAND_OFF = -2;

    private void damageHeldHoe(Player player, PlayerInventory inventory) {
        ItemStack mainHand = inventory.getItemInMainHand();
        if (isHoe(mainHand)) {
            applyHoeDamage(player, inventory, HAND_MAIN, mainHand);
            return;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (isHoe(offHand)) {
            applyHoeDamage(player, inventory, HAND_OFF, offHand);
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isHoe(contents[slot])) {
                applyHoeDamage(player, inventory, slot, contents[slot]);
                return;
            }
        }
    }

    private void applyHoeDamage(Player player, PlayerInventory inventory, int slot, ItemStack hoe) {
        ItemStack result = damageTool(hoe);
        if (result == null) {
            if (slot == HAND_MAIN) {
                inventory.setItemInMainHand(null);
            } else if (slot == HAND_OFF) {
                inventory.setItemInOffHand(null);
            } else {
                inventory.setItem(slot, null);
            }
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            debug.log(player.getName() + "'s hoe broke while growing crops.");
        } else if (slot == HAND_MAIN) {
            inventory.setItemInMainHand(result);
        } else if (slot == HAND_OFF) {
            inventory.setItemInOffHand(result);
        } else {
            inventory.setItem(slot, result);
        }
    }

    /**
     * Applies one point of durability damage to the given tool, respecting
     * unbreakable items and the Unbreaking enchantment (vanilla-style chance
     * to skip damage). Returns null if the tool breaks, or the (mutated)
     * stack otherwise.
     */
    private ItemStack damageTool(ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        if (meta == null || meta.isUnbreakable() || !(meta instanceof Damageable damageable)) {
            return tool;
        }

        int unbreakingLevel = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0 && random.nextInt(unbreakingLevel + 1) != 0) {
            return tool; // Unbreaking absorbed this use - no damage applied
        }

        int maxDurability = tool.getType().getMaxDurability();
        int newDamage = damageable.getDamage() + 1;

        if (maxDurability > 0 && newDamage >= maxDurability) {
            return null; // tool breaks
        }

        damageable.setDamage(newDamage);
        tool.setItemMeta(meta);
        return tool;
    }
}
