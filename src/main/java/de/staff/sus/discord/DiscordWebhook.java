package de.staff.sus.discord;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Einfacher Discord-Webhook-Client fuer Ban- und (optional) Verdachts-Benachrichtigungen.
 * Alle Netzwerk-Aufrufe laufen asynchron, um den Server-Main-Thread nicht zu blockieren.
 */
public class DiscordWebhook {

    private final JavaPlugin plugin;

    public DiscordWebhook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    private String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    private String getUsername() {
        return plugin.getConfig().getString("discord.username", "SusPlugin");
    }

    /**
     * Sendet eine Ban-Benachrichtigung (rot) an den konfigurierten Webhook.
     */
    public void sendBanNotification(String playerName, String reason, String staffName) {
        if (!isEnabled()) return;

        String description = "**Spieler:** " + escape(playerName) + "\n"
                + "**Grund:** " + escape(reason) + "\n"
                + "**Gebannt von:** " + escape(staffName);

        sendEmbed(":hammer: Spieler gebannt", description, 0xE02B2B); // Rot
    }

    /**
     * Sendet eine Verdachts-Benachrichtigung (gelb) an den konfigurierten Webhook.
     * Wird nur gesendet, wenn discord.notify-on-flag = true in der config.yml gesetzt ist.
     */
    public void sendFlagNotification(String playerName, String reason, int score) {
        if (!isEnabled()) return;
        if (!plugin.getConfig().getBoolean("discord.notify-on-flag", false)) return;

        String description = "**Spieler:** " + escape(playerName) + "\n"
                + "**Meldung:** " + escape(reason) + "\n"
                + "**Aktueller Score:** " + score;

        sendEmbed(":warning: Verdaechtige Aktivitaet", description, 0xF2C744); // Gelb
    }

    private void sendEmbed(String title, String description, int colorHex) {
        String url = getWebhookUrl();
        if (url == null || url.isBlank()) {
            plugin.getLogger().warning("Discord-Webhook ist aktiviert, aber keine webhook-url in der config.yml gesetzt.");
            return;
        }

        String json = buildEmbedJson(title, description, colorHex);

        // Asynchron ausfuehren, damit ein langsamer/fehlerhafter Webhook-Call
        // niemals den Server-Tick blockiert.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                postJson(url, json);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Discord-Webhook konnte nicht gesendet werden: " + e.getMessage());
            }
        });
    }

    private void postJson(String webhookUrl, String json) throws IOException {
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        // Discord antwortet bei Erfolg typischerweise mit 204 (No Content)
        if (responseCode >= 300) {
            plugin.getLogger().warning("Discord-Webhook antwortete mit Status " + responseCode);
        }
        connection.disconnect();
    }

    private String buildEmbedJson(String title, String description, int colorHex) {
        String username = getUsername();
        String timestamp = Instant.now().toString();

        // Minimalistischer, manueller JSON-Aufbau (keine externe Library noetig)
        return "{"
                + "\"username\": \"" + escape(username) + "\","
                + "\"embeds\": [{"
                + "\"title\": \"" + escape(title) + "\","
                + "\"description\": \"" + escape(description) + "\","
                + "\"color\": " + colorHex + ","
                + "\"timestamp\": \"" + timestamp + "\""
                + "}]"
                + "}";
    }

    /**
     * Escaped Zeichen, die in JSON-Strings problematisch waeren (Anfuehrungszeichen,
     * Backslashes, Zeilenumbrueche werden bewusst NICHT escaped, da \n in Discord
     * Markdown fuer Zeilenumbrueche gewollt ist - stattdessen wird das rohe \n Zeichen
     * durch die JSON-Escape-Sequenz \\n ersetzt).
     */
    private String escape(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
