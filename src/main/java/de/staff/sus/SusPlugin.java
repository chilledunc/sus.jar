package de.staff.sus;

import de.staff.sus.commands.OffendCommand;
import de.staff.sus.commands.SusBackCommand;
import de.staff.sus.commands.SusCommand;
import de.staff.sus.discord.DiscordWebhook;
import de.staff.sus.gui.SusGUI;
import de.staff.sus.listeners.AntiCheatListener;
import de.staff.sus.manager.SuspiciousManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SusPlugin extends JavaPlugin {

    private SuspiciousManager suspiciousManager;
    private SusGUI susGUI;
    private DiscordWebhook discordWebhook;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // erstellt config.yml beim ersten Start, falls nicht vorhanden

        this.discordWebhook = new DiscordWebhook(this);
        this.suspiciousManager = new SuspiciousManager(discordWebhook);
        this.susGUI = new SusGUI(suspiciousManager);

        // Listener registrieren
        getServer().getPluginManager().registerEvents(new AntiCheatListener(suspiciousManager), this);
        getServer().getPluginManager().registerEvents(susGUI, this);

        // Commands registrieren
        getCommand("sus").setExecutor(new SusCommand(susGUI));
        getCommand("offend").setExecutor(new OffendCommand(suspiciousManager, discordWebhook));
        getCommand("susback").setExecutor(new SusBackCommand(susGUI));

        getLogger().info("SusPlugin wurde aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SusPlugin wurde deaktiviert.");
    }

    public SuspiciousManager getSuspiciousManager() {
        return suspiciousManager;
    }
}
