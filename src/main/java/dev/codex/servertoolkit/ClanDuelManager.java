package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ClanDuelManager implements Listener {
    private final ServerToolkit plugin;
    private final ClanManager clans;
    private Match activeMatch;

    public ClanDuelManager(ServerToolkit plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servertoolkit.clanduel") && !sender.isOp()) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (activeMatch == null) sender.sendMessage(color("&cEs läuft gerade kein Clan-Duel."));
            else finish(null, "Das Clan-Duel wurde administrativ beendet.");
            return;
        }
        if (activeMatch != null) {
            sender.sendMessage(color("&cEs läuft bereits ein Clan-Duel. Nutze /clanduel stop."));
            return;
        }
        List<String> eligible = new ArrayList<>(clans.clanKeysWithOnlineMembers());
        if (eligible.size() < 2) {
            sender.sendMessage(color("&cEs müssen mindestens zwei Clans mit Online-Mitgliedern existieren."));
            return;
        }
        Collections.shuffle(eligible);
        String first = eligible.get(0);
        String second = eligible.get(1);
        List<Player> firstPlayers = clans.onlineMembers(first);
        List<Player> secondPlayers = clans.onlineMembers(second);
        if (firstPlayers.isEmpty() || secondPlayers.isEmpty()) {
            sender.sendMessage(color("&cDie zufälligen Clans haben keine Online-Mitglieder. Bitte erneut versuchen."));
            return;
        }
        String worldName = plugin.getConfig().getString("clanduel.world",
                plugin.getConfig().getString("rtp.worlds.survival", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(color("&cDie Clan-Duel-Welt &f" + worldName + " &cist nicht geladen."));
            return;
        }
        Location center = findArena(world);
        if (center == null) {
            sender.sendMessage(color("&cKein sicherer Kampfplatz gefunden. Prüfe clanduel.world und den Welt-Spawn."));
            return;
        }
        Location firstSpawn = findOffset(world, center, -7, 0);
        Location secondSpawn = findOffset(world, center, 7, 0);
        if (firstSpawn == null || secondSpawn == null) {
            sender.sendMessage(color("&cDie beiden Team-Startpunkte sind nicht sicher."));
            return;
        }

        Set<UUID> firstIds = new HashSet<>();
        Set<UUID> secondIds = new HashSet<>();
        Map<UUID, Location> returnLocations = new HashMap<>();
        for (Player player : firstPlayers) {
            firstIds.add(player.getUniqueId());
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        for (Player player : secondPlayers) {
            secondIds.add(player.getUniqueId());
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        activeMatch = new Match(first, second, firstIds, secondIds, returnLocations, firstSpawn, secondSpawn);
        for (Player player : firstPlayers) {
            player.teleport(firstSpawn.clone().add(0, 0, ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
            player.sendMessage(color("&6Clan-Duel! &eDein Team: &f" + clans.displayName(first)
                    + " &7gegen &f" + clans.displayName(second)));
        }
        for (Player player : secondPlayers) {
            player.teleport(secondSpawn.clone().add(0, 0, ThreadLocalRandom.current().nextDouble(-1.0, 1.0)));
            player.sendMessage(color("&6Clan-Duel! &eDein Team: &f" + clans.displayName(second)
                    + " &7gegen &f" + clans.displayName(first)));
        }
        Bukkit.broadcastMessage(color("&6&lCLAN-DUEL &e" + clans.displayName(first) + " &7vs. &c"
                + clans.displayName(second) + " &8| &f" + (firstPlayers.size() + secondPlayers.size())
                + " Spieler wurden zur Arena teleportiert!"));
    }

    private Location findArena(World world) {
        int min = Math.max(0, plugin.getConfig().getInt("rtp.min-radius", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-radius", 5000));
        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 100; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int radius = ThreadLocalRandom.current().nextInt(min, max + 1);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location safe = safeAt(world, x, world.getHighestBlockYAt(x, z), z);
            if (safe != null) return safe;
        }
        return null;
    }

    private Location findOffset(World world, Location center, int dx, int dz) {
        for (int radius = 0; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Location safe = safeAt(world, center.getBlockX() + dx + x, world.getHighestBlockYAt(
                            center.getBlockX() + dx + x, center.getBlockZ() + dz + z), center.getBlockZ() + dz + z);
                    if (safe != null) return safe;
                }
            }
        }
        return null;
    }

    private Location safeAt(World world, int x, int floorY, int z) {
        if (floorY <= world.getMinHeight() || floorY + 2 >= world.getMaxHeight()) return null;
        Location probe = new Location(world, x + 0.5, floorY + 1, z + 0.5);
        if (!world.getWorldBorder().isInside(probe)) return null;
        Block floor = world.getBlockAt(x, floorY, z);
        Block feet = world.getBlockAt(x, floorY + 1, z);
        Block head = world.getBlockAt(x, floorY + 2, z);
        if (!floor.getType().isSolid() || feet.getType().isSolid() || head.getType().isSolid()
                || floor.isLiquid() || feet.isLiquid() || head.isLiquid()) return null;
        Material type = floor.getType();
        if (type == Material.MAGMA_BLOCK || type == Material.CACTUS || type == Material.FIRE
                || type == Material.SOUL_FIRE || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                || type == Material.LAVA) return null;
        return probe;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDuelDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player direct) attacker = direct;
        else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) attacker = shooter;
        if (activeMatch == null) {
            if (isParticipant(victim.getUniqueId())) event.setCancelled(true);
            return;
        }
        boolean victimIn = isParticipant(victim.getUniqueId());
        boolean attackerIn = attacker != null && isParticipant(attacker.getUniqueId());
        if (victimIn || attackerIn) {
            if (!victimIn || !attackerIn || sameTeam(victim.getUniqueId(), attacker.getUniqueId())) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (activeMatch == null || !isParticipant(event.getEntity().getUniqueId())) return;
        activeMatch.eliminated.add(event.getEntity().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> checkWinner());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (activeMatch == null || !isParticipant(event.getPlayer().getUniqueId())) return;
        Location back = activeMatch.returnLocations.get(event.getPlayer().getUniqueId());
        if (back != null) event.setRespawnLocation(back);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (activeMatch == null || !isParticipant(event.getPlayer().getUniqueId())) return;
        activeMatch.eliminated.add(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> checkWinner());
    }

    private void checkWinner() {
        if (activeMatch == null) return;
        boolean firstAlive = activeMatch.first.stream().anyMatch(id -> !activeMatch.eliminated.contains(id) && Bukkit.getPlayer(id) != null);
        boolean secondAlive = activeMatch.second.stream().anyMatch(id -> !activeMatch.eliminated.contains(id) && Bukkit.getPlayer(id) != null);
        if (!firstAlive) finish(activeMatch.secondName, null);
        else if (!secondAlive) finish(activeMatch.firstName, null);
    }

    private void finish(String winner, String message) {
        Match match = activeMatch;
        if (match == null) return;
        activeMatch = null;
        for (UUID id : match.allPlayers()) {
            Player player = Bukkit.getPlayer(id);
            Location back = match.returnLocations.get(id);
            if (player != null && back != null) player.teleport(back);
        }
        if (message != null) Bukkit.broadcastMessage(color("&e" + message));
        else Bukkit.broadcastMessage(color("&6&lCLAN-DUEL &a" + clans.displayName(winner) + " &7hat gewonnen!"));
    }

    private boolean isParticipant(UUID id) {
        return activeMatch != null && (activeMatch.first.contains(id) || activeMatch.second.contains(id));
    }

    private boolean sameTeam(UUID a, UUID b) {
        return activeMatch.first.contains(a) && activeMatch.first.contains(b)
                || activeMatch.second.contains(a) && activeMatch.second.contains(b);
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private static final class Match {
        private final String firstName;
        private final String secondName;
        private final Set<UUID> first;
        private final Set<UUID> second;
        private final Set<UUID> eliminated = new HashSet<>();
        private final Map<UUID, Location> returnLocations;
        private final Location firstSpawn;
        private final Location secondSpawn;

        private Match(String firstName, String secondName, Set<UUID> first, Set<UUID> second,
                      Map<UUID, Location> returnLocations, Location firstSpawn, Location secondSpawn) {
            this.firstName = firstName;
            this.secondName = secondName;
            this.first = first;
            this.second = second;
            this.returnLocations = returnLocations;
            this.firstSpawn = firstSpawn;
            this.secondSpawn = secondSpawn;
        }

        private Set<UUID> allPlayers() {
            Set<UUID> all = new HashSet<>(first);
            all.addAll(second);
            return all;
        }
    }
}
