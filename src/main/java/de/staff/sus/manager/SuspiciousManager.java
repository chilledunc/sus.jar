package de.staff.sus.manager;

import de.staff.sus.discord.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zentrale Verwaltung aller Spieler, die verdaechtiges Verhalten gezeigt haben.
 * Eintraege bleiben nur waehrend der aktuellen Serverlaufzeit / Session erhalten.
 */
public class SuspiciousManager {

    // LinkedHashMap, damit die Reihenfolge (zuletzt gemeldet zuerst) stabil bleibt
    private final Map<UUID, SuspiciousEntry> suspects = new LinkedHashMap<>();

    // Cooldown, damit derselbe Spieler nicht im Sekundentakt neue Chat-Nachrichten spammt
    private final Map<UUID, Long> lastNotify = new java.util.HashMap<>();
    private static final long NOTIFY_COOLDOWN_MS = 5000L;

    private final DiscordWebhook discordWebhook;

    public SuspiciousManager(DiscordWebhook discordWebhook) {
        this.discordWebhook = discordWebhook;
    }

    public void flag(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        SuspiciousEntry entry = suspects.computeIfAbsent(uuid, k -> new SuspiciousEntry());
        entry.addFlag(reason);

        notifyStaff(player, reason, entry.getScore());

        if (discordWebhook != null) {
            discordWebhook.sendFlagNotification(player.getName(), reason, entry.getScore());
        }
    }

    private void notifyStaff(Player player, String reason, int score) {
        long now = System.currentTimeMillis();
        long last = lastNotify.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < NOTIFY_COOLDOWN_MS) {
            return;
        }
        lastNotify.put(player.getUniqueId(), now);

        String message = ChatColor.DARK_RED + "[SUS] " + ChatColor.YELLOW + player.getName()
                + ChatColor.GRAY + " -> " + ChatColor.WHITE + reason
                + ChatColor.GRAY + " (Score: " + score + ")";

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("sus.notify")) {
                staff.sendMessage(message);
            }
        }
    }

    public Map<UUID, SuspiciousEntry> getSuspects() {
        return suspects;
    }

    public SuspiciousEntry getEntry(UUID uuid) {
        return suspects.get(uuid);
    }

    public void clear(UUID uuid) {
        suspects.remove(uuid);
    }
}
