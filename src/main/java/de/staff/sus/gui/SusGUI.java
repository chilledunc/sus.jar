package de.staff.sus.gui;

import de.staff.sus.manager.SuspiciousEntry;
import de.staff.sus.manager.SuspiciousManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Erstellt und verwaltet die /sus GUI.
 */
public class SusGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_RED + "Verdaechtige Spieler";

    private final SuspiciousManager manager;

    // Speichert den Zustand des Staff-Mitglieds vor dem Spectaten, um /susback zu ermoeglichen
    private final Map<UUID, GameMode> previousGameMode = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> previousLocation = new HashMap<>();

    public SusGUI(SuspiciousManager manager) {
        this.manager = manager;
    }

    public void open(Player staff) {
        Map<UUID, SuspiciousEntry> suspects = manager.getSuspects();

        int size = Math.max(9, ((suspects.size() / 9) + 1) * 9);
        size = Math.min(size, 54);

        Inventory inventory = Bukkit.createInventory(null, size, TITLE);

        if (suspects.isEmpty()) {
            ItemStack info = new ItemStack(Material.BARRIER);
            var meta = info.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "Keine verdaechtigen Spieler");
            meta.setLore(List.of(ChatColor.GRAY + "Aktuell wurden keine Cheats erkannt."));
            info.setItemMeta(meta);
            inventory.setItem(4, info);
            staff.openInventory(inventory);
            return;
        }

        int slot = 0;
        for (Map.Entry<UUID, SuspiciousEntry> entry : suspects.entrySet()) {
            if (slot >= size) break;

            Player target = Bukkit.getPlayer(entry.getKey());
            String name = target != null ? target.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Unbekannt";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getKey()));
            meta.setDisplayName(ChatColor.YELLOW + name
                    + (target == null ? ChatColor.GRAY + " (offline)" : ""));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Score: " + ChatColor.RED + entry.getValue().getScore());
            lore.add("");
            lore.add(ChatColor.GRAY + "Letzte Meldungen:");
            List<SuspiciousEntry.Flag> flags = entry.getValue().getFlags();
            int shown = 0;
            for (int i = flags.size() - 1; i >= 0 && shown < 5; i--, shown++) {
                lore.add(ChatColor.DARK_GRAY + "- " + ChatColor.WHITE + flags.get(i).getReason());
            }
            lore.add("");
            if (target != null) {
                lore.add(ChatColor.GREEN + "Klicken zum Spectaten");
            } else {
                lore.add(ChatColor.RED + "Spieler ist offline");
            }
            meta.setLore(lore);
            head.setItemMeta(meta);

            inventory.setItem(slot, head);
            slot++;
        }

        staff.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player staff)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;
        if (meta.getOwningPlayer() == null) return;

        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null || !target.isOnline()) {
            staff.sendMessage(ChatColor.RED + "Dieser Spieler ist nicht mehr online.");
            return;
        }

        spectate(staff, target);
    }

    /**
     * Versetzt den Staff in den Spectator-Modus und "haengt" ihn an den Zielspieler.
     */
    private void spectate(Player staff, Player target) {
        staff.closeInventory();

        UUID staffId = staff.getUniqueId();
        // Nur beim ersten Spectaten den urspruenglichen Zustand merken (nicht ueberschreiben,
        // falls Staff direkt von einem Spectate-Ziel zum naechsten wechselt)
        if (!previousGameMode.containsKey(staffId)) {
            previousGameMode.put(staffId, staff.getGameMode());
            previousLocation.put(staffId, staff.getLocation());
        }

        if (staff.getGameMode() != GameMode.SPECTATOR) {
            staff.setGameMode(GameMode.SPECTATOR);
        }

        staff.teleport(target.getLocation());
        staff.setSpectatorTarget(target);

        staff.sendMessage(ChatColor.GREEN + "Du spectatest jetzt " + ChatColor.YELLOW + target.getName()
                + ChatColor.GREEN + ". Mit " + ChatColor.YELLOW + "/susback" + ChatColor.GREEN
                + " kehrst du zurueck.");
    }

    /**
     * Stellt den Zustand (Gamemode + Position) des Staff-Mitglieds vor dem Spectaten wieder her.
     * Wird vom /susback Command aufgerufen.
     */
    public boolean restore(Player staff) {
        UUID staffId = staff.getUniqueId();
        if (!previousGameMode.containsKey(staffId)) {
            return false;
        }

        staff.setSpectatorTarget(null);
        staff.setGameMode(previousGameMode.remove(staffId));
        org.bukkit.Location loc = previousLocation.remove(staffId);
        if (loc != null) {
            staff.teleport(loc);
        }
        return true;
    }
}
