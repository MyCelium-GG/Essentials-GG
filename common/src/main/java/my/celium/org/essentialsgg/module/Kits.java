package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.serialization.JsonOps;

import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import my.celium.org.util.PlayerUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kits: {@code /kit} (a menu, or {@code /kit <name>}), {@code /createkit <name>}
 * from your inventory, {@code /delkit}; {@link KitEditor} edits them in menus. Items are stored with their full components,
 * so enchantments and names survive. A restricted kit needs
 * {@code essentialsgg.kit.<name>}.
 */
public final class Kits {
    private static final String OTHERS = Perms.declare(Perms.ROOT + "kit.others", 2);

    static final SuggestionProvider<CommandSourceStack> KITS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        ServerPlayer player = context.getSource().getPlayer();
        for (Map.Entry<String, ServerData.Kit> kit : Store.server().kits.entrySet()) {
            if (kit.getKey().startsWith(remaining) && (player == null || mayUse(player, kit.getKey(), kit.getValue()))) {
                builder.suggest(kit.getKey());
            }
        }
        return builder.buildFuture();
    };

    private Kits() {
    }

    public static void commands() {
        Cmd.define("kit", Module.KITS, "command.kit", 0, List.of("kits"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> menu(p))))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                        .executes(b.run(Cmd.self((p, c) -> claim(p, StringArgumentType.getString(c, "name"), false))))
                        .then(Args.player("player").requires(s -> Perms.has(s, OTHERS))
                                .executes(b.run(Kits::giveOther)))));
        Cmd.define("createkit", Module.KITS, "command.createkit", 3, List.of("setkit"), (root, b) -> root
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(b.run(Cmd.self((p, c) -> create(p, StringArgumentType.getString(c, "name"), "0"))))
                        .then(Commands.argument("cooldown", StringArgumentType.word())
                                .executes(b.run(Cmd.self((p, c) -> create(p, StringArgumentType.getString(c, "name"),
                                        StringArgumentType.getString(c, "cooldown"))))))));
        Cmd.define("delkit", Module.KITS, "command.delkit", 3, List.of("deletekit"), (root, b) -> root
                .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS)
                        .executes(b.run(c -> delete(c.getSource(), StringArgumentType.getString(c, "name"))))));
    }

    /** Declares a node per restricted kit and warp, so permission APIs that need nodes up front know them. */
    public static void reloadPermissions() {
        Store.server().kits.forEach((name, kit) -> {
            if (kit.restricted) {
                Perms.declare(Perms.ROOT + "kit." + name, 2);
            }
        });
        Store.server().warps.forEach((name, warp) -> {
            if (warp.restricted) {
                Perms.declare(Perms.ROOT + "warp." + name, 2);
            }
        });
    }

    static boolean mayUse(ServerPlayer player, String name, ServerData.Kit kit) {
        return !kit.restricted || Perms.has(player, Perms.ROOT + "kit." + name, 2);
    }

    /** Millis until {@code player} may claim the kit again; 0 when ready, -1 when it was a one-time kit already claimed. */
    static long wait(ServerPlayer player, String name, ServerData.Kit kit) {
        Long last = Store.of(player).kits.get(name);
        if (last == null || Perms.has(player, EssentialsGG.BYPASS_COOLDOWN)) {
            return 0;
        }
        if (kit.cooldown < 0) {
            return -1;
        }
        return Math.max(0, last + kit.cooldown * 1000L - System.currentTimeMillis());
    }

    private static int claim(ServerPlayer player, String typed, boolean free) {
        String name = typed.toLowerCase(Locale.ROOT);
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null || !mayUse(player, name, kit)) {
            Lang.send(player, "kit.unknown", "kit", typed);
            return 0;
        }
        if (!free) {
            long wait = wait(player, name, kit);
            if (wait < 0) {
                Lang.send(player, "kit.once", "kit", name);
                return 0;
            }
            if (wait > 0) {
                Lang.send(player, "kit.cooldown", "kit", name, "time", Time.format(player, wait));
                return 0;
            }
            Store.of(player).kits.put(name, System.currentTimeMillis());
            Store.markDirty();
        }
        give(player, kit);
        Lang.send(player, "kit.claimed", "kit", name);
        return 1;
    }

    private static int giveOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = Args.player(context, "player");
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null) {
            Lang.send(context.getSource(), "kit.unknown", "kit", name);
            return 0;
        }
        give(target, kit);
        Lang.send(target, "kit.claimed", "kit", name);
        Lang.send(context.getSource(), "kit.given", "kit", name, "player", Nicknames.name(target));
        return 1;
    }

    private static void give(ServerPlayer player, ServerData.Kit kit) {
        for (ItemStack stack : decode(player.level().getServer(), kit.items)) {
            PlayerUtil.giveItem(player, stack);
        }
    }

    private static int create(ServerPlayer player, String typed, String cooldownText) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Lang.send(player, "kit.bad_name");
            return 0;
        }
        long cooldown = cooldown(cooldownText);
        if (cooldown == Long.MIN_VALUE) {
            Lang.send(player, "error.bad_time", "time", cooldownText);
            return 0;
        }
        List<JsonElement> items = encode(player.level().getServer(), player.getInventory());
        if (items.isEmpty()) {
            Lang.send(player, "kit.empty");
            return 0;
        }
        ServerData.Kit kit = Store.server().kits.getOrDefault(name, new ServerData.Kit());
        kit.items = items;
        kit.cooldown = cooldown;
        if (kit.icon == null) {
            ItemStack held = player.getMainHandItem();
            kit.icon = BuiltInRegistries.ITEM.getKey((held.isEmpty() ? Items.CHEST : held.getItem())).toString();
        }
        Store.server().kits.put(name, kit);
        Store.markDirty();
        Lang.send(player, "kit.created", "kit", name, "count", items.size());
        return 1;
    }

    /** {@code once} -> -1, {@code 0} -> 0, a duration -> seconds; {@link Long#MIN_VALUE} when unreadable. */
    static long cooldown(String text) {
        if (text.equalsIgnoreCase("once")) {
            return -1;
        }
        if (text.equals("0")) {
            return 0;
        }
        long millis = Time.parse(text);
        return millis < 0 ? Long.MIN_VALUE : millis / 1000;
    }

    static int edit(CommandContext<CommandSourceStack> context, String what, String value) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name").toLowerCase(Locale.ROOT);
        ServerData.Kit kit = Store.server().kits.get(name);
        if (kit == null) {
            Lang.send(source, "kit.unknown", "kit", name);
            return 0;
        }
        switch (what) {
            case "cooldown" -> {
                long cooldown = cooldown(value);
                if (cooldown == Long.MIN_VALUE) {
                    Lang.send(source, "error.bad_time", "time", value);
                    return 0;
                }
                kit.cooldown = cooldown;
            }
            case "restricted" -> {
                kit.restricted = Boolean.parseBoolean(value);
                reloadPermissions();
            }
            case "firstjoin" -> kit.firstJoin = Boolean.parseBoolean(value);
            case "icon" -> {
                ItemStack held = source.getPlayerOrException().getMainHandItem();
                if (held.isEmpty()) {
                    Lang.send(source, "kit.icon_hand");
                    return 0;
                }
                kit.icon = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            }
            case "items" -> {
                List<JsonElement> items = encode(source.getServer(), source.getPlayerOrException().getInventory());
                if (items.isEmpty()) {
                    Lang.send(source, "kit.empty");
                    return 0;
                }
                kit.items = items;
            }
            default -> {
                return 0;
            }
        }
        Store.markDirty();
        Lang.send(source, "kit.edited", "kit", name);
        return 1;
    }

    private static int delete(CommandSourceStack source, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (Store.server().kits.remove(name) == null) {
            Lang.send(source, "kit.unknown", "kit", typed);
            return 0;
        }
        Store.markDirty();
        Lang.send(source, "kit.deleted", "kit", name);
        return 1;
    }

    static int menu(ServerPlayer player) {
        List<Gui.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, ServerData.Kit> entry : Store.server().kits.entrySet()) {
            String name = entry.getKey();
            ServerData.Kit kit = entry.getValue();
            if (!mayUse(player, name, kit)) {
                continue;
            }
            long wait = wait(player, name, kit);
            Component status = wait < 0 ? Lang.get(player, "menu.kit_claimed")
                    : wait > 0 ? Lang.get(player, "menu.kit_wait", "time", Time.format(player, wait))
                    : Lang.get(player, "menu.kit_ready");
            List<Component> lore = List.of(status, Lang.get(player, "menu.kit_items", "count", kit.items.size()),
                    Lang.get(player, "menu.kit_hint"));
            entries.add(new Gui.Entry(Gui.icon(Gui.item(kit.icon, Items.CHEST), Lang.get(player, "menu.kit_name", "kit", name),
                    lore), click -> {
                        if (click.right()) {
                            preview(click.player(), name, kit, Kits::menu);
                        } else {
                            click.player().closeContainer();
                            claim(click.player(), name, false);
                        }
                    }));
        }
        if (entries.isEmpty()) {
            Lang.send(player, "kit.none");
            return 0;
        }
        Gui.paged(player, Lang.get(player, "menu.kits_title"), entries, 0,
                Lang.get(player, "menu.previous"), Lang.get(player, "menu.next"), null);
        return 1;
    }

    static void preview(ServerPlayer player, String name, ServerData.Kit kit, Consumer<ServerPlayer> back) {
        List<ItemStack> items = decode(player.level().getServer(), kit.items);
        Gui.Builder menu = Gui.builder(Math.min(6, (items.size() + 8) / 9 + 1), Lang.get(player, "menu.kit_preview", "kit", name));
        for (int i = 0; i < items.size() && i < menu.size() - 9; i++) {
            menu.item(i, items.get(i));
        }
        menu.button(menu.size() - 5, Gui.icon(Items.ARROW, Lang.get(player, "menu.back"), List.of()), click -> back.accept(click.player()));
        menu.open(player);
    }

    public static void onFirstJoin(ServerPlayer player) {
        if (!Module.KITS.enabled()) {
            return;
        }
        Store.server().kits.forEach((name, kit) -> {
            if (kit.firstJoin) {
                give(player, kit);
                PlayerData data = Store.of(player);
                data.kits.put(name, System.currentTimeMillis());
                Store.markDirty();
            }
        });
    }

    // ---------------------------------------------------------------- item (de)serialization

    static List<JsonElement> encode(MinecraftServer server, Container inventory) {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<JsonElement> out = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(out::add);
            }
        }
        return out;
    }

    static List<ItemStack> decode(MinecraftServer server, List<JsonElement> items) {
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<ItemStack> out = new ArrayList<>();
        for (JsonElement item : items) {
            ItemStack.CODEC.parse(ops, item).result().ifPresent(stack -> out.add(stack.copy()));
        }
        return out;
    }
}
