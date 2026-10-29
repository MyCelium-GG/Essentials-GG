package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.gui.Prompt;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kits edited in menus. {@code /kitedit} lists every kit (plus a button to
 * create one), {@code /kitedit <name>} opens one kit:
 *
 * <ul>
 *   <li><b>Contents</b>: a chest holding the kit's items. Whatever is in it
 *       when closed is the kit; items taken out stay with the editor.</li>
 *   <li><b>Cooldown</b>: presets from none to 30 days, once per player, or
 *       any duration typed in chat.</li>
 *   <li><b>Restricted</b> and <b>first join</b> toggles, <b>icon</b> from the
 *       held item, <b>rename</b> (typed in chat), <b>preview</b>, <b>delete</b>
 *       with a confirmation.</li>
 * </ul>
 *
 * The subcommands ({@code /kitedit <name> cooldown 1d}, ...) still work for
 * scripts and the console.
 */
public final class KitEditor {
    /** Cooldown presets in seconds; -1 is once per player. */
    private static final long[] PRESETS = {0, 300, 1800, 3600, 21_600, 43_200, 86_400, 604_800, 2_592_000, -1};
    private static final String[] PRESET_ICONS = {"minecraft:lime_wool", "minecraft:white_wool", "minecraft:white_wool",
        "minecraft:yellow_wool", "minecraft:yellow_wool", "minecraft:orange_wool", "minecraft:orange_wool",
        "minecraft:red_wool", "minecraft:red_wool", "minecraft:black_wool"};

    private KitEditor() {
    }

