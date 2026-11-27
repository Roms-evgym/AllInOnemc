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
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class ServerToolkit extends JavaPlugin implements Listener, CommandExecutor {
    private boolean lobbyMode;
    private ClanManager clanManager;
    private ClanDuelManager clanDuelManager;
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
    private RedeemCodeManager redeemCodeManager;
    private PopupMenuManager popupMenuManager;
    private PlaytimeManager playtimeManager;
    private BountyManager bountyManager;
    private ContentToolsManager contentToolsManager;
    private ContentToolsGuiManager contentToolsGuiManager;
    private MissingGuiManager missingGuiManager;
    private SubcommandDialogManager subcommandDialogManager;
    private AuthManager authManager;
    private ReplayManager replayManager;
    private final Map<java.util.UUID, Long> rtpCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().addDefault("proxy.lobby-server", "Lobby");
        getConfig().options().copyDefaults(true);
        saveConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        lobbyMode = getConfig().getString("mode", "LOBBY").equalsIgnoreCase("LOBBY");
        authManager = new AuthManager(this);
        getServer().getPluginManager().registerEvents(this, this);
        clanManager = new ClanManager(this);
        clanDuelManager = new ClanDuelManager(this, clanManager);
        maceManager = new MaceManager(this, clanManager);
        economyManager = new EconomyManager(this);
        playerFeaturesManager = new PlayerFeaturesManager(this);
        utilityManager = new UtilityManager(this);
        contentToolsManager = new ContentToolsManager(this);
        contentToolsGuiManager = new ContentToolsGuiManager(this);
        missingGuiManager = new MissingGuiManager(this);
        networkSyncManager = new NetworkSyncManager(this);
        playtimeManager = new PlaytimeManager(this);
        bountyManager = new BountyManager(this);
        redeemCodeManager = new RedeemCodeManager(this);
        popupMenuManager = new PopupMenuManager(this);
        subcommandDialogManager = new SubcommandDialogManager(this);
        marketManager = new MarketManager(this);
        kitManager = new KitManager(this);
        moderationManager = new ModerationManager(this);
        replayManager = new ReplayManager(this);
        sitManager = new SitManager(this);
        lobbyManager = new LobbyManager(this);
        WorldSpawnManager worldSpawnManager = new WorldSpawnManager();
        for (String name : new String[]{"spawn", "setspawn", "sethome", "home", "homes", "delhome", "rtp", "clan", "clanduel", "mace",
                "fly", "god", "invsee", "echest", "ecsee", "trash", "ranks", "rank", "voice", "ah", "order", "orders", "kit",
                "ban", "punish", "banpreset", "banrank", "banhammer", "vanish", "hub", "lobby", "fakebase", "fakore", "money", "eco", "pay", "sell", "shop", "tpa", "tpaccept", "tpdeny", "code", "setcode", "menu", "bounty", "playtime", "sit", "lag", "crash", "nick", "unnick", "prank", "register", "login", "logout", "report", "atlas"}) {
            registerCommandBridge(name, List.of(), (sender, args) -> {
                if (name.equals("fakebase") || name.equals("fakore")) worldSpawnManager.onCommand(sender, null, name, args);
                else onCommand(sender, null, name, args);
            });
        }
        registerCommandBridge("servers", List.of("servermenu"), (sender, args) -> onCommand(sender, null, "servers", args));
        getLogger().info("ServerToolkit gestartet im " + (lobbyMode ? "LOBBY" : "SURVIVAL") + "-Modus.");
    }

    @EventHandler
    public void onQuickMenuAction(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        if (!event.getIdentifier().namespace().equals("allinonemc")) return;
        Player player = connection.getPlayer();
        if (!player.hasPermission("servertoolkit.menu.open")) {
            player.sendMessage(color("&cDu darfst das Hauptmenü nicht öffnen."));
            return;
        }
        String action = event.getIdentifier().value();
        String command;
        String permission;
        switch (action) {
            case "menu_spawn" -> { command = "spawn"; permission = "servertoolkit.menu.spawn"; }
            case "menu_rtp" -> { command = "rtp"; permission = "servertoolkit.menu.rtp"; }
            case "menu_shop" -> { command = "shop"; permission = "servertoolkit.menu.shop"; }
            case "menu_sell" -> { command = "sell hand"; permission = "servertoolkit.menu.sell"; }
            case "menu_clan" -> { command = "clan"; permission = "servertoolkit.menu.clan"; }
            case "menu_kit" -> { command = "kit"; permission = "servertoolkit.menu.kit"; }
            case "menu_ranks" -> { command = "ranks"; permission = "servertoolkit.menu.ranks"; }
            case "menu_ah" -> { command = "ah"; permission = "servertoolkit.menu.ah"; }
            case "menu_orders" -> { command = "orders"; permission = "servertoolkit.menu.orders"; }
            case "menu_mace" -> { command = "mace"; permission = "servertoolkit.menu.mace"; }
            case "menu_servers" -> { command = "servers"; permission = "servertoolkit.menu.servers"; }
            case "menu_homes" -> { command = "homes"; permission = "servertoolkit.menu.homes"; }
            case "menu_voice" -> { command = "voice"; permission = "servertoolkit.menu.voice"; }
            case "menu_bounty" -> { command = "bounty"; permission = "servertoolkit.menu.bounty"; }
            case "menu_playtime" -> { command = "playtime"; permission = "servertoolkit.menu.playtime"; }
            case "menu_all" -> { command = "menu"; permission = "servertoolkit.menu.open"; }
            default -> { return; }
        }
        if (!player.hasPermission(permission)) {
            player.sendMessage(color("&cDafür hast du keine Berechtigung."));
            return;
        }
        player.performCommand(command);
    }

    @Override
    public void onDisable() {
        if (authManager != null) authManager.shutdown();
        if (maceManager != null) maceManager.shutdown();
        if (replayManager != null) replayManager.shutdown();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, NetworkSyncManager.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, NetworkSyncManager.CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p && args.length == 0 && subcommandDialogManager.openIfRoot(p, label)) return true;
        switch (label.toLowerCase(java.util.Locale.ROOT)) {
            case "menu" -> {
                if (sender instanceof Player p) missingGuiManager.openMain(p);
                else sender.sendMessage("Nur im Spiel verfügbar.");
                return true;
            }
            case "code" -> {
                if (sender instanceof Player p && args.length == 0) missingGuiManager.openCode(p);
                else redeemCodeManager.handleRedeem(sender, args);
                return true;
            }
            case "setcode" -> {
                if (sender instanceof Player p && args.length == 0) missingGuiManager.openSetCode(p);
                else redeemCodeManager.handleCreate(sender, args);
                return true;
            }
            case "report" -> { replayManager.handleReport(sender, args); return true; }
            case "atlas" -> {
                if (sender instanceof Player p) replayManager.handleAtlas(p, args);
                else sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
                return true;
            }
            case "playtime" -> {
                if (sender instanceof Player p) {
                    if (args.length == 0) missingGuiManager.openTargets(p, "playtime");
                    else playtimeManager.handle(p, args);
                } else sender.sendMessage("Nur im Spiel verfügbar.");
                return true;
            }
            case "lag" -> { contentToolsManager.handleLag(sender, args); return true; }
            case "crash" -> { contentToolsManager.handleCrash(sender, args); return true; }
            case "nick" -> { contentToolsManager.handleNick(sender, args); return true; }
            case "unnick" -> { contentToolsManager.handleUnnick(sender, args); return true; }
            case "prank" -> { contentToolsManager.handlePrank(sender, args); return true; }
            case "register", "login", "logout" -> { if (sender instanceof Player p) authManager.handle(p, label, args); else sender.sendMessage("Nur im Spiel verfügbar."); return true; }
            case "bounty" -> {
                if (sender instanceof Player p) {
                    if (args.length == 0) missingGuiManager.openTargets(p, "bounty");
                    else bountyManager.handle(p, args);
                } else sender.sendMessage("Nur im Spiel verfügbar.");
                return true;
            }
            case "ban" -> { moderationManager.handleBan(sender, args); return true; }
            case "punish" -> { moderationManager.handlePunish(sender, args); return true; }
            case "banpreset" -> { moderationManager.handlePreset(sender, args); return true; }
            case "banrank" -> { moderationManager.handleBanRanks(sender); return true; }
            case "money" -> {
                if (sender instanceof Player p && args.length == 0) missingGuiManager.openMoney(p);
                else economyManager.handle(sender, args);
                return true;
            }
            case "pay" -> {
                if (sender instanceof Player p && args.length == 0) missingGuiManager.openTargets(p, "pay");
                else economyManager.handle(sender, prepend("pay", args));
                return true;
            }
            case "eco" -> {
                if (sender instanceof Player p && args.length == 0) missingGuiManager.openEco(p);
                else economyManager.handle(sender, args);
                return true;
            }
            case "hub", "lobby" -> { connectToLobby(sender); return true; }
            case "sell", "shop", "rtp", "tpa", "tpaccept", "tpdeny" -> {
                if (sender instanceof Player p && label.equalsIgnoreCase("tpa") && args.length == 0) missingGuiManager.openTargets(p, "tpa");
                else playerFeaturesManager.onCommand(sender, command, label, args);
                return true;
            }
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        switch (label.toLowerCase(java.util.Locale.ROOT)) {
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
                if (args.length == 0) { missingGuiManager.openHomes(player, false); return true; }
                String homeName = normalizeHomeName(args[0]);
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
                if (args.length == 0) { missingGuiManager.openHomes(player, false); return true; }
                String homeName = normalizeHomeName(args[0]);
                if (homeName == null) { player.sendMessage(color("&cUngültiger Home-Name.")); return true; }
                Location home = loadLocation("named-homes." + player.getUniqueId() + "." + homeName);
                if (home == null && homeName.equals("home")) home = loadLocation("homes." + player.getUniqueId());
                if (home == null) player.sendMessage(color("&cHome nicht gefunden. Nutze /homes oder /sethome <Name>."));
                else { player.teleport(home); player.sendMessage(color("&aTeleportiert zu Home &f" + homeName + "&a.")); }
            }
            case "homes" -> missingGuiManager.openHomes(player, false);
            case "delhome" -> {
                if (args.length == 0) missingGuiManager.openHomes(player, true);
                else deleteHome(player, args);
            }
            case "rtp" -> playerFeaturesManager.onCommand(sender, command, label, args);
            case "clan" -> clanManager.handle(player, args);
            case "clanduel" -> clanDuelManager.handle(sender, args);
            case "mace" -> maceManager.onCommand(sender, command, label, args);
            case "fly", "god" -> {
                if (args.length == 0) missingGuiManager.openTargets(player, label);
                else utilityManager.onCommand(sender, command, label, args);
            }
            case "invsee", "ecsee" -> {
                if (args.length == 0) missingGuiManager.openTargets(player, label);
                else utilityManager.onCommand(sender, command, label, args);
            }
            case "echest", "trash", "ranks", "rank", "voice" ->
                    utilityManager.onCommand(sender, command, label, args);
            case "ah", "order", "orders" -> marketManager.onCommand(sender, command, label, args);
            case "kit" -> kitManager.handle(player, args);
            case "sit" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("gui")) missingGuiManager.openMain(player);
                else sitManager.onCommand(sender, null, label, args);
            }
            case "banhammer" -> moderationManager.handleHammer(player, args);
            case "vanish" -> moderationManager.handleVanish(player, args);
            case "servers" -> lobbyManager.openMenu(player);
            default -> { return false; }
        }
        return true;
    }

    private void registerCommandBridge(String name, java.util.Collection<String> aliases,
                                       java.util.function.BiConsumer<CommandSender, String[]> executor) {
        BasicCommand basic = new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                executor.accept(source.getSender(), args);
            }

            @Override
            public String permission() {
                return commandPermission(name);
            }
        };
        registerCommand(name, "AllInOneMC command", aliases, basic);
    }

    private String commandPermission(String name) {
        return switch (name) {
            case "menu" -> "servertoolkit.menu.open";
            case "atlas" -> "atlas.view";
            case "report" -> "atlas.report";
            case "register", "login", "logout" -> "servertoolkit.auth.use";
            case "setspawn", "fakebase", "fakore" -> "servertoolkit.admin";
            case "clanduel" -> "servertoolkit.clanduel";
            case "fly" -> "servertoolkit.fly";
            case "god" -> "servertoolkit.god";
            case "invsee", "ecsee" -> "servertoolkit.staff";
            case "rank", "ranks" -> "servertoolkit.rank.manage";
            case "setcode" -> "servertoolkit.code.create";
            case "lag" -> "servertoolkit.command.lag";
            case "crash" -> "servertoolkit.command.crash";
            case "nick", "unnick" -> "servertoolkit.nick";
            case "prank" -> "servertoolkit.prank.toggle";
            case "ban", "punish" -> "servertoolkit.ban";
            case "banpreset", "banrank", "banhammer" -> "servertoolkit.ban.manage";
            case "vanish" -> "servertoolkit.vanish";
            case "eco" -> "servertoolkit.eco.admin";
            default -> "servertoolkit.command." + name;
        };
    }

    UtilityManager utilityManager() { return utilityManager; }
    ClanManager clanManager() { return clanManager; }
    EconomyManager economyManager() { return economyManager; }
    NetworkSyncManager networkSyncManager() { return networkSyncManager; }
    PopupMenuManager popupMenuManager() { return popupMenuManager; }
    PlaytimeManager playtimeManager() { return playtimeManager; }
    BountyManager bountyManager() { return bountyManager; }
    ContentToolsManager contentToolsManager() { return contentToolsManager; }
    ContentToolsGuiManager contentToolsGuiManager() { return contentToolsGuiManager; }
    MissingGuiManager missingGuiManager() { return missingGuiManager; }

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
