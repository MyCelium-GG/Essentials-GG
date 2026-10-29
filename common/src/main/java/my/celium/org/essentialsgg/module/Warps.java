package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Server warps: {@code /warp [name]} (no name opens the menu),
 * {@code /setwarp <name> [restricted]} (the held item becomes its icon) and
 * {@code /delwarp <name>}. A restricted warp needs {@code essentialsgg.warp.<name>}.
 */
public final class Warps {
    private static final SuggestionProvider<CommandSourceStack> WARPS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        ServerPlayer player = context.getSource().getPlayer();
        for (Map.Entry<String, ServerData.Warp> warp : Store.server().warps.entrySet()) {
            if (warp.getKey().startsWith(remaining) && (player == null || mayUse(player, warp.getKey(), warp.getValue()))) {
                builder.suggest(warp.getKey());
            }
        }
        return builder.buildFuture();
    };

    private Warps() {
    }

    public static void commands() {
        Cmd.define("warp", Module.WARPS, "command.warp", 0, List.of("warps"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> menu(p))))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS)
                        .executes(b.run(Cmd.self((p, c) -> warp(p, StringArgumentType.getString(c, "name")))))));
        Cmd.define("setwarp", Module.WARPS, "command.setwarp", 2, List.of("createwarp"), (root, b) -> root
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(b.run(Cmd.self((p, c) -> set(p, StringArgumentType.getString(c, "name"), false))))
                        .then(Commands.literal("restricted")
                                .executes(b.run(Cmd.self((p, c) -> set(p, StringArgumentType.getString(c, "name"), true)))))));
        Cmd.define("delwarp", Module.WARPS, "command.delwarp", 2, List.of("deletewarp", "remwarp"), (root, b) -> root
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS)
                        .executes(b.run(c -> delete(c.getSource(), StringArgumentType.getString(c, "name"))))));
    }

    static boolean mayUse(ServerPlayer player, String name, ServerData.Warp warp) {
        return !warp.restricted || Perms.has(player, Perms.ROOT + "warp." + name, 2);
    }

    private static int warp(ServerPlayer player, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        ServerData.Warp warp = Store.server().warps.get(name);
        if (warp == null || !mayUse(player, name, warp)) {
            Lang.send(player, "warp.unknown", "warp", typed);
            return 0;
        }
        Teleports.go(player, () -> warp.loc, p -> Lang.send(p, "warp.arrived", "warp", name));
        return 1;
    }

    private static int set(ServerPlayer player, String typed, boolean restricted) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Lang.send(player, "warp.bad_name");
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        String icon = held.isEmpty() ? "minecraft:ender_pearl" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        boolean moved = Store.server().warps.containsKey(name);
        ServerData.Warp warp = new ServerData.Warp(Loc.of(player), icon);
        warp.restricted = restricted;
        Store.server().warps.put(name, warp);
        Store.markDirty();
        Lang.send(player, moved ? "warp.moved" : "warp.set", "warp", name);
        return 1;
    }

    private static int delete(CommandSourceStack source, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (Store.server().warps.remove(name) == null) {
            Lang.send(source, "warp.unknown", "warp", typed);
            return 0;
        }
        Store.markDirty();
        Lang.send(source, "warp.deleted", "warp", name);
        return 1;
    }

    private static int menu(ServerPlayer player) {
        List<Gui.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ServerData.Warp> entry : Store.server().warps.entrySet()) {
            String name = entry.getKey();
            ServerData.Warp warp = entry.getValue();
            if (!mayUse(player, name, warp)) {
                continue;
            }
            Loc loc = warp.loc;
            List<Component> lore = List.of(
                    Lang.get(player, "menu.location", "x", loc.blockX(), "y", loc.blockY(), "z", loc.blockZ(),
                            "world", loc.dimName()),
                    Lang.get(player, "menu.click_teleport"));
            entries.add(new Gui.Entry(Gui.icon(Gui.item(warp.icon, Items.ENDER_PEARL),
                    Lang.get(player, "menu.warp_name", "warp", name), lore), click -> {
                        click.player().closeContainer();
                        warp(click.player(), name);
                    }));
        }
        if (entries.isEmpty()) {
            Lang.send(player, "warp.none");
            return 0;
        }
        Gui.paged(player, Lang.get(player, "menu.warps_title"), entries, 0,
                Lang.get(player, "menu.previous"), Lang.get(player, "menu.next"), null);
        return 1;
    }
}
