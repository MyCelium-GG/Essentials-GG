package my.celium.org.essentialsgg;

import java.util.Locale;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;

/**
 * The parts of Essentials GG that can be switched off. A module that is off
 * registers no commands and its listeners return straight away; changing
 * one needs a restart (commands are registered once).
 */
public enum Module {
    CORE("Admin commands (/ess reload, /ess migrate, /language). Always on.", true),
    TELEPORT("/tpa, /tpahere, /tpaccept, /tpdeny, /back, /top, /tphere, /tpall and teleport warmups.", true),
    HOMES("/home, /sethome, /delhome and the homes menu.", true),
    WARPS("/warp, /setwarp, /delwarp and the warps menu.", true),
    SPAWN("/spawn and /setspawn; new players start at the spawn.", true),
    RTP("/rtp: random teleport to a safe spot, with a dimension menu.", true),
    KITS("/kit, /createkit, /delkit and the kits menu.", true),
    ECONOMY("/balance, /pay, /baltop and /eco.", true),
    MODERATION("/tempban, /unban, /mute, /tempmute, /unmute, /warn, /warnings, /freeze.", true),
    CHAT("Chat format with colors for those allowed, mentions, /broadcast, /clearchat.", true),
    MESSAGING("/msg, /reply, /ignore, /socialspy, /msgtoggle.", true),
    NICKNAMES("/nick and /realname.", true),
    AFK("/afk and automatic AFK.", true),
    UTILITY("/fly, /god, /heal, /feed, /gmc, /speed, /hat, /invsee, /enderchest, /repair, /near, /seen and more.", true),
    WORKBENCHES("/craft, /anvil, /grindstone, /stonecutter, /smithing, /loom, /cartography, /trash.", true),
    INFO("/motd and /rules, MOTD on join, welcome broadcast for new players.", true),
    SIDEBAR("Per-player sidebar (scoreboard) with placeholders. Text in the language files.", false),
    TABLIST("Tab list header and footer, nicknames and AFK tags in the tab list.", true),
    PERMISSIONS("/perm: give players and groups Essentials GG permissions, prefixes and suffixes, no permissions mod needed.", true),
    DISCORD("Discord webhook: joins, leaves, chat, deaths and punishments.", false);

    private final String description;
    private final boolean defaultOn;
    private ConfigValue<Boolean> value;

    Module(String description, boolean defaultOn) {
        this.description = description;
        this.defaultOn = defaultOn;
    }

    /** Adds every module switch to the {@code modules} category. */
    static void define(ConfigBuilder builder) {
        builder.category("modules");
        for (Module module : values()) {
            if (module != CORE) {
                module.value = builder.booleanValue(module.key(), module.defaultOn, module.description);
            }
        }
        builder.root();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean enabled() {
        return EssentialsGG.isModEnabled() && (value == null || value.get());
    }
}