    public static void commands() {
        Cmd.define("kitedit", Module.KITS, "command.kitedit", 3, List.of("editkit"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    list(p);
                    return 1;
                })))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(Kits.KITS)
                        .executes(b.run(Cmd.self((p, c) -> open(p, StringArgumentType.getString(c, "name")))))
                        .then(Commands.literal("cooldown").then(Commands.argument("time", StringArgumentType.word())
                                .executes(b.run(c -> Kits.edit(c, "cooldown", StringArgumentType.getString(c, "time"))))))
                        .then(Commands.literal("restricted").then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(b.run(c -> Kits.edit(c, "restricted", String.valueOf(BoolArgumentType.getBool(c, "on")))))))
                        .then(Commands.literal("firstjoin").then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(b.run(c -> Kits.edit(c, "firstjoin", String.valueOf(BoolArgumentType.getBool(c, "on")))))))
                        .then(Commands.literal("icon").executes(b.run(c -> Kits.edit(c, "icon", ""))))
                        .then(Commands.literal("items").executes(b.run(c -> Kits.edit(c, "items", ""))))));
    }

    private static int open(ServerPlayer player, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (!Store.server().kits.containsKey(name)) {
            Lang.send(player, "kit.unknown", "kit", typed);
            return 0;
        }
        editor(player, name);
        return 1;
    }

    /** "none", "once" or a duration, in the player's language. */
    static String cooldownText(ServerPlayer player, long seconds) {
        if (seconds == 0) {
            return Lang.raw(player, "kited.cooldown_none");
        }
        if (seconds < 0) {
            return Lang.raw(player, "kited.cooldown_once");
        }
        return Time.format(player, seconds * 1000);
    }

    private static Component onOff(ServerPlayer player, boolean on) {
        return Lang.get(player, on ? "kited.on" : "kited.off");
    }

    // ---------------------------------------------------------------- kit list

    static void list(ServerPlayer player) {
        List<Gui.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ServerData.Kit> entry : Store.server().kits.entrySet()) {
            String name = entry.getKey();
            ServerData.Kit kit = entry.getValue();
            entries.add(new Gui.Entry(Gui.icon(Gui.item(kit.icon, Items.CHEST), Lang.get(player, "menu.kit_name", "kit", name),
                    summary(player, kit, true)), click -> editor(click.player(), name)));
        }
        Gui.Entry create = new Gui.Entry(Gui.icon(Items.NETHER_STAR, Lang.get(player, "kited.create"),
                List.of(Lang.get(player, "kited.create_hint"))), click -> askName(click.player(), null));
        Gui.paged(player, Lang.get(player, "kited.list_title"), entries, 0,
                Lang.get(player, "menu.previous"), Lang.get(player, "menu.next"), create);
    }

    private static List<Component> summary(ServerPlayer player, ServerData.Kit kit, boolean hint) {
        List<Component> lore = new ArrayList<>();
        lore.add(Lang.get(player, "menu.kit_items", "count", kit.items.size()));
        lore.add(Lang.get(player, "kited.line_cooldown", "value", cooldownText(player, kit.cooldown)));
        lore.add(Lang.get(player, "kited.line_restricted", "value", onOff(player, kit.restricted)));
        lore.add(Lang.get(player, "kited.line_firstjoin", "value", onOff(player, kit.firstJoin)));
        if (hint) {
            lore.add(Lang.get(player, "kited.click_edit"));
        }
        return lore;
    }

    /** Asks for a kit name in chat: a new kit ({@code from} null) or a new name for {@code from}. */
    private static void askName(ServerPlayer player, String from) {
        Prompt.ask(player, from == null ? "kited.ask_name" : "kited.ask_rename", typed -> {
            String name = typed.toLowerCase(Locale.ROOT);
            Map<String, ServerData.Kit> kits = Store.server().kits;
            if (!name.matches("[a-z0-9_-]{1,32}")) {
                Lang.send(player, "kit.bad_name");
            } else if (kits.containsKey(name)) {
                Lang.send(player, "kited.name_taken", "kit", name);
            } else if (from == null) {
                ServerData.Kit kit = new ServerData.Kit();
                kit.icon = "minecraft:chest";
                kits.put(name, kit);
                Store.markDirty();
                Lang.send(player, "kited.created", "kit", name);
                contents(player, name);
                return;
            } else if (kits.containsKey(from)) {
                kits.put(name, kits.remove(from));
                Store.markDirty();
                Kits.reloadPermissions();
                Lang.send(player, "kited.renamed", "from", from, "kit", name);
                editor(player, name);
                return;
            }
            // Bad answer: back to where they came from, so they can try again.
            if (from == null) {
                list(player);
            } else {
                editor(player, from);
            }
        }, "kit", from == null ? "" : from);
    }

    // ---------------------------------------------------------------- one kit

    static void editor(ServerPlayer player, String name) {
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null) {
            list(player);
            return;
        }
        Gui.Builder menu = Gui.builder(5, Lang.get(player, "kited.title", "kit", name));
        menu.item(4, Gui.icon(Gui.item(kit.icon, Items.CHEST), Lang.get(player, "menu.kit_name", "kit", name),
                summary(player, kit, false)));
        menu.button(20, Gui.icon(Items.CHEST, Lang.get(player, "kited.contents"),
                List.of(Lang.get(player, "menu.kit_items", "count", kit.items.size()), Lang.get(player, "kited.contents_hint"))),
                click -> contents(click.player(), name));
        menu.button(22, Gui.icon(Items.CLOCK, Lang.get(player, "kited.cooldown"),
                List.of(Lang.get(player, "kited.line_cooldown", "value", cooldownText(player, kit.cooldown)),
                        Lang.get(player, "kited.click_change"))),
                click -> cooldown(click.player(), name));
        menu.button(24, Gui.icon(Items.SPYGLASS, Lang.get(player, "kited.preview"), List.of(Lang.get(player, "kited.preview_hint"))),
                click -> Kits.preview(click.player(), name, kit, back -> editor(back, name)));
        menu.button(29, Gui.icon(Gui.item(kit.restricted ? "minecraft:lime_dye" : "minecraft:gray_dye", Items.LEAD),
                Lang.get(player, "kited.restricted"),
                List.of(Lang.get(player, "kited.line_restricted", "value", onOff(player, kit.restricted)),
                        Lang.get(player, "kited.restricted_hint", "node", Perms.ROOT + "kit." + name),
                        Lang.get(player, "kited.click_toggle"))),
                click -> {
                    kit.restricted = !kit.restricted;
                    Store.markDirty();
                    Kits.reloadPermissions();
                    editor(click.player(), name);
                });
        menu.button(31, Gui.icon(Gui.item(kit.firstJoin ? "minecraft:lime_dye" : "minecraft:gray_dye", Items.LEAD),
                Lang.get(player, "kited.firstjoin"),
                List.of(Lang.get(player, "kited.line_firstjoin", "value", onOff(player, kit.firstJoin)),
                        Lang.get(player, "kited.firstjoin_hint"), Lang.get(player, "kited.click_toggle"))),
                click -> {
                    kit.firstJoin = !kit.firstJoin;
                    Store.markDirty();
                    editor(click.player(), name);
                });
        menu.button(33, Gui.icon(Items.ITEM_FRAME, Lang.get(player, "kited.icon"), List.of(Lang.get(player, "kited.icon_hint"))),
                click -> {
                    ItemStack held = click.player().getMainHandItem();
                    if (held.isEmpty()) {
                        Lang.send(click.player(), "kit.icon_hand");
                        return;
                    }
                    kit.icon = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                    Store.markDirty();
                    editor(click.player(), name);
                });
        menu.button(38, Gui.icon(Items.NAME_TAG, Lang.get(player, "kited.rename"), List.of(Lang.get(player, "kited.rename_hint"))),
                click -> askName(click.player(), name));
        menu.button(40, Gui.icon(Items.ARROW, Lang.get(player, "menu.back"), List.of()), click -> list(click.player()));
        menu.button(42, Gui.icon(Items.BARRIER, Lang.get(player, "kited.delete"), List.of(Lang.get(player, "kited.delete_hint"))),
                click -> confirmDelete(click.player(), name));
        menu.fill().open(player);
    }

    private static void cooldown(ServerPlayer player, String name) {
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null) {
            list(player);
            return;
        }
        Gui.Builder menu = Gui.builder(4, Lang.get(player, "kited.cooldown_title", "kit", name));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        for (int i = 0; i < PRESETS.length; i++) {
            long seconds = PRESETS[i];
            List<Component> lore = new ArrayList<>();
            if (kit.cooldown == seconds) {
                lore.add(Lang.get(player, "kited.selected"));
            }
            lore.add(Lang.get(player, "kited.click_choose"));
            menu.button(slots[i], Gui.icon(Gui.item(PRESET_ICONS[i], Items.PAPER),
                    Lang.get(player, "kited.preset", "value", cooldownText(player, seconds)), lore), click -> {
                        kit.cooldown = seconds;
                        Store.markDirty();
                        Lang.send(click.player(), "kited.cooldown_set", "kit", name, "value", cooldownText(click.player(), seconds));
                        editor(click.player(), name);
                    });
        }
        menu.button(23, Gui.icon(Items.NAME_TAG, Lang.get(player, "kited.custom"), List.of(Lang.get(player, "kited.custom_hint"))),
                click -> Prompt.ask(click.player(), "kited.ask_cooldown", typed -> {
                    long seconds = Kits.cooldown(typed);
                    if (seconds == Long.MIN_VALUE) {
                        Lang.send(click.player(), "error.bad_time", "time", typed);
                    } else if (Store.server().kits.containsKey(name)) {
                        kit.cooldown = seconds;
                        Store.markDirty();
                        Lang.send(click.player(), "kited.cooldown_set", "kit", name, "value", cooldownText(click.player(), seconds));
                    }
                    editor(click.player(), name);
                }));
        menu.button(31, Gui.icon(Items.ARROW, Lang.get(player, "menu.back"), List.of()), click -> editor(click.player(), name));
        menu.fill().open(player);
    }

    private static void confirmDelete(ServerPlayer player, String name) {
        Gui.Builder menu = Gui.builder(3, Lang.get(player, "kited.delete_title", "kit", name));
        menu.button(11, Gui.icon(Gui.item("minecraft:red_wool", Items.BARRIER), Lang.get(player, "kited.delete_confirm", "kit", name),
                List.of(Lang.get(player, "kited.delete_warning"))), click -> {
                    if (Store.server().kits.remove(name) != null) {
                        Store.markDirty();
                        Lang.send(click.player(), "kit.deleted", "kit", name);
                    }
                    list(click.player());
                });
        menu.button(15, Gui.icon(Gui.item("minecraft:lime_wool", Items.ARROW), Lang.get(player, "kited.delete_cancel"), List.of()),
                click -> editor(click.player(), name));
        menu.fill().open(player);
    }

    // ---------------------------------------------------------------- contents

    /** A real chest with the kit's items; closing it saves what is inside. */
    private static void contents(ServerPlayer player, String name) {
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null) {
            list(player);
            return;
        }
        SimpleContainer container = new SimpleContainer(54);
        List<ItemStack> items = Kits.decode(player.level().getServer(), kit.items);
        for (int i = 0; i < items.size() && i < 54; i++) {
            container.setItem(i, items.get(i));
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Contents(id, inventory, container, name),
                Lang.get(player, "kited.contents_title", "kit", name)));
    }

    private static final class Contents extends ChestMenu {
        private final SimpleContainer container;
        private final String name;
        private boolean saved;

        Contents(int id, Inventory inventory, SimpleContainer container, String name) {
            super(MenuType.GENERIC_9x6, id, inventory, container, 6);
            this.container = container;
            this.name = name;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            ServerData.Kit kit = Store.server().kits.get(name);
            if (saved || kit == null || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            saved = true;
            List<JsonElement> items = Kits.encode(serverPlayer.level().getServer(), container);
            kit.items = items;
            Store.markDirty();
            Lang.send(serverPlayer, "kited.saved", "kit", name, "count", items.size());
        }
    }
}
