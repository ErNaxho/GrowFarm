package xyz.elnaxho.growfarm;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /plant - toggles AutoPlant for the sender. */
public final class PlantCommand implements CommandExecutor {
    private final ConfigManager config;
    private final PlayerFeatureState state;

    public PlantCommand(ConfigManager config, PlayerFeatureState state) {
        this.config = config;
        this.state = state;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessagePlayersOnly());
            return true;
        }
        if (!player.hasPermission(Permissions.COMMAND_PLANT)) {
            player.sendMessage(config.getMessageNoPermission());
            return true;
        }
        
        UUID id = player.getUniqueId();
        boolean enabled = state.toggleAutoPlant(id);

        player.sendMessage(enabled ? config.getMessagePlantOn() : config.getMessagePlantOff());

        // Only announce/disable AutoGrow if it was actually on - otherwise
        // this fired every time AutoPlant was enabled, regardless of
        // AutoGrow's real state.
        boolean autoGrowWasOn = state.isSneakGrow(id) || state.isMoveGrow(id);
        if (enabled && config.isExclusiveMode() && autoGrowWasOn) {
            state.setSneakGrow(id, false);
            state.setMoveGrow(id, false);
            player.sendMessage(config.getMessageAutoGrowDisabledByAutoPlant());
        }
        return true;
    }
}
