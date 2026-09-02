package xyz.elnaxho.growfarm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * /grow            -> toggles BOTH sneak and move grow together (see rules below)
 * /grow sneak       -> toggles Sneak Grow only
 * /grow move        -> toggles Move Grow only
 * /grow reload      -> reloads config.yml
 *
 * Combined toggle rule for bare "/grow":
 *   sneak=true,  move=true   -> both false
 *   sneak=false, move=false  -> both true
 *   sneak != move (mixed)    -> both false
 */
public final class GrowCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager config;
    private final PlayerFeatureState state;

    public GrowCommand(ConfigManager config, PlayerFeatureState state) {
        this.config = config;
        this.state = state;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessagePlayersOnly());
            return true;
        }

        if (args.length == 0) {
            return handleBoth(player);
        }

        return switch (args[0].toLowerCase()) {
            case "sneak" -> handleSneak(player);
            case "move" -> handleMove(player);
            case "reload" -> handleReload(sender);
            default -> {
                player.sendMessage("Usage: /grow [sneak|move|reload]");
                yield true;
            }
        };
    }

    private boolean handleBoth(Player player) {
        if (!player.hasPermission(Permissions.COMMAND_GROW)) {
            player.sendMessage(config.getMessageNoPermission());
            return true;
        }
        UUID id = player.getUniqueId();
        boolean sneak = state.isSneakGrow(id);
        boolean move = state.isMoveGrow(id);

        // Both on -> disable both. Both off -> enable both. Mixed -> disable both.
        boolean enableBoth;
        if (sneak && move) {
            enableBoth = false;
        } else if (!sneak && !move) {
            enableBoth = true;
        } else {
            enableBoth = false; // mixed state -> disable both
        }

        state.setSneakGrow(id, enableBoth);
        state.setMoveGrow(id, enableBoth);

        player.sendMessage(enableBoth ? config.getMessageGrowBothOn() : config.getMessageGrowBothOff());

        // Handle exclusive mode - only announce/disable AutoPlant if it was
        // actually on. Otherwise this message would fire every single time
        // AutoGrow gets turned on, regardless of AutoPlant's real state.
        if (enableBoth && config.isExclusiveMode() && state.isAutoPlant(id)) {
            state.setAutoPlant(id, false);
            player.sendMessage(config.getMessageAutoPlantDisabledByAutoGrow());
        }
        return true;
    }

    private boolean handleSneak(Player player) {
        if (!player.hasPermission(Permissions.COMMAND_GROW_SNEAK)) {
            player.sendMessage(config.getMessageNoPermission());
            return true;
        }
        UUID id = player.getUniqueId();
        boolean enabled = state.toggleSneakGrow(id);
        boolean moveEnabled = state.isMoveGrow(id);

        player.sendMessage(enabled ? config.getMessageGrowSneakOn() : config.getMessageGrowSneakOff());

        if ((enabled || moveEnabled) && config.isExclusiveMode() && state.isAutoPlant(id)) {
            state.setAutoPlant(id, false);
            player.sendMessage(config.getMessageAutoPlantDisabledByAutoGrow());
        }
        return true;
    }

    private boolean handleMove(Player player) {
        if (!player.hasPermission(Permissions.COMMAND_GROW_MOVE)) {
            player.sendMessage(config.getMessageNoPermission());
            return true;
        }
        UUID id = player.getUniqueId();
        boolean enabled = state.toggleMoveGrow(id);
        boolean sneakEnabled = state.isSneakGrow(id);

        player.sendMessage(enabled ? config.getMessageGrowMoveOn() : config.getMessageGrowMoveOff());

        if ((enabled || sneakEnabled) && config.isExclusiveMode() && state.isAutoPlant(id)) {
            state.setAutoPlant(id, false);
            player.sendMessage(config.getMessageAutoPlantDisabledByAutoGrow());
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.COMMAND_RELOAD)) {
            sender.sendMessage(config.getMessageNoPermission());
            return true;
        }
        config.loadConfig();
        sender.sendMessage(config.getMessageReloadSuccess());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        String prefix = args[0].toLowerCase();
        if (sender.hasPermission(Permissions.COMMAND_GROW_SNEAK) && "sneak".startsWith(prefix)) {
            completions.add("sneak");
        }
        if (sender.hasPermission(Permissions.COMMAND_GROW_MOVE) && "move".startsWith(prefix)) {
            completions.add("move");
        }
        if (sender.hasPermission(Permissions.COMMAND_RELOAD) && "reload".startsWith(prefix)) {
            completions.add("reload");
        }
        return completions;
    }
}
