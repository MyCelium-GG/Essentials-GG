package my.celium.org.essentialsgg.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import my.celium.org.essentialsgg.Module;
import my.celium.org.essentialsgg.cmd.Args;
import my.celium.org.essentialsgg.cmd.Cmd;
import my.celium.org.essentialsgg.data.PlayerData;
import my.celium.org.essentialsgg.data.ServerData;
import my.celium.org.essentialsgg.data.Store;
import my.celium.org.essentialsgg.platform.LocalPerms;
import my.celium.org.essentialsgg.platform.Perms;
import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /perm}: Essentials GG's own permissions, for servers without a
 * permissions mod (and on top of one: these entries are checked first).
 *
 * <pre>
 * /perm user &lt;player&gt; [info]               what they have
 * /perm user &lt;player&gt; add &lt;node&gt; [false]     grant (or deny with false)
 * /perm user &lt;player&gt; remove &lt;node&gt;
 * /perm user &lt;player&gt; check &lt;node&gt;          the final answer, from every source
 * /perm user &lt;player&gt; group add|remove &lt;group&gt;
 * /perm group &lt;group&gt; create|delete|info
 * /perm group &lt;group&gt; add &lt;node&gt; [false] / remove &lt;node&gt;
 * /perm group &lt;group&gt; prefix|suffix &lt;text|off&gt; / weight &lt;n&gt;
 * /perm groups                           every group
 * /perm nodes [filter]                   every node and its default level
 * </pre>
 *
 * Nodes take wildcards: {@code essentialsgg.kit.*}, {@code essentialsgg.*}.
 * Only Essentials GG's own nodes are affected; vanilla operator rights stay
 * as they are.
 */
