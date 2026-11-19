package my.celium.org.essentialsgg;

import java.net.URI;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * What happens when the mod is installed without Mycel: the game still starts, the mod stays idle,
 * and every player who joins gets one chat line saying Mycel is missing, with a link to download it.
 *
 * <p>This class must never touch a Mycel class: it is the only part of the mod that runs when
 * Mycel is not there. Only vanilla classes are used.
 */
public final class MissingMycel {
    static final String NAME = "Essentials GG";
    public static final String MYCEL_URL = "https://www.curseforge.com/minecraft/mc-mods/mycel";

    private static final int RED = 0xFF3B3B;
    private static final int BODY = 0xF4F4F5;
    private static final int FAINT = 0x9A9AA3;
    private static final int LINK = 0xFBBF24;

    private static boolean logged;

    private MissingMycel() {
    }

    /** One warning in the log, so owners see it without joining. */
    public static synchronized void logOnce() {
        if (!logged) {
            logged = true;
            LogUtils.getLogger().warn("{} is installed without Mycel and does nothing until Mycel is installed: {}",
                    NAME, MYCEL_URL);
        }
    }

    /** Tells a player who just joined that the mod cannot work yet. */
    public static void tell(ServerPlayer player) {
        if (player != null) {
            player.sendSystemMessage(message());
        }
    }

    /** {@code Essentials GG » Mycel is missing. ... [Download Mycel]} */
    public static Component message() {
        MutableComponent link = Component.translatableWithFallback("essentialsgg.missing_mycel.download", "[Download Mycel]")
                .withStyle(Style.EMPTY
                .withColor(LINK)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(MYCEL_URL)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(MYCEL_URL))));
        return Component.empty()
                .append(Component.literal(NAME).withStyle(Style.EMPTY.withColor(RED).withBold(true)))
                .append(Component.literal(" » ").withStyle(Style.EMPTY.withColor(FAINT)))
                .append(Component.translatableWithFallback("essentialsgg.missing_mycel",
                        "Mycel is missing. %s needs the Mycel library to work and stays off until it is installed.", NAME)
                        .withStyle(Style.EMPTY.withColor(BODY)))
                .append(" ")
                .append(link);
    }
}
