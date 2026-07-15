package de.staff.sus.listeners;

import de.staff.sus.manager.SuspiciousManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Einfache heuristische Erkennung fuer:
 *  - Speed-Hack
 *  - Fly-Hack
 *  - KillAura / zu hoher Reach
 *  - Autoclicker (zu hohe CPS)
 *
 * WICHTIG: Dies ist ein leichtgewichtiges Grund-Framework, kein vollwertiger Anti-Cheat.
 * Schwellenwerte sollten je nach Server (Ping, TPS, Plugins) angepasst werden.
 */
public class AntiCheatListener implements Listener {

    private final SuspiciousManager manager;

    // Bewegungstracking
    private final Map<UUID, org.bukkit.Location> lastLocation = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();

    // Klick/CPS Tracking
    private final Map<UUID, Deque<Long>> clickTimestamps = new HashMap<>();

    // Angriffstracking (KillAura)
    private final Map<UUID, Deque<Long>> hitTimestamps = new HashMap<>();

    private static final double MAX_HORIZONTAL_SPEED = 0.75; // Bloecke pro Tick (Sprint + Toleranz)
    private static final int MAX_AIR_TICKS = 30; // ca. 1.5s ohne Fallanimation
    private static final double MAX_ATTACK_REACH = 4.2; // Bloecke
    private static final int MAX_CPS = 22;
    private static final int MAX_HITS_PER_HALF_SECOND = 9;

    public AntiCheatListener(SuspiciousManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("sus.bypass")) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;

        UUID uuid = player.getUniqueId();
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // --- Speed Check ---
        if (!player.isFlying() && !player.isGliding() && !player.isInsideVehicle()
                && !player.isSwimming() && player.getVehicle() == null) {

            double allowed = MAX_HORIZONTAL_SPEED;
            if (player.hasPotionEffect(PotionEffectType.SPEED)) {
                int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
                allowed += 0.15 * amplifier;
            }

            if (horizontalDistance > allowed) {
                manager.flag(player, "Moegliches Speed-Hacking (Distanz: "
                        + String.format("%.2f", horizontalDistance) + ")");
            }
        }

        // --- Fly Check ---
        if (!player.getAllowFlight() && !player.isFlying() && !player.isGliding()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && !player.isInsideVehicle()
                && player.getVehicle() == null) {

            boolean onGround = player.isOnGround();
            boolean inLiquidOrClimbable = isInLiquidOrClimbable(player);

            if (!onGround && !inLiquidOrClimbable) {
                int ticks = airTicks.getOrDefault(uuid, 0) + 1;
                airTicks.put(uuid, ticks);

                if (ticks > MAX_AIR_TICKS && player.getFallDistance() <= 0.1f) {
                    manager.flag(player, "Moegliches Fly-Hacking (schwebt seit " + ticks + " Ticks)");
                    airTicks.put(uuid, 0); // Reset, um Spam zu vermeiden
                }
            } else {
                airTicks.put(uuid, 0);
            }
        } else {
            airTicks.put(uuid, 0);
        }

        lastLocation.put(uuid, to);
        lastMoveTime.put(uuid, System.currentTimeMillis());
    }

    private boolean isInLiquidOrClimbable(Player player) {
        org.bukkit.Material type = player.getLocation().getBlock().getType();
        String name = type.name();
        return type.isAir() == false && (
                name.contains("WATER") || name.contains("LAVA") ||
                name.contains("LADDER") || name.contains("VINE") ||
                name.contains("SCAFFOLDING")
        );
    }

    // --- CPS / Autoclicker Check ---
    @EventHandler(ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("sus.bypass")) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> clicks = clickTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        clicks.addLast(now);

        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) {
            clicks.pollFirst();
        }

        if (clicks.size() > MAX_CPS) {
            manager.flag(player, "Sehr hohe CPS erkannt (" + clicks.size() + " Klicks/Sek.)");
            clicks.clear();
        }
    }

    // --- KillAura / Reach Check ---
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.hasPermission("sus.bypass")) return;

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        // Reach-Check
        double distance = attacker.getEyeLocation().distance(event.getEntity().getLocation());
        if (distance > MAX_ATTACK_REACH) {
            manager.flag(attacker, "Reach zu hoch (" + String.format("%.2f", distance) + " Bloecke)");
        }

        // Angriffsfrequenz-Check (Hinweis auf KillAura / Auto-Clicker-Kampf)
        Deque<Long> hits = hitTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        hits.addLast(now);
        while (!hits.isEmpty() && now - hits.peekFirst() > 500) {
            hits.pollFirst();
        }

        if (hits.size() > MAX_HITS_PER_HALF_SECOND) {
            manager.flag(attacker, "Moegliches KillAura (zu viele Angriffe in kurzer Zeit)");
            hits.clear();
        }
    }

    // Platzhalter fuer zukuenftige Erweiterungen (z.B. Interact-basierte Checks)
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        // Aktuell keine Logik hier, Hook fuer spaetere Erweiterungen (z.B. Fast-Place, Scaffold)
    }
}
