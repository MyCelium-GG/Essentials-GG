package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

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
import my.celium.org.essentialsgg.gui.Prompt;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
                .executes(b.run(Cmd.self((p, c) -> menu(p, Store.of(p), null))))
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
                return menu(player, owner, null);
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

    private static boolean validName(String name) {
        return name.matches("[a-z0-9_-]{1," + MAX_NAME + "}");
    }

    private static String limitText(int limit) {
        return limit == Integer.MAX_VALUE ? "∞" : String.valueOf(limit);
    }

    private static int set(ServerPlayer player, String typed) {
        String name = typed.toLowerCase(Locale.ROOT);
        if (!validName(name)) {
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
                "count", data.homes.size(), "limit", limitText(limit));
        return 1;
    }

    private static int delete(ServerPlayer player, String typed) {
        PlayerData data = Store.of(player);
        String name = typed.toLowerCase(Locale.ROOT);
        if (data.homes.remove(name) == null) {
            Lang.send(player, "home.unknown", "home", typed, "homes", String.join(", ", data.homes.keySet()));
            return 0;
        }
        data.homeIcons.remove(name);
        Store.markDirty();
        Lang.send(player, "home.deleted", "home", name);
        return 1;
    }

    private static int menuOther(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        PlayerData owner = Args.known(context, "player");
        return owner == null ? 0 : menu(player, owner, null);
    }

    /** The menu of the player's own homes, returning to {@code back}. */
    static void openOwn(ServerPlayer player, Consumer<ServerPlayer> back) {
        menu(player, Store.of(player), back);
    }

    private static int menu(ServerPlayer player, PlayerData owner, Consumer<ServerPlayer> back) {
        boolean own = owner.id.equals(player.getUUID());
        if (owner.homes.isEmpty() && !own) {
            Lang.send(player, "home.none");
            return 0;
        }
        List<Gui.Entry> entries = new ArrayList<>();
        new TreeMap<>(owner.homes).forEach((name, loc) -> {
            List<Component> lore = new ArrayList<>(Gui.where(player, loc));
            lore.add(Lang.get(player, "menu.click_teleport"));
            if (own) {
                lore.add(Lang.get(player, "menu.click_manage"));
            }
            entries.add(new Gui.Entry(Gui.icon(iconFor(owner, name, loc), Lang.get(player, "menu.home_name", "home", name), lore),
                    click -> {
                        if (own && click.right()) {
                            manage(click.player(), name, back);
                        } else {
                            click.player().closeContainer();
                            teleport(click.player(), name, loc);
                        }
                    }));
        });
        if (entries.isEmpty()) {
            entries.add(new Gui.Entry(Gui.icon(Items.PAPER, Lang.get(player, "menu.homes_empty"),
                    List.of(Lang.get(player, "menu.homes_empty_hint"))), null));
        }
        int limit = own ? limit(player) : owner.homes.size();
        ItemStack info = Gui.icon(Items.BOOK, Lang.get(player, "menu.homes_info",
                "count", owner.homes.size(), "limit", limitText(limit)), List.of());
        Gui.paged(player, Lang.get(player, own ? "menu.homes_title" : "menu.homes_title_other", "player", owner.name),
                entries, 0, info, own ? setHere(player, owner, limit, back) : null, back);
        return 1;
    }

    /** The button that adds a home where the player stands, or says why it can't. */
    private static Gui.Entry setHere(ServerPlayer player, PlayerData data, int limit, Consumer<ServerPlayer> back) {
        if (data.homes.size() >= limit) {
            return new Gui.Entry(Gui.icon(Items.BARRIER, Lang.get(player, "menu.home_limit"), List.of()), Gui.LOCKED);
        }
        return new Gui.Entry(Gui.icon(Gui.item("minecraft:lime_bed", Items.COMPASS), Lang.get(player, "menu.home_set_here"),
                List.of(Lang.get(player, "menu.home_set_hint"))),
                click -> Prompt.ask(click.player(), "menu.home_ask_name", typed -> {
                    if (set(click.player(), typed) > 0) {
                        menu(click.player(), Store.of(click.player()), back);
                    }
                }));
    }

    private static void manage(ServerPlayer player, String name, Consumer<ServerPlayer> back) {
        PlayerData data = Store.of(player);
        Loc loc = data.homes.get(name);
        if (loc == null) {
            menu(player, data, back);
            return;
        }
        Consumer<ServerPlayer> list = p -> menu(p, Store.of(p), back);
        Gui.builder(3, Lang.get(player, "menu.home_manage_title", "home", name))
                .button(10, Gui.icon(Items.ENDER_PEARL, Lang.get(player, "menu.home_teleport"), Gui.where(player, loc)), click -> {
                    click.player().closeContainer();
                    teleport(click.player(), name, loc);
                })
                .button(12, Gui.icon(Items.NAME_TAG, Lang.get(player, "menu.home_rename"), List.of(Lang.get(player, "menu.home_rename_hint"))),
                        click -> rename(click.player(), name, back))
                .button(13, Gui.icon(iconFor(data, name, loc), Lang.get(player, "menu.home_icon"), List.of(Lang.get(player, "menu.home_icon_hint"))),
                        click -> {
                            ItemStack held = click.player().getMainHandItem();
                            if (held.isEmpty()) {
                                Gui.deny(click.player());
                                Lang.send(click.player(), "menu.home_icon_hand");
                                return;
                            }
                            data.homeIcons.put(name, BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                            Store.markDirty();
                            manage(click.player(), name, back);
                        })
                .button(14, Gui.icon(Items.COMPASS, Lang.get(player, "menu.home_move"), List.of(Lang.get(player, "menu.home_move_hint"))),
                        click -> {
                            set(click.player(), name);
                            manage(click.player(), name, back);
                        })
                .button(16, Gui.icon(Items.BARRIER, Lang.get(player, "menu.home_delete"), List.of(Lang.get(player, "menu.home_delete_hint"))),
                        click -> Gui.confirm(click.player(), Lang.get(click.player(), "menu.home_delete_title", "home", name),
                                Lang.get(click.player(), "menu.home_delete_confirm", "home", name),
                                List.of(Lang.get(click.player(), "menu.home_delete_warning")),
                                Lang.get(click.player(), "menu.home_delete_cancel"),
                                p -> {
                                    delete(p, name);
                                    list.accept(p);
                                },
                                p -> manage(p, name, back)))
                .back(player, list).fill().open(player);
    }

    private static void rename(ServerPlayer player, String from, Consumer<ServerPlayer> back) {
        Prompt.ask(player, "menu.home_ask_rename", typed -> {
            PlayerData data = Store.of(player);
            String name = typed.toLowerCase(Locale.ROOT);
            if (!data.homes.containsKey(from)) {
                menu(player, data, back);
            } else if (!validName(name)) {
                Lang.send(player, "home.bad_name");
                manage(player, from, back);
            } else if (data.homes.containsKey(name)) {
                Lang.send(player, "home.name_taken", "home", name);
                manage(player, from, back);
            } else {
                data.homes.put(name, data.homes.remove(from));
                String icon = data.homeIcons.remove(from);
                if (icon != null) {
                    data.homeIcons.put(name, icon);
                }
                Store.markDirty();
                Lang.send(player, "home.renamed", "from", from, "home", name);
                manage(player, name, back);
            }
        }, "home", from);
    }

    private static Item iconFor(PlayerData owner, String name, Loc loc) {
        Item fallback = switch (loc.dimName()) {
            case "the_nether" -> Items.NETHERRACK;
            case "the_end" -> Items.END_STONE;
            default -> Gui.item("minecraft:red_bed", Items.COMPASS);
        };
        return Gui.item(owner.homeIcons.get(name), fallback);
    }
}
