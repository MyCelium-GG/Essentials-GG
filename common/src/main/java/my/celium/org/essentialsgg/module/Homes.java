package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.Gui;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Homes: {@code /sethome [name]}, {@code /home [name]}, {@code /delhome <name>}
 * and {@code /homes} (a menu). How many homes a player may set comes from
 * {@code essentialsgg.homes.limit.<n>} nodes; staff reach other players'
 * homes as {@code /home Steve:base}.
 */
public final class Homes {
    public static ConfigValue<Integer> defaultLimit;
    public static ConfigValue<List<String>> limitTiers;

    private static final String OTHERS = Perms.declare(Perms.ROOT + "homes.others", 2);
    private static final String LIMIT = Perms.ROOT + "homes.limit";
    private static final String DEFAULT_NAME = "home";
    private static final int MAX_NAME = 32;

    private static final SuggestionProvider<CommandSourceStack> OWN_HOMES = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            String remaining = builder.getRemainingLowerCase();
            for (String name : Store.of(player).homes.keySet()) {
                if (name.startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    };

    private Homes() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("homes");
        defaultLimit = builder.intValue("defaultLimit", 3, 0, 1000,
                "Homes a player may set without any essentialsgg.homes.limit.<n> permission.");
        limitTiers = builder.stringListValue("limitTiers", List.of("1", "3", "5", "10", "25", "50", "100"),
                "Numbers usable in essentialsgg.homes.limit.<n>; the highest one a player holds counts. "
                        + "essentialsgg.homes.limit.unlimited removes the limit.");
        builder.root();
        for (String tier : List.of("1", "3", "5", "10", "25", "50", "100")) {
            Perms.declare(LIMIT + "." + tier, 99);
        }
        Perms.declare(LIMIT + ".unlimited", 4);
    }

