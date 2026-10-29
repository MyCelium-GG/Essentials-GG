package my.celium.org.essentialsgg.module;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * A simple economy: balances live with the rest of the player data.
 * {@code /balance}, {@code /pay}, {@code /baltop} and {@code /eco} for staff.
 * Other mods can use {@link #balance}, {@link #deposit} and {@link #withdraw}.
 */
public final class Economy {
    public static ConfigValue<Double> startingBalance;
    public static ConfigValue<String> symbol;
    public static ConfigValue<Boolean> symbolAfter;
    public static ConfigValue<Double> minimumPay;
    public static ConfigValue<Double> maxBalance;

    private static final String OTHERS = Perms.declare(Perms.ROOT + "balance.others", 0);

    private Economy() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("economy");
        startingBalance = builder.doubleValue("startingBalance", 0, 0, 1e12, "Money a new player starts with.");
        symbol = builder.stringValue("symbol", "$", 8, "Currency symbol.");
        symbolAfter = builder.booleanValue("symbolAfter", false, "Write 10 € instead of $10.");
        minimumPay = builder.doubleValue("minimumPay", 0.01, 0.01, 1e12, "Smallest amount /pay accepts.");
        maxBalance = builder.doubleValue("maxBalance", 1e13, 1, 1e15, "No balance goes above this.");
        builder.root();
    }

    public static void commands() {
        Cmd.define("balance", Module.ECONOMY, "command.balance", 0, List.of("bal", "money"), (root, b) -> root
                .executes(b.run(Cmd.self((p, c) -> show(c.getSource(), Store.of(p)))))
                .then(Args.known("player").requires(s -> Perms.has(s, OTHERS))
                        .executes(b.run(c -> {
                            PlayerData data = Args.known(c, "player");
                            return data == null ? 0 : show(c.getSource(), data);
                        }))));
        Cmd.define("pay", Module.ECONOMY, 0, (root, b) -> root
                .then(Args.known("player").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(b.run(Cmd.self(Economy::pay))))));
        Cmd.define("baltop", Module.ECONOMY, "command.baltop", 0, List.of("balancetop", "richest"), (root, b) -> root
                .executes(b.run(c -> top(c.getSource(), 1)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(b.run(c -> top(c.getSource(), IntegerArgumentType.getInteger(c, "page"))))));
        Cmd.define("eco", Module.ECONOMY, "command.eco", 3, List.of("economy"), (root, b) -> root
                .then(Commands.literal("give").then(Args.known("player").then(amount()
                        .executes(b.run(c -> admin(c, "give"))))))
                .then(Commands.literal("take").then(Args.known("player").then(amount()
                        .executes(b.run(c -> admin(c, "take"))))))
                .then(Commands.literal("set").then(Args.known("player").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(b.run(c -> admin(c, "set"))))))
                .then(Commands.literal("reset").then(Args.known("player")
                        .executes(b.run(c -> admin(c, "reset"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> amount() {
        return Commands.argument("amount", DoubleArgumentType.doubleArg(0.01));
    }

    // ---------------------------------------------------------------- API

    public static double balance(PlayerData data) {
        return data.balance;
    }

    public static void deposit(PlayerData data, double amount) {
        data.balance = round(Math.min(maxBalance.get(), data.balance + amount));
        Store.markDirty();
    }

    /** Takes {@code amount}; false (and nothing taken) when the balance is too low. */
    public static boolean withdraw(PlayerData data, double amount) {
        if (data.balance + 1e-9 < amount) {
            return false;
        }
        data.balance = round(data.balance - amount);
        Store.markDirty();
        return true;
    }

    public static String format(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String number = format.format(amount);
        return symbolAfter.get() ? number + " " + symbol.get() : symbol.get() + number;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** New players get the starting balance; called when their data is first made. */
    public static void onFirstJoin(PlayerData data) {
        if (Module.ECONOMY.enabled() && startingBalance.get() > 0) {
            deposit(data, startingBalance.get());
        }
    }

    // ---------------------------------------------------------------- commands

    private static int show(CommandSourceStack source, PlayerData data) {
        ServerPlayer self = source.getPlayer();
        boolean own = self != null && self.getUUID().equals(data.id);
        Lang.send(source, own ? "eco.balance" : "eco.balance_other", "player", Nicknames.name(data),
                "amount", format(data.balance));
        return 1;
    }

    private static int pay(ServerPlayer player, CommandContext<CommandSourceStack> context) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        double amount = round(DoubleArgumentType.getDouble(context, "amount"));
        if (target.id.equals(player.getUUID())) {
            Lang.send(player, "eco.pay_self");
            return 0;
        }
        if (amount < minimumPay.get()) {
            Lang.send(player, "eco.pay_minimum", "amount", format(minimumPay.get()));
            return 0;
        }
        PlayerData self = Store.of(player);
        if (!withdraw(self, amount)) {
            Lang.send(player, "eco.not_enough", "amount", format(amount), "balance", format(self.balance));
            return 0;
        }
        deposit(target, amount);
        Lang.send(player, "eco.paid", "player", Nicknames.name(target), "amount", format(amount));
        ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(target.id);
        if (online != null) {
            Lang.send(online, "eco.received", "player", Nicknames.name(player), "amount", format(amount));
        }
        return 1;
    }

    private static int top(CommandSourceStack source, int page) {
        List<PlayerData> all = new ArrayList<>(Store.all());
        all.removeIf(data -> data.balance <= 0);
        all.sort(Comparator.comparingDouble((PlayerData data) -> data.balance).reversed());
        int perPage = 10;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages);
        double total = all.stream().mapToDouble(data -> data.balance).sum();
        Lang.send(source, "eco.top_header", "page", current, "pages", pages, "total", format(total));
        for (int i = (current - 1) * perPage; i < Math.min(all.size(), current * perPage); i++) {
            PlayerData data = all.get(i);
            Lang.send(source, "eco.top_line", "rank", i + 1, "player", Nicknames.name(data), "amount", format(data.balance));
        }
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> context, String action) {
        PlayerData target = Args.known(context, "player");
        if (target == null) {
            return 0;
        }
        double amount = action.equals("reset") ? startingBalance.get() : round(DoubleArgumentType.getDouble(context, "amount"));
        switch (action) {
            case "give" -> deposit(target, amount);
            case "take" -> {
                target.balance = round(Math.max(0, target.balance - amount));
                Store.markDirty();
            }
            default -> {
                target.balance = round(Math.min(maxBalance.get(), amount));
                Store.markDirty();
            }
        }
        Lang.send(context.getSource(), "eco.admin", "player", Nicknames.name(target), "balance", format(target.balance));
        return 1;
    }
}