public final class Permissions {
    private static final SuggestionProvider<CommandSourceStack> NODES = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        TreeSet<String> nodes = new TreeSet<>();
        nodes.add("*");
        for (String node : Perms.declared().keySet()) {
            nodes.add(node);
            // Offer the wildcard of every level: essentialsgg.*, essentialsgg.command.*, ...
            int dot = node.indexOf('.');
            while (dot > 0) {
                nodes.add(node.substring(0, dot) + ".*");
                dot = node.indexOf('.', dot + 1);
            }
        }
        for (String node : nodes) {
            if (node.startsWith(remaining)) {
                builder.suggest(node);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> GROUPS = (context, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String group : Store.server().groups.keySet()) {
            if (group.startsWith(remaining)) {
                builder.suggest(group);
            }
        }
        return builder.buildFuture();
    };

    private Permissions() {
    }

    public static void commands() {
        Cmd.define("perm", Module.PERMISSIONS, "command.perm", 4, List.of("perms", "permission"), (root, b) -> root
                .executes(b.run(c -> {
                    Lang.send(c.getSource(), "perm.usage");
                    return 1;
                }))
                .then(Commands.literal("user").then(Args.known("player")
                        .executes(b.run(Permissions::userInfo))
                        .then(Commands.literal("info").executes(b.run(Permissions::userInfo)))
                        .then(Commands.literal("add").then(node().executes(b.run(c -> userSet(c, true)))
                                .then(Commands.literal("false").executes(b.run(c -> userSet(c, false))))
                                .then(Commands.literal("true").executes(b.run(c -> userSet(c, true))))))
                        .then(Commands.literal("remove").then(node().executes(b.run(Permissions::userRemove))))
                        .then(Commands.literal("check").then(node().executes(b.run(Permissions::userCheck))))
                        .then(Commands.literal("group")
                                .then(Commands.literal("add").then(group().executes(b.run(c -> userGroup(c, true)))))
                                .then(Commands.literal("remove").then(group().executes(b.run(c -> userGroup(c, false))))))))
                .then(Commands.literal("group").then(group()
                        .executes(b.run(Permissions::groupInfo))
                        .then(Commands.literal("info").executes(b.run(Permissions::groupInfo)))
                        .then(Commands.literal("create").executes(b.run(Permissions::groupCreate)))
                        .then(Commands.literal("delete").executes(b.run(Permissions::groupDelete)))
                        .then(Commands.literal("add").then(node().executes(b.run(c -> groupSet(c, true)))
                                .then(Commands.literal("false").executes(b.run(c -> groupSet(c, false))))
                                .then(Commands.literal("true").executes(b.run(c -> groupSet(c, true))))))
                        .then(Commands.literal("remove").then(node().executes(b.run(Permissions::groupRemove))))
                        .then(Commands.literal("prefix").then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(b.run(c -> groupMeta(c, true)))))
                        .then(Commands.literal("suffix").then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(b.run(c -> groupMeta(c, false)))))
                        .then(Commands.literal("weight").then(Commands.argument("weight", IntegerArgumentType.integer(-1000, 1000))
                                .executes(b.run(Permissions::groupWeight))))))
                .then(Commands.literal("groups").executes(b.run(c -> groups(c.getSource()))))
                .then(Commands.literal("nodes")
                        .executes(b.run(c -> nodes(c.getSource(), "")))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .executes(b.run(c -> nodes(c.getSource(), StringArgumentType.getString(c, "filter")))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> node() {
        // string(): quotes are only needed for nodes with a '*'.
        return Commands.argument("node", StringArgumentType.string()).suggests(NODES);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> group() {
        return Commands.argument("group", StringArgumentType.word()).suggests(GROUPS);
    }

    private static String nodeArg(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "node").toLowerCase(Locale.ROOT).trim();
    }

    private static String groupArg(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "group").toLowerCase(Locale.ROOT);
    }

    private static boolean validNode(CommandSourceStack source, String node) {
        if (!node.matches("\\*|[a-z0-9_\\-]+(\\.[a-z0-9_\\-]+)*(\\.\\*)?")) {
            Lang.send(source, "perm.bad_node", "node", node);
            return false;
        }
        return true;
    }

    /** Sends the new command tree, so commands someone may now use appear (or vanish) at once. */
    private static void refresh(MinecraftServer server, PlayerData data) {
        if (data == null) {
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(data.id);
        if (online != null) {
            server.getCommands().sendCommands(online);
            Sidebar.updateTabName(online);
        }
    }

    // ---------------------------------------------------------------- users

    private static int userInfo(CommandContext<CommandSourceStack> context) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        CommandSourceStack source = context.getSource();
        List<String> groups = new ArrayList<>(data.groups);
        if (Store.server().groups.containsKey(LocalPerms.DEFAULT_GROUP)) {
            groups.add(LocalPerms.DEFAULT_GROUP);
        }
        Lang.send(source, "perm.user_header", "player", data.name,
                "groups", groups.isEmpty() ? "-" : String.join(", ", groups));
        listNodes(source, data.perms);
        return 1;
    }

    private static void listNodes(CommandSourceStack source, Map<String, Boolean> perms) {
        if (perms.isEmpty()) {
            Lang.send(source, "perm.no_nodes");
            return;
        }
        perms.forEach((node, allowed) -> Lang.send(source, allowed ? "perm.node_allowed" : "perm.node_denied", "node", node));
    }

    private static int userSet(CommandContext<CommandSourceStack> context, boolean allowed) {
        PlayerData data = Args.known(context, "player");
        String node = nodeArg(context);
        if (data == null || !validNode(context.getSource(), node)) {
            return 0;
        }
        data.perms.put(node, allowed);
        Store.markDirty();
        refresh(context.getSource().getServer(), data);
        Lang.send(context.getSource(), allowed ? "perm.granted" : "perm.denied_set", "node", node, "target", data.name);
        return 1;
    }

    private static int userRemove(CommandContext<CommandSourceStack> context) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        String node = nodeArg(context);
        if (data.perms.remove(node) == null) {
            Lang.send(context.getSource(), "perm.not_set", "node", node, "target", data.name);
            return 0;
        }
        Store.markDirty();
        refresh(context.getSource().getServer(), data);
        Lang.send(context.getSource(), "perm.removed", "node", node, "target", data.name);
        return 1;
    }

    private static int userCheck(CommandContext<CommandSourceStack> context) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        String node = nodeArg(context);
        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(data.id);
        Boolean local = LocalPerms.check(data.id, node);
        if (online != null) {
            Integer level = Perms.declared().get(node);
            boolean has = Perms.has(online, node, level == null ? 2 : level);
            Lang.send(context.getSource(), has ? "perm.check_yes" : "perm.check_no", "player", data.name, "node", node,
                    "source", Lang.raw(Lang.DEFAULT, local != null ? "perm.source_perm" : "perm.source_other"));
        } else {
            Lang.send(context.getSource(), local == null ? "perm.check_offline" : local ? "perm.check_yes" : "perm.check_no",
                    "player", data.name, "node", node, "source", Lang.raw(Lang.DEFAULT, "perm.source_perm"));
        }
        return 1;
    }

    private static int userGroup(CommandContext<CommandSourceStack> context, boolean add) {
        PlayerData data = Args.known(context, "player");
        if (data == null) {
            return 0;
        }
        String group = groupArg(context);
        if (add && !Store.server().groups.containsKey(group)) {
            Lang.send(context.getSource(), "perm.group_unknown", "group", group);
            return 0;
        }
        boolean changed = add ? !data.groups.contains(group) && data.groups.add(group) : data.groups.remove(group);
        if (!changed) {
            Lang.send(context.getSource(), add ? "perm.already_in" : "perm.not_in", "player", data.name, "group", group);
            return 0;
        }
        Store.markDirty();
        refresh(context.getSource().getServer(), data);
        Lang.send(context.getSource(), add ? "perm.joined" : "perm.left", "player", data.name, "group", group);
        return 1;
    }

    // ---------------------------------------------------------------- groups

