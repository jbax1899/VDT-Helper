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
import org.bukkit.plugin.java.JavaPlugin;

public class VDTHelperPlugin extends JavaPlugin implements Listener {

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

        getLogger().info("VDT Helper enabled. Running initial vacancy checks...");
        handleVacancyChange();
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
        // Clears the cooldown for the joined world if it was previously vacant
        handleVacancyChange();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Checks if the player's world has become empty after they left
        handleVacancyChange();
    }

    @EventHandler
    public void onChange(PlayerChangedWorldEvent e) {
        // Checks if the player's previous world has become empty after they left
        handleVacancyChange();
    }

    /*
     * Handle world vacancy changes by checking if a world has become empty and marking the time it did so.
     * If a world has been empty for the cooldown period, reduce its view and simulation distances.
    */
    private void handleVacancyChange() {
        long now = System.currentTimeMillis();
        boolean shouldSchedule = false;

        for (String worldName : vacantView.keySet()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                getLogger().warning(() -> "World not found, skipping: " + worldName);
                continue;
            }

            if (w.getPlayers().isEmpty()) {
                // Mark the time it became empty (only if newly empty)
                worldVacatedAt.putIfAbsent(worldName, now);
                shouldSchedule = true;
            } else {
                // A player is there, cancel vacancy state
                worldVacatedAt.remove(worldName);
            }
        }

        // If any world became vacant,
        if (shouldSchedule) {
            // Create a task to check if any worlds remain vacant after the cooldown period,
            // at which point we can apply the distance reductions.
            Bukkit.getScheduler().runTaskLater(this, this::checkVacantWorlds, cooldownTicks);
        }
    }

    // === CORE LOGIC ===

    private void checkVacantWorlds() {
        long now = System.currentTimeMillis();

        for (String worldName : vacantView.keySet()) {

            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                getLogger().warning(() -> "World not found, skipping: " + worldName);
                continue;
            }

            // Not empty anymore - skip
            if (!w.getPlayers().isEmpty()) {
                continue;
            }

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
