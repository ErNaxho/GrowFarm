package xyz.elnaxho.growfarm;

/**
 * Central registry of every permission node used by the plugin.
 * Keeping these as constants (instead of scattering raw strings) makes it
 * trivial to add new commands/features without hunting through the codebase,
 * and keeps plugin.yml and the code in sync.
 */
public final class Permissions {

    private Permissions() {
    }

    // --- Commands ---------------------------------------------------------
    public static final String COMMAND_GROW = "growfarm.command.grow";
    public static final String COMMAND_GROW_SNEAK = "growfarm.command.grow.sneak";
    public static final String COMMAND_GROW_MOVE = "growfarm.command.grow.move";
    public static final String COMMAND_PLANT = "growfarm.command.plant";
    public static final String COMMAND_RELOAD = "growfarm.command.reload";

    // --- Features -----------------------------------------------------------
    public static final String FEATURE_AUTOGROW = "growfarm.feature.autogrow";
    public static final String FEATURE_AUTOGROW_MOVE = "growfarm.feature.autogrow.move";
    public static final String FEATURE_AUTOGROW_SNEAK = "growfarm.feature.autogrow.sneak";
    public static final String FEATURE_AUTOPLANT = "growfarm.feature.autoplant";
}
