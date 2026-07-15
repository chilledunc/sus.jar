package de.staff.sus.commands;

import de.staff.sus.discord.DiscordWebhook;
import de.staff.sus.manager.SuspiciousManager;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;

public class OffendCommand implements CommandExecutor {

    private final SuspiciousManager manager;
    private final DiscordWebhook discordWebhook;

    public OffendCommand(SuspiciousManager manager, DiscordWebhook discordWebhook) {
        this.manager = manager;
        this.discordWebhook = discordWebhook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sus.ban")) {
            sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung dafuer.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutzung: /offend <Spieler> <Grund>");
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Dieser Spieler wurde nie zuvor gesehen.");
            return true;
        }

        String staffName = sender instanceof Player ? sender.getName() : "Konsole";

        // Spieler bannen (permanent, ueber die Standard-Bukkit BanList)
        Bukkit.getBanList(BanList.Type.NAME).addBan(
                target.getName(),
                ChatColor.stripColor(ChatColor.RED + "Gebannt: " + reason + " (von " + staffName + ")"),
                (Date) null, // kein Ablaufdatum -> permanent
                staffName
        );

        // Falls online, sofort kicken
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.kickPlayer(ChatColor.RED + "Du wurdest gebannt!\n"
                    + ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason);
        }

        // Verdachtseintrag entfernen, Fall ist erledigt
        manager.clear(target.getUniqueId());

        // Discord-Benachrichtigung senden (asynchron, blockiert den Server nicht)
        if (discordWebhook != null) {
            discordWebhook.sendBanNotification(target.getName(), reason, staffName);
        }

        String broadcast = ChatColor.DARK_RED + "[Ban] " + ChatColor.YELLOW + target.getName()
                + ChatColor.GRAY + " wurde von " + ChatColor.YELLOW + staffName
                + ChatColor.GRAY + " gebannt. Grund: " + ChatColor.WHITE + reason;

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("sus.notify")) {
                staff.sendMessage(broadcast);
            }
        }
        sender.sendMessage(ChatColor.GREEN + "Spieler " + target.getName() + " wurde gebannt.");

        return true;
    }
}
