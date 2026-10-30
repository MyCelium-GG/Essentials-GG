package my.celium.org.essentialsgg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Prompt;
import my.celium.org.essentialsgg.module.Admin;
import my.celium.org.essentialsgg.module.Afk;
import my.celium.org.essentialsgg.module.Chat;
import my.celium.org.essentialsgg.module.Discord;
import my.celium.org.essentialsgg.module.Economy;
import my.celium.org.essentialsgg.module.Homes;
import my.celium.org.essentialsgg.module.Hub;
import my.celium.org.essentialsgg.module.Info;
import my.celium.org.essentialsgg.module.Kits;
import my.celium.org.essentialsgg.module.Messaging;
import my.celium.org.essentialsgg.module.Moderation;
import my.celium.org.essentialsgg.module.KitEditor;
import my.celium.org.essentialsgg.module.Nicknames;
import my.celium.org.essentialsgg.module.Permissions;
import my.celium.org.essentialsgg.module.Rtp;
import my.celium.org.essentialsgg.module.Sidebar;
import my.celium.org.essentialsgg.module.Spawn;
import my.celium.org.essentialsgg.module.Teleports;
import my.celium.org.essentialsgg.module.Utility;
import my.celium.org.essentialsgg.module.Warps;
import my.celium.org.essentialsgg.module.Workbenches;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Fmt;
import my.celium.org.essentialsgg.text.Lang;
import my.celium.org.event.MycelEvents;
import my.celium.org.event.ServerLifecycleEvent;
import my.celium.org.metadata.ModMetadata;
import my.celium.org.schedule.MycelScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Essentials GG: the server essentials, without the bloat.
 *
 * <p>Server-side only, vanilla clients join as usual. Each feature lives in a
 * {@link Module} under {@code module/} and can be switched off in
 * {@code config/essentialsgg.json}. Messages are in
 * {@code config/essentialsgg/lang/}, per-command settings in
 * {@code config/essentialsgg/commands.json}, world data in
 * {@code <world>/data/essentialsgg/}. Each loader forwards its server tick,
 * command registration, chat and deaths here and plugs its permission API
 * into {@link Perms}.
 */
public final class EssentialsGG {
    public static final String ID = "essentialsgg";

    public static final ModMetadata META = ModMetadata.builder(ID, "Essentials GG", "1.0.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/essentials-gg")
            .updateUrl("https://wiki.mycelium-gg.org/updates/essentialsgg.json")
            .build();

    public static final String BYPASS_COOLDOWN = Perms.declare(Perms.ROOT + "bypass.cooldown", 2);
    public static final String BYPASS_WARMUP = Perms.declare(Perms.ROOT + "bypass.warmup", 2);

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<String> themeText;
    public static ConfigValue<String> themeValue;
    public static ConfigValue<String> themeOk;
    public static ConfigValue<String> themeError;
    public static ConfigValue<String> themeWarn;
    public static ConfigValue<String> themeMuted;
    public static ConfigValue<String> themeAccent;
    public static ConfigValue<String> themeBrand;
    public static MycelConfig config;

    private static MinecraftServer server;

    private EssentialsGG() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        // 2: values turned red and error text softer; only untouched colors move along.
        builder.schemaVersion(2).migrate(1, json -> {
            if (json.get("theme") instanceof JsonObject theme) {
                replaceColor(theme, "value", "#D2A8FF", "#FF5555");
                replaceColor(theme, "error", "#EF5350", "#FF8A80");
            }
        });
        enabled = builder.booleanValue("enabled", true, "Master switch for Essentials GG.");
        Module.define(builder);
        builder.category("theme");
        themeText = builder.stringValue("text", "#E6E6E6", 7, "<text> in messages: body text.");
        themeValue = builder.stringValue("value", "#FF5555", 7, "<val>: names, numbers, places.");
        themeOk = builder.stringValue("ok", "#7EE787", 7, "<ok>: on, ready and other good news.");
        themeError = builder.stringValue("error", "#FF8A80", 7, "<err>: error messages.");
        themeWarn = builder.stringValue("warn", "#FFA657", 7, "<warn>: warnings.");
        themeMuted = builder.stringValue("muted", "#8B949E", 7, "<muted>: brackets, hints, separators.");
        themeAccent = builder.stringValue("accent", "#79B8FF", 7, "<accent>: buttons and links.");
        themeBrand = builder.stringValue("brand", "#EF5350", 7, "<brand>: the prefix.");
        builder.root();
        Teleports.config(builder);
        Homes.config(builder);
        Spawn.config(builder);
        Rtp.config(builder);
        Economy.config(builder);
        Chat.config(builder);
        Nicknames.config(builder);
        Afk.config(builder);
        Info.config(builder);
        Sidebar.config(builder);
        Discord.config(builder);
        config = builder.build();

