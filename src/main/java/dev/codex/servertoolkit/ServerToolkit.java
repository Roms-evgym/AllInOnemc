package dev.codex.servertoolkit;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class ServerToolkit extends JavaPlugin implements Listener, CommandExecutor {
    private boolean lobbyMode;
    private ClanManager clanManager;
    private MaceManager maceManager;
    private UtilityManager utilityManager;
    private MarketManager marketManager;
    private KitManager kitManager;
    private ModerationManager moderationManager;
    private SitManager sitManager;
    private LobbyManager lobbyManager;
    private EconomyManager economyManager;
    private PlayerFeaturesManager playerFeaturesManager;
    private NetworkSyncManager networkSyncManager;
    private final Map<java.util.UUID, Long> rtpCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().addDefault("proxy.lobby-server", "Lobby");
        getConfig().options().copyDefaults(true);
        saveConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        lobbyMode = getConfig().getString("mode", "LOBBY").equalsIgnoreCase("LOBBY");
        getServer().getPluginManager().registerEvents(this, this);
        clanManager = new ClanManager(this);
        maceManager = new MaceManager(this, clanManager);
        economyManager = new EconomyManager(this);
        playerFeaturesManager = new PlayerFeaturesManager(this);
        utilityManager = new UtilityManager(this);
        networkSyncManager = new NetworkSyncManager(this);
        marketManager = new MarketManager(this);
        kitManager = new KitManager(this);
        moderationManager = new ModerationManager(this);
        sitManager = new SitManager(this);
        lobbyManager = new LobbyManager(this);
        WorldSpawnManager worldSpawnManager = new WorldSpawnManager();
        for (String name : new String[]{"spawn", "setspawn", "sethome", "home", "homes", "delhome", "rtp", "clan", "mace",
                "fly", "god", "invsee", "echest", "ecsee", "trash", "ranks", "rank", "voice", "ah", "order", "orders", "kit",
                "ban", "banpreset", "banrank", "banhammer", "vanish", "hub", "lobby", "servers", "fakebase", "fakore", "money", "eco", "pay", "sell", "shop", "tpa", "tpaccept", "tpdeny"}) {
            getCommand(name).setExecutor(this);
        }
        getCommand("sit").setExecutor(sitManager);
        getCommand("fakebase").setExecutor(worldSpawnManager);
        getCommand("fakore").setExecutor(worldSpawnManager);
        getLogger().info("ServerToolkit gestartet im " + (lobbyMode ? "LOBBY" : "SURVIVAL") + "-Modus.");
    }

    @Override
    public void onDisable() {
        if (maceManager != null) maceManager.shutdown();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, NetworkSyncManager.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, NetworkSyncManager.CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "ban" -> { moderationManager.handleBan(sender, args); return true; }
            case "banpreset" -> { moderationManager.handlePreset(sender, args); return true; }
            case "banrank" -> { moderationManager.handleBanRanks(sender); return true; }
            case "money" -> { economyManager.handle(sender, args); return true; }
            case "pay" -> { economyManager.handle(sender, prepend("pay", args)); return true; }
            case "eco" -> { economyManager.handle(sender, args); return true; }
            case "hub", "lobby" -> { connectToLobby(sender); return true; }
            case "sell", "shop", "rtp", "tpa", "tpaccept", "tpdeny" -> { playerFeaturesManager.onCommand(sender, command, label, args); return true; }
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "spawn" -> teleportSpawn(player);
            case "setspawn" -> {
                if (!player.hasPermission("servertoolkit.admin")) {
                    player.sendMessage(color("&cDafür hast du keine Rechte."));
                    return true;
                }
                saveLocation("spawn", player.getLocation());
                player.sendMessage(color("&aSpawn wurde gesetzt."));
            }
            case "sethome" -> {
                if (lobbyMode) {
                    player.sendMessage(color("&cHomes sind auf der Lobby deaktiviert."));
                    return true;
                }
                String homeName = args.length == 0 ? "home" : normalizeHomeName(args[0]);
                if (homeName == null) { player.sendMessage(color("&cName: 1-16 Zeichen, Buchstaben, Zahlen, _ oder -")); return true; }
                String base = "named-homes." + player.getUniqueId();
                ConfigurationSection currentHomes = getConfig().getConfigurationSection(base);
                if (getConfig().getConfigurationSection(base + "." + homeName) == null && currentHomes != null
                        && currentHomes.getKeys(false).size() >= utilityManager.maxHomes(player)) {
                    player.sendMessage(color("&cDein Rang erlaubt maximal " + utilityManager.maxHomes(player) + " Homes.")); return true;
                }
                saveLocation(base + "." + homeName, player.getLocation());
                player.sendMessage(color("&aHome &f" + homeName + " &agesetzt."));
            }
            case "home" -> {
                if (lobbyMode) {
                    player.sendMessage(color("&cHomes sind auf der Lobby deaktiviert."));
                    return true;
                }
                String homeName = args.length > 0 ? normalizeHomeName(args[0]) : "home";
                if (homeName == null) { player.sendMessage(color("&cUngültiger Home-Name.")); return true; }
                Location home = loadLocation("named-homes." + player.getUniqueId() + "." + homeName);
                if (home == null && homeName.equals("home")) home = loadLocation("homes." + player.getUniqueId());
                if (home == null) player.sendMessage(color("&cHome nicht gefunden. Nutze /homes oder /sethome <Name>."));
                else { player.teleport(home); player.sendMessage(color("&aTeleportiert zu Home &f" + homeName + "&a.")); }
            }
            case "homes" -> listHomes(player);
            case "delhome" -> deleteHome(player, args);
            case "rtp" -> playerFeaturesManager.onCommand(sender, command, label, args);
            case "clan" -> clanManager.handle(player, args);
            case "mace" -> maceManager.onCommand(sender, command, label, args);
            case "fly", "god", "invsee", "echest", "ecsee", "trash", "ranks", "rank", "voice" ->
                    utilityManager.onCommand(sender, command, label, args);
            case "ah", "order", "orders" -> marketManager.onCommand(sender, command, label, args);
            case "kit" -> kitManager.handle(player, args);
            case "banhammer" -> moderationManager.handleHammer(player, args);
            case "vanish" -> moderationManager.handleVanish(player, args);
            case "servers" -> lobbyManager.openMenu(player);
            default -> { return false; }
        }
        return true;
    }

    UtilityManager utilityManager() { return utilityManager; }
    EconomyManager economyManager() { return economyManager; }
    NetworkSyncManager networkSyncManager() { return networkSyncManager; }

    private String[] prepend(String first, String[] rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private void connectToLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return;
        }
        lobbyManager.connectToServer(player, getConfig().getString("proxy.lobby-server", "Lobby"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        Player player = event.getPlayer();
        if (lobbyMode) {
            player.sendTitle(color(getConfig().getString("join-title", "")),
                    color(getConfig().getString("join-subtitle", "")), 10, 60, 20);
            lobbyManager.onJoin(player);
        }
        if (getConfig().getBoolean("teleport-to-spawn-on-join", true)) {
            Location spawn = loadLocation("spawn");
            if (spawn != null) player.teleport(spawn);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        event.setQuitMessage(null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = loadLocation("spawn");
        if (spawn != null) event.setRespawnLocation(spawn);
    }

    private void listHomes(Player player) {
        ConfigurationSection homes = getConfig().getConfigurationSection("named-homes." + player.getUniqueId());
        java.util.List<String> names = homes == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(homes.getKeys(false));
        if (loadLocation("homes." + player.getUniqueId()) != null && !names.contains("home")) names.add("home");
        if (names.isEmpty()) player.sendMessage(color("&7Du hast noch keine Homes. Setze eines mit &f/sethome <Name>"));
        else player.sendMessage(color("&6Deine Homes (&f" + names.size() + "/" + utilityManager.maxHomes(player) + "&6): &f" + String.join(", ", names)));
    }

    private void deleteHome(Player player, String[] args) {
        if (args.length < 1) { player.sendMessage(color("&cNutzung: /delhome <Name>")); return; }
        String name = normalizeHomeName(args[0]);
        if (name == null) { player.sendMessage(color("&cUngültiger Home-Name.")); return; }
        String path = "named-homes." + player.getUniqueId() + "." + name;
        if (getConfig().getConfigurationSection(path) == null) {
            if (name.equals("home") && loadLocation("homes." + player.getUniqueId()) != null) path = "homes." + player.getUniqueId();
            else { player.sendMessage(color("&cDieses Home gibt es nicht.")); return; }
        }
        getConfig().set(path, null); saveConfig(); player.sendMessage(color("&aHome &f" + name + " &agelöscht."));
    }

    private void randomTeleport(Player player) {
        if (lobbyMode) { player.sendMessage(color("&cRTP ist auf der Lobby deaktiviert.")); return; }
        long now = System.currentTimeMillis();
        long readyAt = rtpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) { player.sendMessage(color("&cWarte noch " + ((readyAt - now + 999) / 1000) + " Sekunden bis zum nächsten RTP.")); return; }
        String worldName = getConfig().getString("rtp.world", "world");
        World world = getServer().getWorld(worldName);
        if (world == null) { player.sendMessage(color("&cDie RTP-Welt &f" + worldName + " &cwurde nicht gefunden.")); return; }
        int min = Math.max(0, getConfig().getInt("rtp.min-radius", 500));
        int max = Math.max(min + 1, getConfig().getInt("rtp.max-radius", 5000));
        Location center = loadLocation("spawn");
        if (center == null || center.getWorld() != world) center = world.getSpawnLocation();
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble(0, Math.PI * 2);
            int radius = random.nextInt(min, max + 1);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location candidate = safeRtpLocation(world, x, z);
            if (candidate != null) {
                player.teleport(candidate);
                rtpCooldowns.put(player.getUniqueId(), now + getConfig().getLong("rtp.cooldown-seconds", 60) * 1000L);
                player.sendMessage(color("&aSicherer Zufallsort gefunden. Viel Glück!")); return;
            }
        }
        player.sendMessage(color("&cKein sicherer Ort gefunden. Versuch es gleich noch einmal."));
    }

    private Location safeRtpLocation(World world, int x, int z) {
        Location probe = new Location(world, x + 0.5, world.getMinHeight(), z + 0.5);
        if (!world.getWorldBorder().isInside(probe)) return null;
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) return null;
        Block floor = world.getBlockAt(x, y, z), feet = world.getBlockAt(x, y + 1, z), head = world.getBlockAt(x, y + 2, z);
        if (!floor.getType().isSolid() || feet.getType().isSolid() || head.getType().isSolid()
                || feet.isLiquid() || head.isLiquid() || floor.isLiquid()) return null;
        Material material = floor.getType();
        if (material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.FIRE
                || material == Material.SOUL_FIRE || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE) return null;
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private String normalizeHomeName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,16}") ? normalized : null;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (protectedLobby()) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (protectedLobby()) event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (protectedLobby() && event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (protectedLobby()) event.setCancelled(true);
    }

    private boolean protectedLobby() {
        return lobbyMode && getConfig().getBoolean("lobby-protection", true);
    }

    private void teleportSpawn(Player player) {
        Location spawn = loadLocation("spawn");
        if (spawn == null) player.sendMessage(color("&cDer Spawn wurde noch nicht gesetzt."));
        else {
            player.teleport(spawn);
            player.sendMessage(color("&aDu bist zum Spawn teleportiert worden."));
        }
    }

    private void saveLocation(String path, Location location) {
        getConfig().set(path + ".world", location.getWorld().getName());
        getConfig().set(path + ".x", location.getX());
        getConfig().set(path + ".y", location.getY());
        getConfig().set(path + ".z", location.getZ());
        getConfig().set(path + ".yaw", location.getYaw());
        getConfig().set(path + ".pitch", location.getPitch());
        saveConfig();
    }

    private Location loadLocation(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null) return null;
        World world = getServer().getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    boolean isLobbyMode() { return lobbyMode; }
    Location loadSpawn() { return loadLocation("spawn"); }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
