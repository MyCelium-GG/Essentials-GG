package my.celium.org.essentialsgg.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Fmt;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Private messages. Vanilla keeps {@code /msg}, {@code /tell} and {@code /w};
 * Essentials GG answers to {@code /m}, {@code /pm}, {@code /dm} and
 * {@code /reply} ({@code /r}), with colors for those allowed in chat, ignore
 * lists, {@code /msgtoggle} and {@code /socialspy} for staff.
 */
public final class Messaging {
    private static final String SPY = Perms.declare(Perms.ROOT + "command.socialspy", 2);
    private static final String BYPASS = Perms.declare(Perms.ROOT + "msg.bypass", 2);
    /** Who each player last talked to, for {@code /reply}. */
    private static final Map<UUID, UUID> LAST = new HashMap<>();

    private Messaging() {
    }

    public static void commands() {
        Cmd.define("msg", Module.MESSAGING, "command.msg", 0, List.of("m", "pm", "dm", "message", "whisper"), (root, b) -> root
                .then(Args.player("player").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(b.run(c -> send(c.getSource(), Args.player(c, "player"),
                                StringArgumentType.getString(c, "message")))))));
        Cmd.define("reply", Module.MESSAGING, "command.reply", 0, List.of("r"), (root, b) -> root
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(b.run(Cmd.self(Messaging::reply)))));
        Cmd.define("ignore", Module.MESSAGING, "command.ignore", 0, List.of("unignore"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> listIgnored(p))))
                .then(Args.known("player").executes(b.run(Cmd.self(Messaging::ignore)))));
        Cmd.define("msgtoggle", Module.MESSAGING, 0, (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> toggle(p)))));
        Cmd.define("socialspy", Module.MESSAGING, "command.socialspy", 2, List.of("spy"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> spy(p)))));
    }

    private static int send(CommandSourceStack source, ServerPlayer to, String text) {
        ServerPlayer from = source.getPlayer();
        if (from != null) {
            if (Moderation.checkMuted(from)) {
                return 0;
            }
            if (from == to) {
                Lang.send(from, "msg.self");
                return 0;
            }
            PlayerData target = Store.of(to);
            if ((target.msgOff || target.ignored.contains(from.getUUID())) && !Perms.has(from, BYPASS)) {
                Lang.send(from, "msg.refuses", "player", Nicknames.name(to));
                return 0;
            }
        }
        Component message = from != null ? Fmt.parse(text, Chat.allowFor(from)) : Fmt.parse(text);
        Component fromName = from != null ? Nicknames.name(from) : source.getDisplayName();
        Lang.send(source, "msg.to", "player", Nicknames.name(to), "message", message);
        Lang.send(to, "msg.from", "player", fromName, "message", message,
                "name", from != null ? from.getGameProfile().name() : "");
        Chat.ping(to, 0.4f, 1.2f);
        if (from != null) {
            LAST.put(from.getUUID(), to.getUUID());
            LAST.put(to.getUUID(), from.getUUID());
            spies(source.getServer(), from, to, message);
        }
        return 1;
    }

    private static void spies(MinecraftServer server, ServerPlayer from, ServerPlayer to, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != from && player != to) {
                PlayerData data = Store.get(player.getUUID());
                if (data != null && data.socialSpy && Perms.has(player, SPY)) {
                    Lang.send(player, "msg.spy", "from", Nicknames.name(from), "to", Nicknames.name(to), "message", message);
                }
            }
        }
    }

    private static int reply(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        UUID last = LAST.get(player.getUUID());
        ServerPlayer to = last == null ? null : player.level().getServer().getPlayerList().getPlayer(last);
        if (to == null) {
            Lang.send(player, "msg.no_reply");
            return 0;
        }
        return send(context.getSource(), to, StringArgumentType.getString(context, "message"));
    }

    private static int ignore(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        if (target.id.equals(player.getUUID())) {
            Lang.send(player, "msg.ignore_self");
            return 0;
        }
        PlayerData self = Store.of(player);
        boolean removed = self.ignored.remove(target.id);
        if (!removed) {
            self.ignored.add(target.id);
        }
        Store.markDirty();
        Lang.send(player, removed ? "msg.unignored" : "msg.ignored", "player", Nicknames.name(target));
        return 1;
    }

    private static int listIgnored(ServerPlayer player) {
        PlayerData self = Store.of(player);
        if (self.ignored.isEmpty()) {
            Lang.send(player, "msg.ignore_none");
            return 0;
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : self.ignored) {
            PlayerData data = Store.get(id);
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(data != null ? data.name : id.toString());
        }
        Lang.send(player, "msg.ignore_list", "players", names.toString());
        return self.ignored.size();
    }

    private static int toggle(ServerPlayer player) {
        PlayerData data = Store.of(player);
        data.msgOff = !data.msgOff;
        Store.markDirty();
        Lang.send(player, data.msgOff ? "msg.toggle_off" : "msg.toggle_on");
        return 1;
    }

    private static int spy(ServerPlayer player) throws CommandSyntaxException {
        PlayerData data = Store.of(player);
        data.socialSpy = !data.socialSpy;
        Store.markDirty();
        Lang.send(player, data.socialSpy ? "msg.spy_on" : "msg.spy_off");
        return 1;
    }

    public static void onLeave(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }
}