    private static ServerData.Group existing(CommandContext<CommandSourceStack> context) {
        String name = groupArg(context);
        ServerData.Group group = Store.server().groups.get(name);
        if (group == null) {
            Lang.send(context.getSource(), "perm.group_unknown", "group", name);
        }
        return group;
    }

    private static int groupInfo(CommandContext<CommandSourceStack> context) {
        ServerData.Group group = existing(context);
        if (group == null) {
            return 0;
        }
        String name = groupArg(context);
        long members = Store.all().stream().filter(data -> data.groups.contains(name)).count();
        Lang.send(context.getSource(), "perm.group_header", "group", name, "weight", group.weight,
                "members", name.equals(LocalPerms.DEFAULT_GROUP) ? "*" : String.valueOf(members),
                "gprefix", group.prefix == null ? "-" : group.prefix, "gsuffix", group.suffix == null ? "-" : group.suffix);
        listNodes(context.getSource(), group.perms);
        return 1;
    }

    private static int groupCreate(CommandContext<CommandSourceStack> context) {
        String name = groupArg(context);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Lang.send(context.getSource(), "perm.bad_group");
            return 0;
        }
        if (Store.server().groups.containsKey(name)) {
            Lang.send(context.getSource(), "perm.group_exists", "group", name);
            return 0;
        }
        Store.server().groups.put(name, new ServerData.Group());
        Store.markDirty();
        Lang.send(context.getSource(), "perm.group_created", "group", name);
        return 1;
    }

    private static int groupDelete(CommandContext<CommandSourceStack> context) {
        String name = groupArg(context);
        if (Store.server().groups.remove(name) == null) {
            Lang.send(context.getSource(), "perm.group_unknown", "group", name);
            return 0;
        }
        Store.all().forEach(data -> data.groups.remove(name));
        Store.markDirty();
        refresh(context.getSource().getServer(), null);
        Lang.send(context.getSource(), "perm.group_deleted", "group", name);
        return 1;
    }

    private static int groupSet(CommandContext<CommandSourceStack> context, boolean allowed) {
        ServerData.Group group = existing(context);
        String node = nodeArg(context);
        if (group == null || !validNode(context.getSource(), node)) {
            return 0;
        }
        group.perms.put(node, allowed);
        Store.markDirty();
        refresh(context.getSource().getServer(), null);
        Lang.send(context.getSource(), allowed ? "perm.granted" : "perm.denied_set", "node", node, "target", groupArg(context));
        return 1;
    }

    private static int groupRemove(CommandContext<CommandSourceStack> context) {
        ServerData.Group group = existing(context);
        if (group == null) {
            return 0;
        }
        String node = nodeArg(context);
        if (group.perms.remove(node) == null) {
            Lang.send(context.getSource(), "perm.not_set", "node", node, "target", groupArg(context));
            return 0;
        }
        Store.markDirty();
        refresh(context.getSource().getServer(), null);
        Lang.send(context.getSource(), "perm.removed", "node", node, "target", groupArg(context));
        return 1;
    }

    private static int groupMeta(CommandContext<CommandSourceStack> context, boolean prefix) {
        ServerData.Group group = existing(context);
        if (group == null) {
            return 0;
        }
        String text = StringArgumentType.getString(context, "text");
        String value = text.equalsIgnoreCase("off") ? null : text;
        if (prefix) {
            group.prefix = value;
        } else {
            group.suffix = value;
        }
        Store.markDirty();
        refresh(context.getSource().getServer(), null);
        Lang.send(context.getSource(), prefix ? "perm.prefix_set" : "perm.suffix_set", "group", groupArg(context),
                "value", value == null ? "-" : value);
        return 1;
    }

    private static int groupWeight(CommandContext<CommandSourceStack> context) {
        ServerData.Group group = existing(context);
        if (group == null) {
            return 0;
        }
        group.weight = IntegerArgumentType.getInteger(context, "weight");
        Store.markDirty();
        refresh(context.getSource().getServer(), null);
        Lang.send(context.getSource(), "perm.weight_set", "group", groupArg(context), "weight", group.weight);
        return 1;
    }

    private static int groups(CommandSourceStack source) {
        if (Store.server().groups.isEmpty()) {
            Lang.send(source, "perm.no_groups");
            return 0;
        }
        Store.server().groups.forEach((name, group) -> Lang.send(source, "perm.groups_line", "group", name,
                "weight", group.weight, "count", group.perms.size()));
        return Store.server().groups.size();
    }

    private static int nodes(CommandSourceStack source, String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(Perms.declared()).entrySet()) {
            if (entry.getKey().contains(lower)) {
                Lang.send(source, "perm.nodes_line", "node", entry.getKey(), "level", entry.getValue());
                shown++;
            }
        }
        if (shown == 0) {
            Lang.send(source, "perm.no_nodes");
        }
        return shown;
    }
}
