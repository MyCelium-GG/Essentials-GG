package my.celium.org.essentialsgg.module;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.essentialsgg.EssentialsGG;
import my.celium.org.essentialsgg.Module;
import my.celium.org.logging.MycelLog;
import net.minecraft.server.level.ServerPlayer;

/**
 * Posts server events to a Discord channel through a webhook: joins, leaves,
 * chat, deaths and punishments, each switchable. Messages are sent off the
 * server thread and never ping anyone ({@code allowed_mentions} is empty).
 */
public final class Discord {
    public static ConfigValue<String> webhookUrl;
    public static ConfigValue<String> username;
    public static ConfigValue<Boolean> joins;
    public static ConfigValue<Boolean> chat;
    public static ConfigValue<Boolean> deaths;
    public static ConfigValue<Boolean> punishments;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile long warnedAt;

    private Discord() {
    }

    public static void config(ConfigBuilder builder) {
        builder.category("discord");
        webhookUrl = builder.stringValue("webhookUrl", "", 256,
                "Channel webhook (Channel settings > Integrations > Webhooks). Empty sends nothing.");
        username = builder.stringValue("username", "", 80, "Name the webhook posts under; empty keeps the webhook's own.");
        joins = builder.booleanValue("joins", true, "Post joins and leaves.");
        chat = builder.booleanValue("chat", true, "Post chat messages (needs the chat module's format on).");
        deaths = builder.booleanValue("deaths", true, "Post player deaths.");
        punishments = builder.booleanValue("punishments", true, "Post tempbans, unbans, mutes and warnings.");
        builder.root();
    }

    private static boolean on(ConfigValue<Boolean> event) {
        return Module.DISCORD.enabled() && event.get() && webhookUrl.get().startsWith("https://");
    }

    public static void onJoin(ServerPlayer player) {
        if (on(joins)) {
            post("**" + escape(player.getGameProfile().name()) + "** joined the server");
        }
    }

    public static void onLeave(ServerPlayer player) {
        if (on(joins)) {
            post("**" + escape(player.getGameProfile().name()) + "** left the server");
        }
    }

    public static void onChat(ServerPlayer player, String message) {
        if (on(chat)) {
            post("**" + escape(player.getGameProfile().name()) + "**: " + escape(message));
        }
    }

    public static void onDeath(ServerPlayer player) {
        if (on(deaths)) {
            post(escape(player.getCombatTracker().getDeathMessage().getString()));
        }
    }

    public static void punishment(String kind, String player, String actor, String reason, String time) {
        if (!on(punishments)) {
            return;
        }
        StringBuilder text = new StringBuilder("**").append(escape(player)).append("** ")
                .append(switch (kind) {
                    case "tempban" -> "was banned";
                    case "unban" -> "was unbanned";
                    case "mute" -> "was muted";
                    case "warn" -> "was warned";
                    default -> kind;
                })
                .append(" by ").append(escape(actor));
        if (time != null) {
            text.append(" for ").append(escape(time));
        }
        if (reason != null) {
            text.append(": ").append(escape(reason));
        }
        post(text.toString());
    }

    /** Markdown and mention characters lose their meaning. */
    static String escape(String text) {
        return text.replaceAll("([\\\\*_~`|>#\\[\\]()-])", "\\\\$1").replace("@", "@​");
    }

    private static void post(String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content.length() > 1900 ? content.substring(0, 1900) + "…" : content);
        if (!username.get().isBlank()) {
            body.addProperty("username", username.get());
        }
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhookUrl.get()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException e) {
            warn("Discord webhook URL is not valid: " + e.getMessage());
            return;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                warn("Discord webhook failed: " + error);
            } else if (response.statusCode() >= 300) {
                warn("Discord webhook answered HTTP " + response.statusCode());
            }
        });
    }

    /** At most one warning a minute, so a dead webhook does not flood the log. */
    private static void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - warnedAt > 60_000) {
            warnedAt = now;
            MycelLog.warnMod(EssentialsGG.ID, message);
        }
    }
}
