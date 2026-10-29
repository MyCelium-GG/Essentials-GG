package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.gui.InventoryView;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Everyday staff and player tools. Commands that can target someone else
 * need {@code essentialsgg.<command>.others} for that.
 */
public final class Utility {
    private static final String INVSEE_MODIFY = Perms.declare(Perms.ROOT + "invsee.modify", 3);
    private static final String EC_OTHERS = Perms.declare(Perms.ROOT + "enderchest.others", 2);
    private static final String EC_MODIFY = Perms.declare(Perms.ROOT + "enderchest.modify", 3);
    private static final String WHOIS_IP = Perms.declare(Perms.ROOT + "whois.ip", 3);
    private static final String SEEN_LOCATION = Perms.declare(Perms.ROOT + "seen.location", 2);
    private static final String REPAIR_ALL = Perms.declare(Perms.ROOT + "repair.all", 2);

    private Utility() {
    }

    /** Self-or-other command: {@code /name} and, with {@code <name>.others}, {@code /name <player>}. */
    private interface Action {
        int run(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException;
    }

    private static void selfOrOther(String name, int level, List<String> aliases, Action action) {
        String others = Perms.declare(Perms.ROOT + name + ".others", Math.max(level, 2));
        Cmd.define(name, Module.UTILITY, "command." + name, level, aliases, (root, b) -> root
                .executes(b.run(c -> action.run(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Args.player("player").requires(s -> Perms.has(s, others))
                        .executes(b.run(c -> action.run(c.getSource(), Args.player(c, "player"))))));
    }

    public static void commands() {
        selfOrOther("fly", 2, List.of(), Utility::fly);
        selfOrOther("god", 2, List.of("godmode"), Utility::god);
        selfOrOther("heal", 2, List.of(), Utility::heal);
        selfOrOther("feed", 2, List.of("eat"), Utility::feed);
        selfOrOther("gmc", 2, List.of("creative"), (s, t) -> gamemode(s, t, GameType.CREATIVE));
        selfOrOther("gms", 2, List.of("survival"), (s, t) -> gamemode(s, t, GameType.SURVIVAL));
        selfOrOther("gma", 2, List.of("adventure"), (s, t) -> gamemode(s, t, GameType.ADVENTURE));
        selfOrOther("gmsp", 2, List.of("spectator"), (s, t) -> gamemode(s, t, GameType.SPECTATOR));
        selfOrOther("ext", 2, List.of("extinguish"), Utility::extinguish);
        selfOrOther("ping", 0, List.of("latency"), Utility::ping);
        selfOrOther("playtime", 0, List.of("pt"), Utility::playtime);
        selfOrOther("getpos", 0, List.of("coords", "whereami"), Utility::getpos);

        Cmd.define("speed", Module.UTILITY, 2, (root, b) -> root
                .then(Commands.argument("speed", IntegerArgumentType.integer(0, 10))
                        .executes(b.run(Cmd.self((p, c) -> speed(p, IntegerArgumentType.getInteger(c, "speed"), p.getAbilities().flying))))
                        .then(Commands.literal("fly").executes(b.run(Cmd.self((p, c) -> speed(p, IntegerArgumentType.getInteger(c, "speed"), true)))))
                        .then(Commands.literal("walk").executes(b.run(Cmd.self((p, c) -> speed(p, IntegerArgumentType.getInteger(c, "speed"), false)))))));
        Cmd.define("hat", Module.UTILITY, "command.hat", 0, List.of("head"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> hat(p)))));
        Cmd.define("more", Module.UTILITY, 2, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> more(p)))));
        Cmd.define("repair", Module.UTILITY, "command.repair", 2, List.of("fix"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> repair(p, false))))
                .then(Commands.literal("all").requires(s -> Perms.has(s, REPAIR_ALL))
                        .executes(b.run(Cmd.self((p, c) -> repair(p, true))))));
        Cmd.define("invsee", Module.UTILITY, "command.invsee", 2, List.of("inv"), (root, b) -> root
                .then(Args.player("player").executes(b.run(Cmd.self((p, c) -> {
                    ServerPlayer target = Args.player(c, "player");
                    InventoryView.open(p, target, Lang.get(p, "menu.invsee_title", "player", target.getGameProfile().name()),
                            Perms.has(p, INVSEE_MODIFY));
                    return 1;
                })))));
        Cmd.define("enderchest", Module.UTILITY, "command.enderchest", 2, List.of("ec", "echest"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    InventoryView.open(p, p.getEnderChestInventory(), Lang.get(p, "menu.enderchest_title"), true);
                    return 1;
                })))
                .then(Args.player("player").requires(s -> Perms.has(s, EC_OTHERS)).executes(b.run(Cmd.self((p, c) -> {
                    ServerPlayer target = Args.player(c, "player");
                    InventoryView.open(p, target.getEnderChestInventory(),
                            Lang.get(p, "menu.enderchest_other", "player", target.getGameProfile().name()),
                            Perms.has(p, EC_MODIFY));
                    return 1;
                })))));
        Cmd.define("near", Module.UTILITY, "command.near", 0, List.of("nearby"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> near(p, 200))))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10_000))
                        .requires(s -> Perms.has(s, Perms.declare(Perms.ROOT + "near.radius", 2)))
                        .executes(b.run(Cmd.self((p, c) -> near(p, IntegerArgumentType.getInteger(c, "radius")))))));
        Cmd.define("seen", Module.UTILITY, "command.seen", 0, List.of("lastseen"), (root, b) -> root
                .then(Args.known("player").executes(b.run(Utility::seen))));
        Cmd.define("whois", Module.UTILITY, 2, (root, b) -> root
                .then(Args.known("player").executes(b.run(Utility::whois))));
        Cmd.define("suicide", Module.UTILITY, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> {
                    p.kill(p.level());
                    return 1;
                }))));
        vanilla("day", "time set day");
        vanilla("night", "time set night");
        vanilla("sun", "weather clear");
        vanilla("storm", "weather thunder");
    }

    /** Shortcut for a vanilla command, run with the caller's own rights. */
    private static void vanilla(String name, String command) {
        Cmd.define(name, Module.UTILITY, 2, (LiteralArgumentBuilder<CommandSourceStack> root, Cmd.Builder b) -> root
                .executes(b.run(c -> {
                    c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(), command);
                    return 1;
                })));
    }

    private static void confirm(CommandSourceStack source, ServerPlayer target, String key, Object... args) {
        Lang.send(target, key, args);
        if (source.getPlayer() != target) {
            Object[] withPlayer = new Object[args.length + 2];
            System.arraycopy(args, 0, withPlayer, 0, args.length);
            withPlayer[args.length] = "player";
            withPlayer[args.length + 1] = Nicknames.name(target);
            Lang.send(source, key + "_other", withPlayer);
        }
    }

    private static int fly(CommandSourceStack source, ServerPlayer target) {
        boolean on = !target.getAbilities().mayfly;
        target.getAbilities().mayfly = on;
        if (!on) {
            target.getAbilities().flying = false;
        }
        target.onUpdateAbilities();
        confirm(source, target, on ? "util.fly_on" : "util.fly_off");
        return 1;
    }

    private static int god(CommandSourceStack source, ServerPlayer target) {
        PlayerData data = Store.of(target);
        data.god = !data.god;
        Store.markDirty();
        applyGod(target, data);
        confirm(source, target, data.god ? "util.god_on" : "util.god_off");
        return 1;
    }

    private static void applyGod(ServerPlayer player, PlayerData data) {
        boolean creative = player.isCreative() || player.isSpectator();
        boolean want = data.god || creative;
        if (player.getAbilities().invulnerable != want) {
            player.getAbilities().invulnerable = want;
            player.onUpdateAbilities();
        }
    }

    /** Keeps god mode on across game mode changes, respawns and rejoins. */
    public static void tick(MinecraftServer server) {
        if (!Module.UTILITY.enabled() || server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = Store.get(player.getUUID());
            if (data != null && data.god) {
                applyGod(player, data);
                player.getFoodData().setFoodLevel(20);
            }
        }
    }

    private static int heal(CommandSourceStack source, ServerPlayer target) {
        target.setHealth(target.getMaxHealth());
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(20f);
        target.clearFire();
        target.removeAllEffects();
        confirm(source, target, "util.healed");
        return 1;
    }

    private static int feed(CommandSourceStack source, ServerPlayer target) {
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(20f);
        confirm(source, target, "util.fed");
        return 1;
    }

    private static int gamemode(CommandSourceStack source, ServerPlayer target, GameType type) {
        target.setGameMode(type);
        confirm(source, target, "util.gamemode", "mode", type.getShortDisplayName());
        return 1;
    }

    private static int extinguish(CommandSourceStack source, ServerPlayer target) {
        target.clearFire();
        confirm(source, target, "util.extinguished");
        return 1;
    }

    private static int ping(CommandSourceStack source, ServerPlayer target) {
        Lang.send(source, "util.ping", "player", Nicknames.name(target), "ms", target.connection.latency());
        return 1;
    }

    private static int playtime(CommandSourceStack source, ServerPlayer target) {
        long ticks = target.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        Lang.send(source, "util.playtime", "player", Nicknames.name(target), "time", Time.format(source.getPlayer(), ticks * 50));
        return 1;
    }

    private static int getpos(CommandSourceStack source, ServerPlayer target) {
        Loc loc = Loc.of(target);
        Lang.send(source, "util.getpos", "player", Nicknames.name(target), "x", loc.blockX(), "y", loc.blockY(),
                "z", loc.blockZ(), "world", loc.dimName());
        return 1;
    }

    private static int speed(ServerPlayer player, int speed, boolean fly) {
        if (fly) {
            player.getAbilities().setFlyingSpeed(speed == 0 ? 0.05f : 0.05f * speed);
        } else {
            float walk = speed == 0 ? 0.1f : 0.1f * speed;
            player.getAbilities().setWalkingSpeed(walk);
            AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.setBaseValue(walk);
            }
        }
        player.onUpdateAbilities();
        Lang.send(player, fly ? "util.speed_fly" : "util.speed_walk", "speed", speed == 0 ? 1 : speed);
        return 1;
    }

    private static int hat(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            Lang.send(player, "util.hat_empty");
            return 0;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        player.setItemSlot(EquipmentSlot.HEAD, hand.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, head);
        Lang.send(player, "util.hat");
        return 1;
    }

    private static int more(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            Lang.send(player, "util.hand_empty");
            return 0;
        }
        hand.setCount(hand.getMaxStackSize());
        Lang.send(player, "util.more", "count", hand.getCount());
        return 1;
    }

    private static int repair(ServerPlayer player, boolean all) {
        int repaired = 0;
        if (all) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                repaired += fix(inventory.getItem(i));
            }
        } else {
            repaired = fix(player.getMainHandItem());
        }
        Lang.send(player, repaired > 0 ? "util.repaired" : "util.nothing_to_repair", "count", repaired);
        return repaired;
    }

    private static int fix(ItemStack stack) {
        if (!stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged()) {
            stack.setDamageValue(0);
            return 1;
        }
        return 0;
    }

    private static int near(ServerPlayer player, int radius) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer other : player.level().players()) {
            if (other != player && !other.isSpectator() && other.distanceTo(player) <= radius) {
                near.add(other);
            }
        }
        if (near.isEmpty()) {
            Lang.send(player, "util.near_none", "radius", radius);
            return 0;
        }
        near.sort(Comparator.comparingDouble(other -> other.distanceTo(player)));
        Lang.send(player, "util.near_header", "radius", radius, "count", near.size());
        for (ServerPlayer other : near) {
            Lang.send(player, "util.near_line", "player", Nicknames.name(other), "distance", Math.round(other.distanceTo(player)));
        }
        return near.size();
    }

    private static int seen(CommandContext<CommandSourceStack> context) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(data.id);
        if (online != null) {
            Lang.send(source, "util.seen_online", "player", Nicknames.name(data));
            return 1;
        }
        long ago = System.currentTimeMillis() - data.lastSeen;
        Lang.send(source, "util.seen", "player", Nicknames.name(data), "time", Time.format(source.getPlayer(), ago));
        if (data.logout != null && Perms.has(source, SEEN_LOCATION)) {
            Lang.send(source, "util.seen_location", "x", data.logout.blockX(), "y", data.logout.blockY(),
                    "z", data.logout.blockZ(), "world", data.logout.dimName());
        }
        return 1;
    }

    private static int whois(CommandContext<CommandSourceStack> context) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(data.id);
        Lang.send(source, "util.whois_header", "player", Nicknames.name(data));
        Lang.send(source, "util.whois_line", "key", "UUID", "value", data.id.toString());
        Lang.send(source, "util.whois_line", "key", "Account", "value", data.name);
        if (Perms.has(source, WHOIS_IP) && data.lastIp != null) {
            Lang.send(source, "util.whois_line", "key", "IP", "value", data.lastIp);
        }
        if (online != null) {
            Loc loc = Loc.of(online);
            Lang.send(source, "util.whois_line", "key", "Location",
                    "value", loc.blockX() + " " + loc.blockY() + " " + loc.blockZ() + " (" + loc.dimName() + ")");
            Lang.send(source, "util.whois_line", "key", "Game mode", "value", online.gameMode().getName());
            Lang.send(source, "util.whois_line", "key", "Health",
                    "value", Math.round(online.getHealth()) + "/" + Math.round(online.getMaxHealth()));
            Lang.send(source, "util.whois_line", "key", "Fly / God",
                    "value", online.getAbilities().mayfly + " / " + data.god);
            Lang.send(source, "util.whois_line", "key", "AFK", "value", String.valueOf(Afk.isAfk(online)));
        } else {
            Lang.send(source, "util.whois_line", "key", "Last seen",
                    "value", Time.format(source.getPlayer(), System.currentTimeMillis() - data.lastSeen));
        }
        if (Module.ECONOMY.enabled()) {
            Lang.send(source, "util.whois_line", "key", "Balance", "value", Economy.format(data.balance));
        }
        Lang.send(source, "util.whois_line", "key", "Muted",
                "value", String.valueOf(data.activeMute(System.currentTimeMillis()) != null));
        Lang.send(source, "util.whois_line", "key", "Warnings", "value", String.valueOf(data.warnings.size()));
        return 1;
    }
}
