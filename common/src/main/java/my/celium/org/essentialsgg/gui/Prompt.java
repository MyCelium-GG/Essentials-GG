package my.celium.org.essentialsgg.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import my.celium.org.essentialsgg.text.Lang;
import net.minecraft.server.level.ServerPlayer;

/**
 * Asks a player to type something in chat: their next chat message is the
 * answer and is never shown to anyone. {@code cancel} (or a minute passing)
 * drops the question. Menus use it for names and custom values.
 */
public final class Prompt {
    private static final long TIMEOUT_MS = 60_000;

    private record Question(long expires, Consumer<String> answer) {
    }

    private static final Map<UUID, Question> OPEN = new HashMap<>();

    private Prompt() {
    }

    /** Closes the player's menu, shows {@code key} and waits for their next chat line. */
    public static void ask(ServerPlayer player, String key, Consumer<String> answer, Object... args) {
        player.closeContainer();
        OPEN.put(player.getUUID(), new Question(System.currentTimeMillis() + TIMEOUT_MS, answer));
        Lang.send(player, key, args);
        Lang.send(player, "prompt.hint");
    }

    /** Chat hook: {@code true} when the line answered a question (and must not reach chat). */
    public static boolean answer(ServerPlayer player, String raw) {
        Question question = OPEN.remove(player.getUUID());
        if (question == null || question.expires < System.currentTimeMillis()) {
            return false;
        }
        String text = raw.strip();
        if (text.equalsIgnoreCase("cancel")) {
            Lang.send(player, "prompt.cancelled");
            return true;
        }
        question.answer.accept(text);
        return true;
    }

    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }
}
