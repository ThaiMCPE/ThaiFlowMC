package dev.thaiflowmc.loader.error;

/**
 * Renders a {@link ModLoadException} as the short, readable block ThaiFlowMC
 * prints to the console. Full stack traces still go to the debug log; this
 * is only what a mod author sees by default.
 */
public final class FriendlyErrorFormatter {

    private FriendlyErrorFormatter() {
    }

    public static String format(ModLoadException e) {
        return "[ThaiFlowMC]\n\nCould not load mod:\n" + e.modName() + "\n\n" + e.friendlyDetail();
    }
}
