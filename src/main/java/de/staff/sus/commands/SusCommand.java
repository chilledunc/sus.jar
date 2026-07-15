package de.staff.sus.commands;

import de.staff.sus.gui.SusGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SusCommand implements CommandExecutor {

    private final SusGUI gui;

    public SusCommand(SusGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgefuehrt werden.");
            return true;
        }

        if (!player.hasPermission("sus.staff")) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung dafuer.");
            return true;
        }

        gui.open(player);
        return true;
    }
}