        Admin.commands();
        Teleports.commands();
        Homes.commands();
        Warps.commands();
        Spawn.commands();
        Rtp.commands();
        Kits.commands();
        Economy.commands();
        Moderation.commands();
        Chat.commands();
        Messaging.commands();
        Nicknames.commands();
        Afk.commands();
        Utility.commands();
        Workbenches.commands();
        Info.commands();
        Sidebar.commands();
        Permissions.commands();
        KitEditor.commands();
        Hub.commands();

        reload();

        MycelEvents.onServerLifecycle(event -> {
            if (event.phase() == ServerLifecycleEvent.Phase.STARTED) {
                serverStarting(event.server());
                MycelScheduler.runEvery(20L * 60, Store::saveIfDirty);
            } else {
                Sidebar.clear();
                Teleports.clear();
                Store.close();
                server = null;
            }
        });
        MycelEvents.onPlayerJoin(event -> {
            if (isModEnabled()) {
                Players.join(event.player());
            }
        });
        MycelEvents.onPlayerLeave(event -> {
            if (isModEnabled()) {
                Players.leave(event.player());
            }
        });
    }

    /** Re-reads the config, theme, languages and command settings. Command changes need a restart. */
    public static void reload() {
        config.reload();
        Map<String, Integer> theme = new HashMap<>();
        putTheme(theme, "text", themeText);
        putTheme(theme, "val", themeValue);
        putTheme(theme, "ok", themeOk);
        putTheme(theme, "err", themeError);
        putTheme(theme, "warn", themeWarn);
        putTheme(theme, "muted", themeMuted);
        putTheme(theme, "accent", themeAccent);
        putTheme(theme, "brand", themeBrand);
        Fmt.setTheme(theme);
        Lang.load(Mycel.getConfigDir());
        Cmd.loadSettings(Mycel.getConfigDir());
        Kits.reloadPermissions();
    }

    private static void replaceColor(JsonObject theme, String key, String from, String to) {
        if (theme.has(key) && from.equalsIgnoreCase(theme.get(key).getAsString())) {
            theme.addProperty(key, to);
        }
    }

    private static void putTheme(Map<String, Integer> theme, String name, ConfigValue<String> value) {
        Integer rgb = Fmt.color(value.get());
        theme.put(name, rgb != null ? rgb : Fmt.color(value.getDefault()));
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }

    /** The running server, or {@code null} between worlds. */
    public static MinecraftServer server() {
        return server;
    }

    /**
     * Loads the world's data. Loaders call this as early as they can (before
     * permission nodes are gathered, so per-kit nodes exist); the Mycel
     * "started" event calls it again, which is then a no-op.
     */
    public static void serverStarting(MinecraftServer mc) {
        if (server != mc) {
            server = mc;
            Store.load(mc);
            Kits.reloadPermissions();
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (isModEnabled()) {
            Cmd.register(dispatcher);
        }
    }

    public static void tickServer(MinecraftServer mc) {
        if (mc == null || !isModEnabled() || server == null) {
            return;
        }
        Teleports.tick(mc);
        Afk.tick(mc);
        Moderation.tick(mc);
        Rtp.tick(mc);
        Utility.tick(mc);
        if (mc.getTickCount() % 20 == 0) {
            Sidebar.tick(mc);
        }
    }

    /**
     * Chat hook: {@code true} means Essentials GG handled the message (sent it
     * formatted, or refused it) and the loader must cancel the vanilla one.
     */
    public static boolean onChat(ServerPlayer player, String raw) {
        if (!isModEnabled() || server == null) {
            return false;
        }
        Afk.activity(player);
        if (Prompt.answer(player, raw)) {
            return true;
        }
        return Chat.onChat(player, raw);
    }

    public static void onDeath(LivingEntity entity) {
        if (isModEnabled() && server != null && entity instanceof ServerPlayer player) {
            Teleports.onDeath(player);
            Discord.onDeath(player);
        }
    }

    /** Fixed permission nodes with their default level, for loaders that need nodes registered up front. */
    public static List<Map.Entry<String, Integer>> permissionNodes() {
        return new ArrayList<>(Perms.declared().entrySet());
    }
}
