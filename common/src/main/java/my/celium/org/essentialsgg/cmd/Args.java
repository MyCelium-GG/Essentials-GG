package my.celium.org.essentialsgg.cmd;

import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/** Shared command arguments. */
public final class Args {
    /** Online names first, then everyone who ever joined. */
    public static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String name : context.getSource().getOnlinePlayerNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        for (String name : Store.namesStartingWith(remaining, 50)) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private Args() {
    }

    /** An online player: name or (with selector rights) {@code @p}. */
    public static RequiredArgumentBuilder<CommandSourceStack, ?> player(String name) {
        return Commands.argument(name, EntityArgument.player());
    }

    public static ServerPlayer player(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        return EntityArgument.getPlayer(context, name);
    }

    /** Any player who ever joined, online or not, by name. */
    public static RequiredArgumentBuilder<CommandSourceStack, String> known(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests(KNOWN_PLAYERS);
    }

    /** The data behind a {@link #known} argument, or {@code null} after telling the source. */
    public static PlayerData known(CommandContext<CommandSourceStack> context, String name) {
        String typed = StringArgumentType.getString(context, name);
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayerByName(typed);
        PlayerData data = online != null ? Store.of(online) : Store.byName(typed);
        if (data == null) {
            Lang.send(context.getSource(), "error.player_unknown", "player", typed);
        }
        return data;
    }
}
