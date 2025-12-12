package com.jordanmakes.vdthelper;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VDTHelper extends JavaPlugin implements Listener {

    private final Map<String, Integer> vacantView = new HashMap<>(); // Minimum view distance per world
    private final Map<String, Integer> vacantSim = new HashMap<>(); // Minimum simulation distance per world
    private final Map<String, Long> worldVacatedAt = new HashMap<>(); // last time a world was vacated

    private int cooldownTicks; // time in ticks since a world became vacant before applying reductions

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("ViewDistanceTweaks") == null) {
            getLogger().warning("ViewDistanceTweaks is not loaded - Commands may fail.");
        }

        saveDefaultConfig();
        loadConfigValues();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadConfigValues() {
        cooldownTicks = getConfig().getInt("cooldown-seconds", 10) * 20; // Convert seconds to ticks

        ConfigurationSection section = getConfig().getConfigurationSection("worlds");
        if (section != null) {
            for (String world : section.getKeys(false)) {
                vacantView.put(world, getConfig().getInt("worlds." + world + ".view-distance"));
                vacantSim.put(world, getConfig().getInt("worlds." + world + ".simulation-distance"));
            }
        }
    }

    // === EVENT TRIGGERS ===

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scheduleCheck();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        scheduleCheck();
    }

    @EventHandler
    public void onChange(PlayerChangedWorldEvent e) {
        scheduleCheck();
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        scheduleCheck();
    }

    private void scheduleCheck() {
        // 1-second/20-tick wait to account for processing delays
        Bukkit.getScheduler().runTaskLater(this, this::handleVacancyChange, 20L);
    }

    /*
     * Handle world vacancy changes by checking if a world has become empty and marking the time it did so.
     * If a world has been empty for the cooldown period, reduce its view and simulation distances.
    */
    private void handleVacancyChange() {
        long now = System.currentTimeMillis();
        
        for (String worldName : vacantView.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                getLogger().warning(() -> "World not found, skipping: " + worldName);
                continue;
            }
            getLogger().info(() -> "DEBUG: world=" + worldName + " players=" + w.getPlayers().size());

            boolean empty = w.getPlayers().isEmpty();

            if (empty) {
                // Mark the time it became empty (only if newly empty)
                worldVacatedAt.putIfAbsent(worldName, now);
            } else if (worldVacatedAt.containsKey(worldName)) {
                // A player is here AND the world was previously empty -> reload needed
                getLogger().info(() -> "World " + worldName + " is now populated. Reloading VDT...");
                runCommand("viewdistancetweaks reload");
                worldVacatedAt.remove(worldName);
            }
        }

        // Evaluate reductions after cooldown
        Bukkit.getScheduler().runTaskLater(this, this::checkVacantWorlds, cooldownTicks);
    }

    // === CORE LOGIC ===

    private void checkVacantWorlds() {
        long now = System.currentTimeMillis();

        // Clean up any worlds that now have players; population reloads are handled immediately in handleVacancyChange
        for (String worldName : vacantView.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                getLogger().warning(() -> "World not found, skipping: " + worldName);
                continue;
            }

            if (!w.getPlayers().isEmpty()) worldVacatedAt.remove(worldName);
        }

        boolean willReduce = false;
        for (String worldName : vacantView.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;

            if (!w.getPlayers().isEmpty()) continue;

            Long whenVacant = worldVacatedAt.get(worldName);
            if (whenVacant == null) continue;

            long elapsed = now - whenVacant;
            if (elapsed >= (cooldownTicks * 50L)) {
                willReduce = true;
                break;
            }
        }

        if (willReduce) {
            getLogger().info("Preparing reductions. Reloading ViewDistanceTweaks...");
            runCommand("viewdistancetweaks reload");

            // Delay reduction pass by 10 ticks to let VDT finish reloading
            Bukkit.getScheduler().runTaskLater(this, () -> applyReductions(System.currentTimeMillis()), 10L);
            return;
        }

        // Pass 2: apply reductions when no delayed reload is pending
        applyReductions(now);
    }

    private void applyReductions(long now) {
        for (String worldName : vacantView.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;

            if (!w.getPlayers().isEmpty()) continue;

            // Was this world marked as vacant?
            Long whenVacant = worldVacatedAt.get(worldName);
            if (whenVacant == null) continue;

            // Has cooldown passed?
            long elapsed = now - whenVacant;
            if (elapsed < (cooldownTicks * 50L)) continue; // ticks to ms

            // Apply distance reduction
            int view = vacantView.get(worldName);
            int sim = vacantSim.get(worldName);
            getLogger().info(() -> "Reducing " + worldName + " to view=" + view + " sim=" + sim);
            applyVDT(worldName, view, sim);

            // Remove the world from the vacant list to avoid reapplying
            worldVacatedAt.remove(worldName);
        }
    }

    // === CALL VDT COMMANDS ===

    private void applyVDT(String world, int view, int sim) {
        runCommand("viewdistancetweaks viewdistance " + view + " " + world);
        runCommand("viewdistancetweaks simulationdistance " + sim + " " + world);
    }

    private void runCommand(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
