package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatStatsManager implements Listener {
    private static final long COMBAT_MILLIS = 20_000L;
    private final ServerToolkit plugin;
    private final File statsFile;
    private final File stateFile;
    private final YamlConfiguration stats;
    private final YamlConfiguration state;
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, UUID> opponents = new HashMap<>();
    private final Map<UUID, Long> playtimeFlush = new HashMap<>();
    private final Set<UUID> forcedSpawn = new HashSet<>();

    public CombatStatsManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.stateFile = new File(plugin.getDataFolder(), "combat-state.yml");
        this.stats = YamlConfiguration.loadConfiguration(statsFile);
        this.state = YamlConfiguration.loadConfiguration(stateFile);
        for (String id : state.getStringList("forced-spawn")) {
            try { forcedSpawn.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::flushAllPlaytime, 20L * 60L, 20L * 60L);
    }

    private boolean enabled() { return !plugin.isLobbyMode(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        if (!enabled() || event.getFinalDamage() <= 0 || !(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim) || attacker.getWorld() != victim.getWorld()) return;
        tag(attacker, victim);
        tag(victim, attacker);
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private void tag(Player player, Player opponent) {
        long until = System.currentTimeMillis() + COMBAT_MILLIS;
        combatUntil.put(player.getUniqueId(), until);
        opponents.put(player.getUniqueId(), opponent.getUniqueId());
        player.sendActionBar(ChatColor.RED + "⚔ Im Kampf: 20 Sekunden");
    }

    private void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long until = combatUntil.get(player.getUniqueId());
            if (until == null) continue;
            long left = until - now;
            if (left <= 0) {
                combatUntil.remove(player.getUniqueId());
                opponents.remove(player.getUniqueId());
                player.sendActionBar(ChatColor.GREEN + "Du bist nicht mehr im Kampf.");
            } else {
                player.sendActionBar(ChatColor.RED + "⚔ Im Kampf: " + ((left + 999) / 1000) + "s");
            }
        }
    }

    public boolean isInCombat(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            combatUntil.remove(player.getUniqueId());
            opponents.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !isInCombat(player) || isAdmin(player)) return;
        String raw = event.getMessage().substring(1).trim();
        if (raw.isEmpty()) return;
        String root = raw.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.lastIndexOf(':') + 1);
        if (root.equals("ah") || root.equals("order") || root.equals("orders")) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Du bist im Kampf! Erlaubt sind nur /ah, /order und /orders.");
    }

    private boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("servertoolkit.admin")
                || player.hasPermission("servertoolkit.combat.bypass");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        Player victim = event.getEntity();
        addDeath(victim.getUniqueId(), victim.getName());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            addKill(killer.getUniqueId(), killer.getName());
        } else if (isInCombat(victim)) {
            UUID opponentId = opponents.get(victim.getUniqueId());
            Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);
            if (opponent != null) addKill(opponent.getUniqueId(), opponent.getName());
        }
        combatUntil.remove(victim.getUniqueId());
        opponents.remove(victim.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushPlaytime(player.getUniqueId(), player.getName());
        playtimeFlush.remove(player.getUniqueId());
        if (!enabled() || !isInCombat(player)) {
            combatUntil.remove(player.getUniqueId());
            opponents.remove(player.getUniqueId());
            return;
        }
        Location location = player.getLocation();
        dropInventory(player, location);
        addDeath(player.getUniqueId(), player.getName());
        UUID opponentId = opponents.get(player.getUniqueId());
        Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);
        if (opponent != null) addKill(opponent.getUniqueId(), opponent.getName());
        forcedSpawn.add(player.getUniqueId());
        saveForcedSpawn();
        combatUntil.remove(player.getUniqueId());
        opponents.remove(player.getUniqueId());
        player.sendMessage(ChatColor.RED + "Kampf-Logout: Du bist gestorben und deine Items wurden gedroppt.");
    }

    private void dropInventory(Player player, Location location) {
        for (ItemStack item : player.getInventory().getContents()) drop(item, location);
        for (ItemStack item : player.getInventory().getArmorContents()) drop(item, location);
        drop(player.getInventory().getItemInOffHand(), location);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void drop(ItemStack item, Location location) {
        if (item != null && !item.getType().isAir()) location.getWorld().dropItemNaturally(location, item.clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playtimeFlush.put(player.getUniqueId(), System.currentTimeMillis());
        if (!enabled() || !forcedSpawn.contains(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Location spawn = plugin.loadSpawn();
            if (spawn == null) spawn = player.getWorld().getSpawnLocation();
            player.teleport(spawn);
            forcedSpawn.remove(player.getUniqueId());
            saveForcedSpawn();
            player.sendMessage(ChatColor.RED + "Du wurdest nach deinem Kampf-Logout am Spawn wiederbelebt.");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!enabled() || !forcedSpawn.remove(id)) return;
        Location spawn = plugin.loadSpawn();
        if (spawn == null) spawn = event.getPlayer().getWorld().getSpawnLocation();
        event.setRespawnLocation(spawn);
        saveForcedSpawn();
    }

    private void flushAllPlaytime() {
        for (Player player : Bukkit.getOnlinePlayers()) flushPlaytime(player.getUniqueId(), player.getName());
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) flushPlaytime(player.getUniqueId(), player.getName());
    }

    private void flushPlaytime(UUID id, String name) {
        Long last = playtimeFlush.get(id);
        long now = System.currentTimeMillis();
        if (last == null) { playtimeFlush.put(id, now); return; }
        long seconds = Math.max(0, (now - last) / 1000L);
        if (seconds > 0) {
            String path = "players." + id;
            stats.set(path + ".name", name);
            stats.set(path + ".playtime-seconds", stats.getLong(path + ".playtime-seconds") + seconds);
            playtimeFlush.put(id, last + seconds * 1000L);
            saveStats();
        }
    }

    private void addKill(UUID id, String name) { addCounter(id, name, "kills"); }
    private void addDeath(UUID id, String name) { addCounter(id, name, "deaths"); }
    private void addCounter(UUID id, String name, String key) {
        String path = "players." + id;
        stats.set(path + ".name", name);
        stats.set(path + "." + key, stats.getInt(path + "." + key) + 1);
        saveStats();
    }

    private void saveStats() {
        try { stats.save(statsFile); }
        catch (IOException ex) { plugin.getLogger().severe("stats.yml konnte nicht gespeichert werden: " + ex.getMessage()); }
    }

    private void saveForcedSpawn() {
        state.set("forced-spawn", forcedSpawn.stream().map(UUID::toString).toList());
        try { state.save(stateFile); }
        catch (IOException ex) { plugin.getLogger().severe("combat-state.yml konnte nicht gespeichert werden: " + ex.getMessage()); }
    }

    public void handleStats(Player viewer, String[] args) {
        OfflinePlayer target = args.length == 0 ? viewer : Bukkit.getOfflinePlayer(args[0]);
        UUID id = target.getUniqueId();
        String path = "players." + id;
        String name = stats.getString(path + ".name", target.getName() == null ? "Unbekannt" : target.getName());
        long playtime = stats.getLong(path + ".playtime-seconds");
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            Long last = playtimeFlush.get(id);
            if (last != null) playtime += Math.max(0, (System.currentTimeMillis() - last) / 1000L);
        }
        viewer.sendMessage(ChatColor.GOLD + "§lStats von " + ChatColor.YELLOW + name);
        viewer.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + stats.getInt(path + ".kills"));
        viewer.sendMessage(ChatColor.GRAY + "Deaths: " + ChatColor.RED + stats.getInt(path + ".deaths"));
        viewer.sendMessage(ChatColor.GRAY + "Playtime: " + ChatColor.AQUA + formatDuration(playtime));
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