    public static void commands() {
        Cmd.define("home", Module.HOMES, "command.home", 0, List.of("h"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> home(p, null))))
                .then(Commands.argument("name", StringArgumentType.string()).suggests(OWN_HOMES)
                        .executes(b.run(Cmd.self((p, c) -> home(p, StringArgumentType.getString(c, "name")))))));
        Cmd.define("sethome", Module.HOMES, "command.sethome", 0, List.of("createhome"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> set(p, DEFAULT_NAME))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(b.run(Cmd.self((p, c) -> set(p, StringArgumentType.getString(c, "name")))))));
        Cmd.define("delhome", Module.HOMES, "command.delhome", 0, List.of("deletehome", "remhome"), (root, b) -> root
                .then(Commands.argument("name", StringArgumentType.string()).suggests(OWN_HOMES)
                        .executes(b.run(Cmd.self((p, c) -> delete(p, StringArgumentType.getString(c, "name")))))));
        Cmd.define("homes", Module.HOMES, "command.homes", 0, List.of("listhomes"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> menu(p, Store.of(p), 0))))
                .then(Args.known("player").requires(s -> Perms.has(s, OTHERS))
                        .executes(b.run(Cmd.self(Homes::menuOther)))));
    }

    public static int limit(ServerPlayer player) {
        List<Integer> tiers = new ArrayList<>();
        for (String tier : limitTiers.get()) {
            try {
                tiers.add(Integer.parseInt(tier.trim()));
            } catch (NumberFormatException ignored) {
                // Not a number: not a tier.
            }
        }
        return Perms.limit(player, LIMIT, tiers, defaultLimit.get());
    }

    private static int home(ServerPlayer player, String query) {
        PlayerData owner = Store.of(player);
        String name = query;
        if (query != null && query.contains(":") && Perms.has(player, OTHERS)) {
            String who = query.substring(0, query.indexOf(':'));
            owner = Store.byName(who);
            if (owner == null) {
                Lang.send(player, "error.player_unknown", "player", who);
                return 0;
            }
            name = query.substring(query.indexOf(':') + 1);
        }
        Map<String, Loc> homes = owner.homes;
        if (homes.isEmpty()) {
            Lang.send(player, "home.none");
            return 0;
        }
        if (name == null) {
            name = homes.size() == 1 ? homes.keySet().iterator().next() : DEFAULT_NAME;
            if (!homes.containsKey(name)) {
                return menu(player, owner, 0);
            }
        }
        String key = name.toLowerCase(Locale.ROOT);
        Loc loc = homes.get(key);
        if (loc == null) {
            Lang.send(player, "home.unknown", "home", name, "homes", String.join(", ", homes.keySet()));
            return 0;
        }
        teleport(player, key, loc);
        return 1;
    }

    private static void teleport(ServerPlayer player, String name, Loc loc) {
        Teleports.go(player, () -> loc, p -> Lang.send(p, "home.arrived", "home", name));
    }

    private static int set(ServerPlayer player, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1," + MAX_NAME + "}") || name.contains(":")) {
            Lang.send(player, "home.bad_name");
            return 0;
        }
        PlayerData data = Store.of(player);
        int limit = limit(player);
        if (!data.homes.containsKey(name) && data.homes.size() >= limit) {
            Lang.send(player, "home.limit", "limit", limit);
            return 0;
        }
        boolean moved = data.homes.containsKey(name);
        data.homes.put(name, Loc.of(player));
        Store.markDirty();
        Lang.send(player, moved ? "home.moved" : "home.set", "home", name,
                "count", data.homes.size(), "limit", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit));
        return 1;
    }

    private static int delete(ServerPlayer player, String typed) {
        PlayerData data = Store.of(player);
        String name = typed.toLowerCase(Locale.ROOT);
        if (data.homes.remove(name) == null) {
            Lang.send(player, "home.unknown", "home", typed, "homes", String.join(", ", data.homes.keySet()));
            return 0;
        }
        Store.markDirty();
        Lang.send(player, "home.deleted", "home", name);
        return 1;
    }

    private static int menuOther(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        PlayerData owner = Args.known(context, "player");
        return owner == null ? 0 : menu(player, owner, 0);
    }

    private static int menu(ServerPlayer player, PlayerData owner, int page) {
        if (owner.homes.isEmpty()) {
            Lang.send(player, "home.none");
            return 0;
        }
        boolean own = owner.id.equals(player.getUUID());
        List<Gui.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Loc> home : owner.homes.entrySet()) {
            Loc loc = home.getValue();
            List<Component> lore = new ArrayList<>(List.of(
                    Lang.get(player, "menu.location", "x", loc.blockX(), "y", loc.blockY(), "z", loc.blockZ(),
                            "world", loc.dimName()),
                    Lang.get(player, "menu.click_teleport")));
            if (own) {
                lore.add(Lang.get(player, "menu.shift_delete"));
            }
            entries.add(new Gui.Entry(Gui.icon(iconFor(loc), Lang.get(player, "menu.home_name", "home", home.getKey()), lore),
                    click -> {
                        click.player().closeContainer();
                        if (own && click.shift()) {
                            delete(click.player(), home.getKey());
                        } else {
                            teleport(click.player(), home.getKey(), loc);
                        }
                    }));
        }
        int limit = own ? limit(player) : owner.homes.size();
        Gui.Entry info = new Gui.Entry(Gui.icon(Items.BOOK, Lang.get(player, "menu.homes_info",
                "count", owner.homes.size(), "limit", limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit)),
                List.of()), click -> { });
        Gui.paged(player, Lang.get(player, own ? "menu.homes_title" : "menu.homes_title_other", "player", owner.name),
                entries, page, Lang.get(player, "menu.previous"), Lang.get(player, "menu.next"), info);
        return 1;
    }

    private static Item iconFor(Loc loc) {
        return switch (loc.dimName()) {
            case "the_nether" -> Items.NETHERRACK;
            case "the_end" -> Items.END_STONE;
            default -> Gui.item("minecraft:red_bed", Items.COMPASS);
        };
    }
}
