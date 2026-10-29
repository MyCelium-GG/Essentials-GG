package my.celium.org.essentialsgg.module;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Fmt;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Chat: one format for every line, colors and gradients for those allowed,
 * {@code @name} mentions with a ping, ignore lists, a slow mode and mutes.
 * The vanilla message is replaced by a system message built from the format,
 * so vanilla clients see it as is.
 */
public final class Chat {
    public static ConfigValue<Boolean> formatChat;
    public static ConfigValue<String> format;
    public static ConfigValue<Boolean> mentions;
    public static ConfigValue<Integer> slowModeSeconds;

    private static final String COLOR = Perms.declare(Perms.ROOT + "chat.color", 2);
    private static final String HEX = Perms.declare(Perms.ROOT + "chat.hex", 2);
    private static final String GRADIENT = Perms.declare(Perms.ROOT + "chat.gradient", 2);
    private static final String FORMAT = Perms.declare(Perms.ROOT + "chat.format", 2);
    private static final String LINKS = Perms.declare(Perms.ROOT + "chat.events", 4);
    private static final String SLOW_BYPASS = Perms.declare(Perms.ROOT + "chat.slowmode.bypass", 2);
    private static final String CLEAR_BYPASS = Perms.declare(Perms.ROOT + "clearchat.bypass", 2);
    /** LuckPerms meta / string nodes read for {group_prefix} and {group_suffix}. */
    public static final String META_PREFIX = Perms.ROOT + "prefix";
    public static final String META_SUFFIX = Perms.ROOT + "suffix";

    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private Chat() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("chat");
        formatChat = builder.booleanValue("formatChat", true,
                "Rebuild every chat line from the format below. Off leaves chat to vanilla (mutes still apply).");
        format = builder.stringValue("format", "{group_prefix}{name}{group_suffix} <muted>»</muted> <text>{message}", 512,
                "Chat line. {name} is the nickname, {account} the account name, {message} the text, "
                        + "{group_prefix}/{group_suffix} the prefix and suffix from your permissions mod "
                        + "(LuckPerms meta 'essentialsgg.prefix' / 'essentialsgg.suffix'), {world} the dimension.");
        mentions = builder.booleanValue("mentions", true, "Highlight @name (or a plain name) and ping that player.");
        slowModeSeconds = builder.intValue("slowModeSeconds", 0, 0, 3600, "Seconds between two chat messages per player.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("broadcast", Module.CHAT, "command.broadcast", 3, List.of("bc", "announce"), (root, b) -> root
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(b.run(Chat::broadcast))));
        Cmd.define("clearchat", Module.CHAT, "command.clearchat", 2, List.of("cc"), (root, b) -> root
                .executes(b.run(Chat::clear)));
    }

    /** What a player may use in their own messages. */
    public static Fmt.Allow allowFor(ServerPlayer player) {
        return new Fmt.Allow(Perms.has(player, COLOR), Perms.has(player, HEX), Perms.has(player, GRADIENT),
                Perms.has(player, FORMAT), Perms.has(player, LINKS));
    }

    /** Chat hook: {@code true} when the vanilla message must be cancelled. */
    public static boolean onChat(ServerPlayer player, String raw) {
        if (Moderation.checkMuted(player)) {
            return true;
        }
        if (!Module.CHAT.enabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long slow = slowModeSeconds.get() * 1000L;
        if (slow > 0 && !Perms.has(player, SLOW_BYPASS)) {
            Long last = LAST_MESSAGE.get(player.getUUID());
            if (last != null && now - last < slow) {
                Lang.send(player, "chat.slow", "seconds", (slow - (now - last) + 999) / 1000);
                return true;
            }
            LAST_MESSAGE.put(player.getUUID(), now);
        }
        if (!formatChat.get()) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        MutableComponent message = mentions.get() ? Mentions.parse(server, raw, allowFor(player))
                : Fmt.parse(raw, allowFor(player));
        Component line = line(player, message);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PlayerData data = Store.get(viewer.getUUID());
            if (data != null && data.ignored.contains(player.getUUID()) && viewer != player) {
                continue;
            }
            viewer.sendSystemMessage(line);
            if (mentions.get() && viewer != player && Mentions.mentions(raw, viewer)) {
                ping(viewer, 0.6f, 1.4f);
            }
        }
        server.sendSystemMessage(line);
        Discord.onChat(player, message.getString());
        return true;
    }

    /** The formatted chat line for {@code message} from {@code player}. */
    public static Component line(ServerPlayer player, Component message) {
        Map<String, Component> values = new HashMap<>();
        values.put("name", Nicknames.name(player));
        values.put("account", Component.literal(player.getGameProfile().name()));
        values.put("message", message);
        values.put("world", Component.literal(player.level().dimension().identifier().getPath()));
        values.put("group_prefix", Fmt.parse(meta(player, META_PREFIX)));
        values.put("group_suffix", Fmt.parse(meta(player, META_SUFFIX)));
        return Fmt.parse(format.get(), values);
    }

    /** A sound only {@code player} hears, at their own position. */
    public static void ping(ServerPlayer player, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP),
                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    /** A prefix/suffix string from the permissions mod, or "". */
    public static String meta(ServerPlayer player, String key) {
        String value = Perms.meta(player, key);
        return value == null ? "" : value;
    }

    private static int broadcast(CommandContext<CommandSourceStack> context) {
        String message = StringArgumentType.getString(context, "message");
        Lang.broadcast(context.getSource().getServer(), "chat.broadcast", "message", Fmt.parse(message));
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        Component blank = Component.literal(" ");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!Perms.has(player, CLEAR_BYPASS)) {
                for (int i = 0; i < 100; i++) {
                    player.sendSystemMessage(blank);
                }
            }
        }
        Lang.broadcast(server, "chat.cleared", "actor", context.getSource().getDisplayName());
        return 1;
    }

    /** {@code @name} mentions: highlighted in the line, and a ping for the player (also on a plain name). */
    static final class Mentions {
        private Mentions() {
        }

        static boolean mentions(String raw, ServerPlayer viewer) {
            return containsWord(raw.toLowerCase(Locale.ROOT), viewer.getGameProfile().name().toLowerCase(Locale.ROOT)) >= 0;
        }

        /** Start of {@code word} in {@code text} as a whole word, or -1. */
        private static int containsWord(String text, String word) {
            int from = 0;
            while (true) {
                int at = text.indexOf(word, from);
                if (at < 0) {
                    return -1;
                }
                int end = at + word.length();
                boolean startOk = at == 0 || !isNameChar(text.charAt(at - 1));
                boolean endOk = end >= text.length() || !isNameChar(text.charAt(end));
                if (startOk && endOk) {
                    return at;
                }
                from = at + 1;
            }
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        /** The message parsed with each {@code @name} of an online player turned into a highlighted mention. */
        static MutableComponent parse(MinecraftServer server, String raw, Fmt.Allow allow) {
            Map<String, Component> values = new HashMap<>();
            String text = raw;
            int n = 0;
            for (ServerPlayer target : server.getPlayerList().getPlayers()) {
                String name = target.getGameProfile().name();
                String lower = text.toLowerCase(Locale.ROOT);
                int at = containsWord(lower, "@" + name.toLowerCase(Locale.ROOT));
                if (at < 0) {
                    continue;
                }
                String key = "@" + n++;
                values.put(key, Lang.get(Lang.DEFAULT, "chat.mention", "player", Nicknames.name(target)));
                text = text.substring(0, at) + "{" + key + "}" + text.substring(at + name.length() + 1);
            }
            return Fmt.parse(text, allow, values);
        }
    }
}
