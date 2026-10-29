package my.celium.org.essentialsgg.module;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.cmd.Time;
import my.celium.org.essentialsgg.data.Loc;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

/**
 * Punishments next to vanilla's: {@code /ban}, {@code /kick} and
 * {@code /pardon} stay vanilla, Essentials GG adds {@code /tempban}
 * (a vanilla ban with an end date, so the ban list stays the one source of
 * truth), {@code /unban}, mutes, warnings and {@code /freeze}. Staff with
 * {@code essentialsgg.notify.punish} see every punishment.
 */
public final class Moderation {
    private static final String NOTIFY = Perms.declare(Perms.ROOT + "notify.punish", 2);
    private static final String EXEMPT = Perms.declare(Perms.ROOT + "punish.exempt", 4);

    /** Frozen players and where they stand. */
    private static final Map<UUID, Loc> FROZEN = new HashMap<>();

    private Moderation() {
    }

    public static void commands() {
        Cmd.define("tempban", Module.MODERATION, 3, (root, b) -> root
                .then(Args.known("player").then(Commands.argument("duration", StringArgumentType.word())
                        .executes(b.run(c -> tempban(c, null)))
                        .then(reason().executes(b.run(c -> tempban(c, StringArgumentType.getString(c, "reason"))))))));
        Cmd.define("unban", Module.MODERATION, 3, (root, b) -> root
                .then(Args.known("player").executes(b.run(Moderation::unban))));
        Cmd.define("mute", Module.MODERATION, 2, (root, b) -> root
                .then(Args.known("player").executes(b.run(c -> mute(c, null, null)))
                        .then(reason().executes(b.run(c -> mute(c, null, StringArgumentType.getString(c, "reason")))))));
        Cmd.define("tempmute", Module.MODERATION, 2, (root, b) -> root
                .then(Args.known("player").then(Commands.argument("duration", StringArgumentType.word())
                        .executes(b.run(c -> mute(c, StringArgumentType.getString(c, "duration"), null)))
                        .then(reason().executes(b.run(c -> mute(c, StringArgumentType.getString(c, "duration"),
                                StringArgumentType.getString(c, "reason"))))))));
        Cmd.define("unmute", Module.MODERATION, 2, (root, b) -> root
                .then(Args.known("player").executes(b.run(Moderation::unmute))));
        Cmd.define("warn", Module.MODERATION, 2, (root, b) -> root
                .then(Args.known("player").then(reason().executes(b.run(Moderation::warn)))));
        Cmd.define("warnings", Module.MODERATION, "command.warnings", 0, List.of("warns"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> warnings(c.getSource(), Store.of(p)))))
                .then(Args.known("player").requires(s -> Perms.has(s, NOTIFY)).executes(b.run(c -> {
                    PlayerData data = Args.known(c, "player");
                    return data == null ? 0 : warnings(c.getSource(), data);
                }))));
        Cmd.define("clearwarnings", Module.MODERATION, 3, (root, b) -> root
                .then(Args.known("player").executes(b.run(Moderation::clearWarnings))));
        Cmd.define("freeze", Module.MODERATION, 2, (root, b) -> root
                .then(Args.player("player").executes(b.run(Moderation::freeze))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> reason() {
        return Commands.argument("reason", StringArgumentType.greedyString());
    }

    private static String actor(CommandSourceStack source) {
        return source.getTextName();
    }

    /** Refuses to punish owners (unless the console does it) and tells the source. */
    private static boolean exempt(CommandSourceStack source, PlayerData target) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(target.id);
        if (online != null && source.getPlayer() != null && Perms.has(online, EXEMPT)) {
            Lang.send(source, "mod.exempt", "player", Nicknames.name(target));
            return true;
        }
        return false;
    }

    private static void notifyStaff(MinecraftServer server, String key, Object... args) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Perms.has(player, NOTIFY)) {
                Lang.send(player, key, args);
            }
        }
        server.sendSystemMessage(Lang.get(Lang.DEFAULT, key, args));
    }

    // ---------------------------------------------------------------- bans

    private static int tempban(CommandContext<CommandSourceStack> context, String reason) {
        PlayerData target = Args.known(context, "player");
        if (target == null || exempt(context.getSource(), target)) {
            return 0;
        }
        String durationText = StringArgumentType.getString(context, "duration");
        long duration = Time.parse(durationText);
        if (duration <= 0) {
            Lang.send(context.getSource(), "error.bad_time", "time", durationText);
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        Date expires = new Date(System.currentTimeMillis() + duration);
        String why = reason == null ? Lang.raw(Lang.DEFAULT, "mod.no_reason") : reason;
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(new UserBanListEntry(new NameAndId(target.id, target.name), new Date(), actor(context.getSource()), expires, why));
        ServerPlayer online = server.getPlayerList().getPlayer(target.id);
        String time = Time.format(online, duration);
        if (online != null) {
            online.connection.disconnect(Lang.get(online, "mod.tempban_screen", "time", time, "reason", why,
                    "until", date(expires)));
        }
        notifyStaff(server, "mod.tempbanned", "player", Nicknames.name(target), "actor", actor(context.getSource()),
                "time", time, "reason", why);
        Discord.punishment("tempban", target.name, actor(context.getSource()), why, time);
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        UserBanList bans = context.getSource().getServer().getPlayerList().getBans();
        NameAndId who = new NameAndId(target.id, target.name);
        if (!bans.isBanned(who)) {
            Lang.send(context.getSource(), "mod.not_banned", "player", target.name);
            return 0;
        }
        bans.remove(who);
        notifyStaff(context.getSource().getServer(), "mod.unbanned", "player", target.name, "actor", actor(context.getSource()));
        Discord.punishment("unban", target.name, actor(context.getSource()), null, null);
        return 1;
    }

    private static String date(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(date);
    }

    // ---------------------------------------------------------------- mutes

    private static int mute(CommandContext<CommandSourceStack> context, String durationText, String reason) {
        PlayerData target = Args.known(context, "player");
        if (target == null || exempt(context.getSource(), target)) {
            return 0;
        }
        long duration = 0;
        if (durationText != null) {
            duration = Time.parse(durationText);
            if (duration <= 0) {
                Lang.send(context.getSource(), "error.bad_time", "time", durationText);
                return 0;
            }
        }
        String why = reason == null ? Lang.raw(Lang.DEFAULT, "mod.no_reason") : reason;
        target.mute = new PlayerData.Mute(duration > 0 ? System.currentTimeMillis() + duration : 0, why,
                actor(context.getSource()));
        Store.markDirty();
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer online = server.getPlayerList().getPlayer(target.id);
        String time = duration > 0 ? Time.format(online, duration) : Lang.raw(Lang.languageOf(online), "mod.forever");
        Lang.send(online, "mod.you_are_muted", "time", time, "reason", why);
        notifyStaff(server, "mod.muted", "player", Nicknames.name(target), "actor", actor(context.getSource()),
                "time", time, "reason", why);
        Discord.punishment("mute", target.name, actor(context.getSource()), why, time);
        return 1;
    }

    private static int unmute(CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        if (target.activeMute(System.currentTimeMillis()) == null) {
            Lang.send(context.getSource(), "mod.not_muted", "player", Nicknames.name(target));
            return 0;
        }
        target.mute = null;
        Store.markDirty();
        MinecraftServer server = context.getSource().getServer();
        Lang.send(server.getPlayerList().getPlayer(target.id), "mod.you_are_unmuted");
        notifyStaff(server, "mod.unmuted", "player", Nicknames.name(target), "actor", actor(context.getSource()));
        return 1;
    }

    /** Whether {@code player} is muted; tells them so. */
    public static boolean checkMuted(ServerPlayer player) {
        if (!Module.MODERATION.enabled()) {
            return false;
        }
        PlayerData data = Store.of(player);
        PlayerData.Mute mute = data.activeMute(System.currentTimeMillis());
        if (mute == null) {
            return false;
        }
        String time = mute.until == 0 ? Lang.raw(Lang.languageOf(player), "mod.forever")
                : Time.format(player, mute.until - System.currentTimeMillis());
        Lang.send(player, "mod.still_muted", "time", time, "reason", mute.reason);
        return true;
    }

    // ---------------------------------------------------------------- warnings

    private static int warn(CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null || exempt(context.getSource(), target)) {
            return 0;
        }
        String reason = StringArgumentType.getString(context, "reason");
        target.warnings.add(new PlayerData.Warning(System.currentTimeMillis(), reason, actor(context.getSource())));
        Store.markDirty();
        MinecraftServer server = context.getSource().getServer();
        Lang.send(server.getPlayerList().getPlayer(target.id), "mod.you_are_warned", "reason", reason,
                "count", target.warnings.size());
        notifyStaff(server, "mod.warned", "player", Nicknames.name(target), "actor", actor(context.getSource()),
                "reason", reason, "count", target.warnings.size());
        Discord.punishment("warn", target.name, actor(context.getSource()), reason, null);
        return 1;
    }

    private static int warnings(CommandSourceStack source, PlayerData target) {
        if (target.warnings.isEmpty()) {
            Lang.send(source, "mod.no_warnings", "player", Nicknames.name(target));
            return 0;
        }
        Lang.send(source, "mod.warnings_header", "player", Nicknames.name(target), "count", target.warnings.size());
        int i = 1;
        for (PlayerData.Warning warning : target.warnings) {
            Lang.send(source, "mod.warnings_line", "number", i++, "reason", warning.reason, "actor", warning.by,
                    "date", date(new Date(warning.at)));
        }
        return target.warnings.size();
    }

    private static int clearWarnings(CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        int count = target.warnings.size();
        target.warnings.clear();
        Store.markDirty();
        Lang.send(context.getSource(), "mod.warnings_cleared", "player", Nicknames.name(target), "count", count);
        return 1;
    }

    // ---------------------------------------------------------------- freeze

    private static int freeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = Args.player(context, "player");
        PlayerData data = Store.of(target);
        if (exempt(context.getSource(), data)) {
            return 0;
        }
        if (FROZEN.remove(target.getUUID()) != null) {
            Lang.send(target, "mod.unfrozen");
            Lang.send(context.getSource(), "mod.unfroze", "player", Nicknames.name(target));
        } else {
            FROZEN.put(target.getUUID(), Loc.of(target));
            Lang.send(target, "mod.frozen");
            Lang.send(context.getSource(), "mod.froze", "player", Nicknames.name(target));
        }
        return 1;
    }

    public static boolean isFrozen(ServerPlayer player) {
        return FROZEN.containsKey(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty() || server.getTickCount() % 5 != 0) {
            return;
        }
        FROZEN.forEach((id, loc) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && (Math.abs(player.getX() - loc.x()) > 0.1 || Math.abs(player.getZ() - loc.z()) > 0.1
                    || player.getY() > loc.y() + 0.6)) {
                loc.teleport(player);
            }
        });
    }
}
