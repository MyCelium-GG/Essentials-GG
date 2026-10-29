package my.celium.org.essentialsgg.module;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Nicknames with colors, hex and gradients for those allowed. A nickname
 * shows in chat, private messages, join lines, the tab list and every
 * Essentials GG message; hovering it shows the account name.
 */
public final class Nicknames {
    public static ConfigValue<Integer> minLength;
    public static ConfigValue<Integer> maxLength;
    public static ConfigValue<String> prefix;
    public static ConfigValue<String> allowed;

    private static final String COLOR = Perms.declare(Perms.ROOT + "nick.color", 2);
    private static final String HEX = Perms.declare(Perms.ROOT + "nick.hex", 2);
    private static final String GRADIENT = Perms.declare(Perms.ROOT + "nick.gradient", 2);
    private static final String FORMAT = Perms.declare(Perms.ROOT + "nick.format", 2);
    private static final String OTHERS = Perms.declare(Perms.ROOT + "nick.others", 2);
    private static final String IMPERSONATE = Perms.declare(Perms.ROOT + "nick.impersonate", 3);

    private Nicknames() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("nicknames");
        minLength = builder.intValue("minLength", 2, 1, 32, "Shortest nickname, colors not counted.");
        maxLength = builder.intValue("maxLength", 16, 1, 64, "Longest nickname, colors not counted.");
        prefix = builder.stringValue("prefix", "", 32, "Put in front of every nickname, e.g. ~ so nicknames stand out.");
        allowed = builder.stringValue("allowedCharacters", "[A-Za-z0-9_ ]+", 128,
                "Regular expression the nickname (without colors) must match.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("nick", Module.NICKNAMES, "command.nick", 2, List.of("nickname"), (root, b) -> root
                .then(Commands.argument("nick", StringArgumentType.greedyString())
                        .executes(b.run(Cmd.self((p, c) -> set(c.getSource(), p, StringArgumentType.getString(c, "nick"))))))
                .then(Commands.literal("player").requires(s -> Perms.has(s, OTHERS))
                        .then(Args.player("player").then(Commands.argument("nick", StringArgumentType.greedyString())
                                .executes(b.run(c -> set(c.getSource(), Args.player(c, "player"),
                                        StringArgumentType.getString(c, "nick"))))))));
        Cmd.define("realname", Module.NICKNAMES, 0, (root, b) -> root
                .then(Commands.argument("nick", StringArgumentType.greedyString())
                        .executes(b.run(Nicknames::realname))));
    }

    /** How a player is shown: nickname (with its colors and the prefix) or account name. Hover: account name. */
    public static MutableComponent name(ServerPlayer player) {
        PlayerData data = Store.get(player.getUUID());
        return name(player.getGameProfile().name(), data);
    }

    public static MutableComponent name(PlayerData data) {
        return name(data.name, data);
    }

    private static MutableComponent name(String account, PlayerData data) {
        String nick = data != null && Module.NICKNAMES.enabled() ? data.nick : null;
        if (nick == null) {
            return Component.literal(account).withStyle(style -> style
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(account)))
                    .withClickEvent(new ClickEvent.SuggestCommand("/msg " + account + " ")));
        }
        MutableComponent shown = Component.empty();
        if (!prefix.get().isEmpty()) {
            shown.append(Fmt.parse(prefix.get()));
        }
        shown.append(Fmt.parse(nick, Fmt.Allow.STYLE));
        return Component.empty().withStyle(style -> style
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(account)))
                .withClickEvent(new ClickEvent.SuggestCommand("/msg " + account + " "))).append(shown);
    }

    /** What a player may use in their own nickname. */
    private static Fmt.Allow allowFor(ServerPlayer player) {
        return new Fmt.Allow(Perms.has(player, COLOR), Perms.has(player, HEX), Perms.has(player, GRADIENT),
                Perms.has(player, FORMAT), false);
    }

    private static int set(CommandSourceStack source, ServerPlayer target, String typed) throws CommandSyntaxException {
        PlayerData data = Store.of(target);
        String nick = typed.strip();
        if (nick.equalsIgnoreCase("off") || nick.equalsIgnoreCase(target.getGameProfile().name())) {
            data.nick = null;
            Store.markDirty();
            Sidebar.updateTabName(target);
            Lang.send(source, "nick.cleared", "player", Nicknames.name(target));
            return 1;
        }
        // Staff setting someone else's nickname use their own rights.
        ServerPlayer actor = source.getPlayer();
        Fmt.Allow allow = actor == null ? Fmt.Allow.STYLE : allowFor(actor);
        String plain = Fmt.parse(nick, allow).getString();
        if (plain.length() < minLength.get() || plain.length() > maxLength.get()) {
            Lang.send(source, "nick.length", "min", minLength.get(), "max", maxLength.get());
            return 0;
        }
        if (!matches(plain)) {
            Lang.send(source, "nick.characters");
            return 0;
        }
        if (actor != null && !Perms.has(actor, IMPERSONATE) && takenByOther(target, plain)) {
            Lang.send(source, "nick.taken", "nick", plain);
            return 0;
        }
        data.nick = keepAllowed(nick, allow);
        Store.markDirty();
        Sidebar.updateTabName(target);
        if (actor != target) {
            Lang.send(target, "nick.set_by_staff", "nick", name(target));
        }
        Lang.send(source, "nick.set", "player", Component.literal(target.getGameProfile().name()), "nick", name(target));
        return 1;
    }

    /**
     * The nickname as stored: formatting the setter may not use is escaped so
     * it can never turn on later (nicknames are rendered with full style rights).
     */
    private static String keepAllowed(String nick, Fmt.Allow allow) {
        if (allow.equals(Fmt.Allow.STYLE)) {
            return nick;
        }
        // Re-encode what the setter's rights produced: plain text, legacy-escaped, colors as hex tags.
        StringBuilder out = new StringBuilder();
        Fmt.parse(nick, allow).visit((style, text) -> {
            StringBuilder tags = new StringBuilder();
            if (style.getColor() != null) {
                tags.append("<#").append(String.format(Locale.ROOT, "%06x", style.getColor().getValue())).append('>');
            }
            if (style.isBold()) {
                tags.append("<b>");
            }
            if (style.isItalic()) {
                tags.append("<i>");
            }
            if (style.isUnderlined()) {
                tags.append("<u>");
            }
            if (style.isStrikethrough()) {
                tags.append("<st>");
            }
            if (style.isObfuscated()) {
                tags.append("<obf>");
            }
            out.append("<reset>").append(tags).append(text.replace("\\", "\\\\").replace("<", "\\<")
                    .replace("&", "&&").replace("§", ""));
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    private static boolean matches(String plain) {
        try {
            return Pattern.compile(allowed.get()).matcher(plain).matches();
        } catch (PatternSyntaxException e) {
            return Pattern.compile(allowed.getDefault()).matcher(plain).matches();
        }
    }

    /** Whether {@code plain} is another player's account name or nickname. */
    private static boolean takenByOther(ServerPlayer target, String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        for (PlayerData other : Store.all()) {
            if (other.id.equals(target.getUUID())) {
                continue;
            }
            if ((other.name != null && other.name.toLowerCase(Locale.ROOT).equals(lower))
                    || (other.nick != null && Fmt.plain(other.nick).toLowerCase(Locale.ROOT).equals(lower))) {
                return true;
            }
        }
        return false;
    }

    private static int realname(CommandContext<CommandSourceStack> context) {
        String query = StringArgumentType.getString(context, "nick").toLowerCase(Locale.ROOT);
        int found = 0;
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            PlayerData data = Store.get(player.getUUID());
            if (data != null && data.nick != null && Fmt.plain(data.nick).toLowerCase(Locale.ROOT).contains(query)) {
                Lang.send(context.getSource(), "nick.realname", "nick", name(player), "player", player.getGameProfile().name());
                found++;
            }
        }
        if (found == 0) {
            Lang.send(context.getSource(), "nick.realname_none", "nick", query);
        }
        return found;
    }
}
