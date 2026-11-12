package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MaceManager implements CommandExecutor, Listener {
    private final ServerToolkit plugin;
    private final ClanManager clans;
    private final File statsFile;
    private final YamlConfiguration stats;
    private final Map<UUID, UUID> challenges = new HashMap<>();
    private final Map<UUID, Match> activeMatches = new HashMap<>();
    private final Set<UUID> eventEntrants = new HashSet<>();
    private final ArrayDeque<UUID> tournamentQueue = new ArrayDeque<>();
    private boolean eventOpen;
    private boolean eventRunning;

    public MaceManager(ServerToolkit plugin, ClanManager clans) {
        this.plugin = plugin;
        this.clans = clans;
        this.statsFile = new File(plugin.getDataFolder(), "mace-stats.yml");
        if (!statsFile.exists()) {
            statsFile.getParentFile().mkdirs();
            try { statsFile.createNewFile(); } catch (IOException exception) {
                plugin.getLogger().severe("Konnte mace-stats.yml nicht erstellen: " + exception.getMessage());
            }
        }
        this.stats = YamlConfiguration.loadConfiguration(statsFile);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "duel" -> challenge(player, args);
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "setarena" -> setArena(player, args);
            case "stats" -> showStats(player, args);
            case "top" -> showTop(player);
            case "event" -> eventCommand(player, args);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void challenge(Player challenger, String[] args) {
        if (args.length < 2) {
            challenger.sendMessage(color("&cNutzung: /mace duel <Spieler>"));
            return;
        }
        if (!arenasReady()) {
            challenger.sendMessage(color("&cDie Mace-Arena ist noch nicht eingerichtet."));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(challenger)) {
            challenger.sendMessage(color("&cSpieler nicht gefunden."));
            return;
        }
        if (activeMatches.containsKey(challenger.getUniqueId()) || activeMatches.containsKey(target.getUniqueId())) {
            challenger.sendMessage(color("&cDu oder dein Gegner seid bereits in einem Duell."));
            return;
        }
        String clanA = clans.clanKey(challenger.getUniqueId());
        String clanB = clans.clanKey(target.getUniqueId());
        if (clanA != null && clanA.equals(clanB)) {
            challenger.sendMessage(color("&cDu kannst kein Clanmitglied herausfordern."));
            return;
        }
        challenges.put(target.getUniqueId(), challenger.getUniqueId());
        challenger.sendMessage(color("&aMace-Duell an &f" + target.getName() + " &agesendet."));
        target.sendMessage(color("&e" + challenger.getName() + " &7fordert dich zum Mace-Duell heraus."));
        target.sendMessage(color("&7Nimm mit &f/mace accept &7an oder lehne mit &f/mace deny &7ab."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> challenges.remove(target.getUniqueId(), challenger.getUniqueId()), 1200L);
    }

    private void accept(Player target) {
        UUID challengerId = challenges.remove(target.getUniqueId());
        Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
        if (challenger == null) {
            target.sendMessage(color("&cDu hast keine offene Mace-Herausforderung."));
            return;
        }
        startMatch(challenger, target, false);
    }

    private void deny(Player target) {
        UUID challengerId = challenges.remove(target.getUniqueId());
        Player challenger = challengerId == null ? null : Bukkit.getPlayer(challengerId);
        target.sendMessage(color("&7Du hast die Herausforderung abgelehnt."));
        if (challenger != null) challenger.sendMessage(color("&c" + target.getName() + " hat die Herausforderung abgelehnt."));
    }

    private void setArena(Player player, String[] args) {
        if (!player.hasPermission("servertoolkit.admin")) {
            player.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("a") || args[1].equalsIgnoreCase("b"))) {
            player.sendMessage(color("&cNutzung: /mace setarena <a|b>"));
            return;
        }
        saveLocation("mace.arena." + args[1].toLowerCase(Locale.ROOT), player.getLocation());
        player.sendMessage(color("&aArena-Punkt " + args[1].toUpperCase(Locale.ROOT) + " wurde gesetzt."));
    }

    private void eventCommand(Player player, String[] args) {
        if (args.length < 2) {
            eventStatus(player);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (!eventOpen || eventRunning) player.sendMessage(color("&cGerade ist keine Mace-Veranstaltung offen."));
                else if (activeMatches.containsKey(player.getUniqueId())) player.sendMessage(color("&cDu bist gerade in einem Duell."));
                else if (!eventEntrants.add(player.getUniqueId())) player.sendMessage(color("&eDu stehst bereits auf der Teilnehmerliste."));
                else announce("&e" + player.getName() + " &7nimmt am Mace-Event teil. (&f" + eventEntrants.size() + "&7)");
            }
            case "open" -> {
                if (!isAdmin(player)) return;
                if (eventRunning) player.sendMessage(color("&cEin Turnier läuft bereits."));
                else {
                    eventEntrants.clear();
                    eventOpen = true;
                    announce("&6Ein Mace-Event ist offen! &e/mace event join &6zum Anmelden.");
                }
            }
            case "close" -> {
                if (!isAdmin(player)) return;
                eventOpen = false;
                announce("&7Die Anmeldung zum Mace-Event ist geschlossen.");
            }
            case "start" -> {
                if (!isAdmin(player)) return;
                if (!arenasReady()) player.sendMessage(color("&cSetze zuerst beide Arena-Punkte mit /mace setarena a/b."));
                else if (eventEntrants.size() < 2) player.sendMessage(color("&cMindestens zwei Spieler müssen teilnehmen."));
                else {
                    eventOpen = false;
                    eventRunning = true;
                    tournamentQueue.clear();
                    tournamentQueue.addAll(eventEntrants);
                    announce("&6Das Mace-Event startet mit &e" + eventEntrants.size() + " &6Spielern!");
                    launchNextRound();
                }
            }
            case "cancel" -> {
                if (!isAdmin(player)) return;
                eventOpen = false;
                eventRunning = false;
                tournamentQueue.clear();
                eventEntrants.clear();
                announce("&cDas Mace-Event wurde abgebrochen.");
            }
            case "status" -> eventStatus(player);
            default -> eventStatus(player);
        }
    }

    private void eventStatus(Player player) {
        String status = eventRunning ? "Turnier läuft" : eventOpen ? "Anmeldung offen" : "Kein Event aktiv";
        player.sendMessage(color("&6Mace-Event: &e" + status + " &7| Teilnehmer: &f" + eventEntrants.size()));
        if (eventOpen && !eventRunning) player.sendMessage(color("&7Anmeldung: &f/mace event join"));
    }

    private void launchNextRound() {
        if (!eventRunning) return;
        if (tournamentQueue.size() < 2) {
            UUID champion = tournamentQueue.pollFirst();
            eventRunning = false;
            eventEntrants.clear();
            if (champion == null) announce("&7Das Mace-Event wurde beendet.");
            else {
                String name = Bukkit.getOfflinePlayer(champion).getName();
                announce("&6Mace-Champion: &e" + (name == null ? "Unbekannt" : name) + "&6!");
            }
            return;
        }
        UUID aId = tournamentQueue.pollFirst();
        UUID bId = tournamentQueue.pollFirst();
        Player a = Bukkit.getPlayer(aId);
        Player b = Bukkit.getPlayer(bId);
        if (a == null || b == null) {
            if (a != null) tournamentQueue.addLast(aId);
            if (b != null) tournamentQueue.addLast(bId);
            Bukkit.getScheduler().runTaskLater(plugin, this::launchNextRound, 20L);
            return;
        }
        if (!startMatch(a, b, true)) {
            if (a.isOnline()) tournamentQueue.addLast(aId);
            if (b.isOnline()) tournamentQueue.addLast(bId);
            Bukkit.getScheduler().runTaskLater(plugin, this::launchNextRound, 20L);
        }
    }

    private boolean startMatch(Player a, Player b, boolean tournament) {
        Location pointA = loadLocation("mace.arena.a");
        Location pointB = loadLocation("mace.arena.b");
        if (pointA == null || pointB == null || activeMatches.containsKey(a.getUniqueId()) || activeMatches.containsKey(b.getUniqueId())) {
            a.sendMessage(color("&cDas Duell konnte nicht gestartet werden."));
            return false;
        }
        Match match = new Match(a, b, tournament);
        activeMatches.put(a.getUniqueId(), match);
        activeMatches.put(b.getUniqueId(), match);
        prepare(a, pointA);
        prepare(b, pointB);
        announce("&6Mace-Duell: &e" + a.getName() + " &7gegen &e" + b.getName() + "&6!");
        a.sendMessage(color("&7Nutze Windladungen und die Höhe: Fallangriffe verstärken den Mace."));
        b.sendMessage(color("&7Nutze Windladungen und die Höhe: Fallangriffe verstärken den Mace."));
        return true;
    }

    private void prepare(Player player, Location location) {
        Snapshot snapshot = new Snapshot(player);
        Match match = activeMatches.get(player.getUniqueId());
        match.snapshots.put(player.getUniqueId(), snapshot);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[]{
                new ItemStack(Material.DIAMOND_BOOTS), new ItemStack(Material.DIAMOND_LEGGINGS),
                new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET)
        });
        inventory.addItem(named(new ItemStack(Material.MACE), "&6Mace"));
        inventory.addItem(named(new ItemStack(Material.WIND_CHARGE, 16), "&bWindladungen"));
        inventory.addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.teleport(location);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDuelDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = event.getDamager() instanceof Player p ? p : null;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) attacker = p;
        Match victimMatch = victim == null ? null : activeMatches.get(victim.getUniqueId());
        Match attackerMatch = attacker == null ? null : activeMatches.get(attacker.getUniqueId());
        if (victimMatch != null || attackerMatch != null) {
            if (victimMatch == null || victimMatch != attackerMatch) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDuelDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Match match = activeMatches.get(dead.getUniqueId());
        if (match == null || match.finishing) return;
        match.finishing = true;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        UUID winnerId = match.other(dead.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishMatch(match, winnerId, dead.getName()), 10L);
    }

    @EventHandler
    public void onDuelRespawn(PlayerRespawnEvent event) {
        Match match = activeMatches.get(event.getPlayer().getUniqueId());
        if (match == null) return;
        Snapshot snapshot = match.snapshots.get(event.getPlayer().getUniqueId());
        if (snapshot != null) event.setRespawnLocation(snapshot.location);
    }

    @EventHandler
    public void onDuelQuit(PlayerQuitEvent event) {
        Match match = activeMatches.get(event.getPlayer().getUniqueId());
        if (match == null || match.finishing) return;
        match.finishing = true;
        finishMatch(match, match.other(event.getPlayer().getUniqueId()), event.getPlayer().getName());
    }

    private void finishMatch(Match match, UUID winnerId, String loserName) {
        if (!activeMatches.containsValue(match)) return;
        activeMatches.remove(match.a);
        activeMatches.remove(match.b);
        Player a = Bukkit.getPlayer(match.a);
        Player b = Bukkit.getPlayer(match.b);
        restore(a, match.snapshots.get(match.a));
        restore(b, match.snapshots.get(match.b));
        if (winnerId != null) {
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                addStat(winner, "wins");
                clans.recordMaceWin(winnerId);
                winner.sendMessage(color("&aDu hast das Mace-Duell gewonnen!"));
            }
            Player loser = Bukkit.getPlayer(match.other(winnerId));
            if (loser != null) {
                addStat(loser, "losses");
                loser.sendMessage(color("&cDu hast das Mace-Duell verloren."));
            }
        }
        if (match.tournament && eventRunning) {
            if (winnerId != null && eventEntrants.contains(winnerId)) tournamentQueue.addLast(winnerId);
            Bukkit.getScheduler().runTaskLater(plugin, this::launchNextRound, 40L);
        }
        if (loserName != null && winnerId != null) {
            String winnerName = Bukkit.getOfflinePlayer(winnerId).getName();
            announce("&e" + (winnerName == null ? "Spieler" : winnerName) + " &7besiegt &e" + loserName + " &7im Mace-Duell.");
        }
    }

    private void restore(Player player, Snapshot snapshot) {
        if (player == null || snapshot == null || !player.isOnline()) return;
        player.setGameMode(snapshot.gameMode);
        player.getInventory().setContents(snapshot.contents);
        player.getInventory().setArmorContents(snapshot.armor);
        player.getInventory().setItemInOffHand(snapshot.offhand);
        player.setLevel(snapshot.level);
        player.setExp(snapshot.exp);
        player.setFoodLevel(snapshot.food);
        player.setSaturation(snapshot.saturation);
        player.setFireTicks(snapshot.fireTicks);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        for (PotionEffect effect : snapshot.effects) player.addPotionEffect(effect);
        player.teleport(snapshot.location);
        double health = Math.max(1.0, Math.min(snapshot.health, player.getMaxHealth()));
        player.setHealth(health);
    }

    private void addStat(Player player, String key) {
        String path = "players." + player.getUniqueId();
        stats.set(path + ".name", player.getName());
        stats.set(path + "." + key, stats.getInt(path + "." + key) + 1);
        if (key.equals("wins")) stats.set(path + ".streak", stats.getInt(path + ".streak") + 1);
        else stats.set(path + ".streak", 0);
        saveStats();
    }

    private void showStats(Player player, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : player;
        if (target == null) {
            player.sendMessage(color("&cDieser Spieler ist nicht online."));
            return;
        }
        String path = "players." + target.getUniqueId();
        player.sendMessage(color("&6Mace-Statistik von &e" + target.getName()));
        player.sendMessage(color("&7Siege: &a" + stats.getInt(path + ".wins") + " &7| Niederlagen: &c" + stats.getInt(path + ".losses")
                + " &7| Serie: &b" + stats.getInt(path + ".streak")));
    }

    private void showTop(Player player) {
        ConfigurationSection section = stats.getConfigurationSection("players");
        if (section == null || section.getKeys(false).isEmpty()) {
            player.sendMessage(color("&7Noch gibt es keine Mace-Duelle."));
            return;
        }
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> stats.getInt("players." + id + ".wins")).reversed());
        player.sendMessage(color("&6&m-----&r &eMace-Rangliste &6&m-----"));
        for (int i = 0; i < Math.min(5, ids.size()); i++) {
            String id = ids.get(i);
            player.sendMessage(color("&e" + (i + 1) + ". &f" + stats.getString("players." + id + ".name", "Unbekannt")
                    + " &7- &a" + stats.getInt("players." + id + ".wins") + " Siege"));
        }
    }

    private boolean arenasReady() { return loadLocation("mace.arena.a") != null && loadLocation("mace.arena.b") != null; }

    private void saveLocation(String path, Location location) {
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    private Location loadLocation(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private void help(Player player) {
        player.sendMessage(color("&6Mace: &f/mace duel <Spieler>, /mace accept, /mace deny, /mace stats [Spieler], /mace top"));
        player.sendMessage(color("&6Event: &f/mace event join|status &7(Leitung: open|close|start|cancel)"));
        if (player.hasPermission("servertoolkit.admin")) player.sendMessage(color("&6Setup: &f/mace setarena <a|b>"));
    }

    private boolean isAdmin(Player player) {
        if (player.hasPermission("servertoolkit.admin")) return true;
        player.sendMessage(color("&cDafür hast du keine Rechte."));
        return false;
    }

    private void announce(String message) {
        Bukkit.broadcastMessage(color("&8[&6Mace&8] " + message));
    }

    private ItemStack named(ItemStack item, String displayName) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void saveStats() {
        try { stats.save(statsFile); }
        catch (IOException exception) { plugin.getLogger().severe("Konnte mace-stats.yml nicht speichern: " + exception.getMessage()); }
    }

    private String color(String message) { return ChatColor.translateAlternateColorCodes('&', message); }

    public void shutdown() {
        for (Match match : new HashSet<>(activeMatches.values())) {
            restore(Bukkit.getPlayer(match.a), match.snapshots.get(match.a));
            restore(Bukkit.getPlayer(match.b), match.snapshots.get(match.b));
        }
        activeMatches.clear();
    }

    private static final class Match {
        private final UUID a;
        private final UUID b;
        private final boolean tournament;
        private final Map<UUID, Snapshot> snapshots = new HashMap<>();
        private boolean finishing;

        private Match(Player a, Player b, boolean tournament) {
            this.a = a.getUniqueId();
            this.b = b.getUniqueId();
            this.tournament = tournament;
        }

        private UUID other(UUID id) { return id.equals(a) ? b : a; }
    }

    private static final class Snapshot {
        private final Location location;
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final GameMode gameMode;
        private final double health;
        private final int food;
        private final float saturation;
        private final int level;
        private final float exp;
        private final int fireTicks;
        private final List<PotionEffect> effects;

        private Snapshot(Player player) {
            location = player.getLocation().clone();
            contents = cloneItems(player.getInventory().getContents());
            armor = cloneItems(player.getInventory().getArmorContents());
            offhand = player.getInventory().getItemInOffHand().clone();
            gameMode = player.getGameMode();
            health = player.getHealth();
            food = player.getFoodLevel();
            saturation = player.getSaturation();
            level = player.getLevel();
            exp = player.getExp();
            fireTicks = player.getFireTicks();
            effects = new ArrayList<>(player.getActivePotionEffects());
        }

        private static ItemStack[] cloneItems(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
            return copy;
        }
    }
}
