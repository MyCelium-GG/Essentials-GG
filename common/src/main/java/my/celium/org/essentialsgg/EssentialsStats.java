package my.celium.org.essentialsgg;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.stats.ModStats;

/**
 * Anonymous stats for the FastStats project {@code essentials-gg}, sent through Mycel under its
 * consent ({@code shareStats} on servers): which modules are on, which commands are turned off
 * and which get used, and whether the chat theme was customized.
 */
public final class EssentialsStats {
    private static final String TOKEN = "59cf5f84dd45ea2bc0807fcbc58da8cc";

    private static volatile ModStats stats;

    private EssentialsStats() {
    }

    static void start() {
        stats = ModStats.builder(EssentialsGG.META, TOKEN)
                .bool("enabled", () -> EssentialsGG.enabled.get())
                .stringArray("modules_on", EssentialsStats::modulesOn)
                .stringArray("commands_off", EssentialsStats::commandsOff)
                .bool("custom_theme", EssentialsStats::customTheme)
                .counters("commands")
                .errors("my.celium.org.essentialsgg.")
                .start();
    }

    /** A command ran successfully. */
    public static void command(String name) {
        ModStats current = stats;
        if (current != null) {
            current.count("commands", name);
        }
    }

    private static String[] modulesOn() {
        List<String> on = new ArrayList<>();
        for (Module module : Module.values()) {
            if (module.enabled()) {
                on.add(module.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return on.toArray(String[]::new);
    }

    private static String[] commandsOff() {
        List<String> off = new ArrayList<>();
        for (Cmd.Def def : Cmd.all()) {
            if (def.module().enabled() && !Cmd.isEnabled(def.name())) {
                off.add(def.name());
            }
        }
        return off.isEmpty() ? null : off.toArray(String[]::new);
    }

    private static boolean customTheme() {
        return !EssentialsGG.themeText.isDefault() || !EssentialsGG.themeValue.isDefault() || !EssentialsGG.themeOk.isDefault()
                || !EssentialsGG.themeError.isDefault() || !EssentialsGG.themeWarn.isDefault()
                || !EssentialsGG.themeMuted.isDefault() || !EssentialsGG.themeAccent.isDefault()
                || !EssentialsGG.themeBrand.isDefault();
    }
}
