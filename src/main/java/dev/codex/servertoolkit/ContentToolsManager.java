package dev.codex.servertoolkit;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class ContentToolsManager implements Listener {
    private static final List<String> NICKS = List.of(
            "ShadowWolf", "PixelNinja", "CloudRider", "FrostByte", "LunarFox",
            "EnderKnight", "NovaCraft", "SilentRaven", "BlazeRunner", "AquaViper",
            "StormPixel", "MysticOwl", "EchoMiner", "VoidWalker", "CopperCat",
            "RedstoneAce", "NightBloom", "SkyVoyager", "QuartzFox", "IronPhantom"
    );
    private static final List<String> SKIN_PROFILES = List.of(
            "Notch", "jeb_", "Dream", "GeorgeNotFound", "Sapnap", "TommyInnit",
            "CaptainSparklez", "Grian", "MumboJumbo", "GoodTimesWithScar",
            "Iskall85", "Xisumavoid", "Etho", "Docm77", "PearlescentMoon"
    );

    private final ServerToolkit plugin;
    private final Map<UUID, LagSession> lagSessions = new HashMap<>();
    private final Map<UUID, Integer> crashTasks = new HashMap<>();
    private final Map<UUID, NickSession> nickSessions = new HashMap<>();

    ContentToolsManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void handleLag(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) plugin.contentToolsGuiManager().openTargets(player, "lag");
            else sender.sendMessage("§eNutzung: /lag <Spieler> [Sekunden] | /lag stop <Spieler>");
            return;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("servertoolkit.lag")) { sender.sendMessage("§cKeine Berechtigung."); return; }
            if (args.length < 2) { sender.sendMessage("§cNutzung: /lag stop <Spieler>"); return; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("§cSpieler nicht online."); return; }
            stopLag(target, "§aDie Lag-Simulation wurde beendet.");
            sender.sendMessage("§aLag-Simulation für " + target.getName() + " beendet.");
            return;
        }
        if (!sender.hasPermission("servertoolkit.lag")) { sender.sendMessage("§cKeine Berechtigung."); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cSpieler nicht online."); return; }
        if (target.equals(sender)) { sender.sendMessage("§cDu kannst dich nicht selbst pranken."); return; }
        if (!isPrankEnabled(target)) { sender.sendMessage("§c" + target.getName() + " hat Pranks nicht aktiviert. (Standard: off)"); return; }
        int seconds = 8;
        if (args.length > 1) {
            try { seconds = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) { sender.sendMessage("§cSekunden müssen eine Zahl sein."); return; }
        }
        seconds = Math.max(3, Math.min(30, seconds));
        startLag(target, seconds);
        sender.sendMessage("§aLag-Prank für " + target.getName() + " gestartet.");
    }

    void handleCrash(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) plugin.contentToolsGuiManager().openTargets(player, "crash");
            else sender.sendMessage("§eNutzung: /crash <Spieler> | /crash stop <Spieler>");
            return;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("servertoolkit.crash")) { sender.sendMessage("§cKeine Berechtigung."); return; }
            if (args.length < 2) { sender.sendMessage("§cNutzung: /crash stop <Spieler>"); return; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("§cSpieler nicht online."); return; }
            Integer task = crashTasks.remove(target.getUniqueId());
            if (task != null) Bukkit.getScheduler().cancelTask(task);
            target.sendMessage("§aDie Crash-Simulation wurde abgebrochen.");
            sender.sendMessage("§aCrash-Simulation für " + target.getName() + " abgebrochen.");
            return;
        }
        if (!sender.hasPermission("servertoolkit.crash")) { sender.sendMessage("§cKeine Berechtigung."); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cSpieler nicht online."); return; }
        if (target.equals(sender)) { sender.sendMessage("§cDu kannst dich nicht selbst pranken."); return; }
        if (!isPrankEnabled(target)) { sender.sendMessage("§c" + target.getName() + " hat Pranks nicht aktiviert. (Standard: off)"); return; }
        startCrash(target);
        sender.sendMessage("§aCrash-Simulation für " + target.getName() + " gestartet.");
    }

    private void startLag(Player player, int seconds) {
        stopLag(player, null);
        Map<PotionEffectType, PotionEffect> previous = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) previous.put(effect.getType(), effect);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 4, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, seconds * 20, 3, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, seconds * 20, 0, false, true, true));
        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> restoreLag(player, previous), seconds * 20L).getTaskId();
        lagSessions.put(player.getUniqueId(), new LagSession(task, previous));
        player.sendMessage("§eLag-Simulation aktiv für " + seconds + " Sekunden. §7/lag stop zum vorzeitigen Beenden.");
    }

    private void stopLag(Player player, String message) {
        LagSession session = lagSessions.remove(player.getUniqueId());
        if (session == null) return;
        Bukkit.getScheduler().cancelTask(session.task);
        restoreLag(player, session.previous);
        if (message != null) player.sendMessage(message);
    }

    private void restoreLag(Player player, Map<PotionEffectType, PotionEffect> previous) {
        lagSessions.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        for (PotionEffect effect : previous.values()) player.addPotionEffect(effect, true);
    }

    private void startCrash(Player player) {
        player.sendMessage("§c§lCRASH-SIMULATION §7– Disconnect in 5 Sekunden. §8/crash stop durch Admin zum Abbrechen.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, true));
        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            crashTasks.remove(player.getUniqueId());
            if (player.isOnline()) player.kickPlayer("§cMinecraft wurde getrennt\n§7Dies war eine vereinbarte Content-Crash-Simulation.");
        }, 100L).getTaskId();
        crashTasks.put(player.getUniqueId(), task);
    }

    void handlePrank(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können ihren Prank-Status ändern.");
            return;
        }
        if (args.length == 0) {
            plugin.contentToolsGuiManager().openPrank(player);
            return;
        }
        if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("status"))) {
            player.sendMessage("§eNutzung: /prank [on|off|status]");
            return;
        }
        boolean enabled = isPrankEnabled(player);
        if (args[0].equalsIgnoreCase("status")) {
            player.sendMessage("§ePranks: " + (enabled ? "§aON" : "§cOFF"));
            return;
        }
        enabled = args[0].equalsIgnoreCase("on");
        plugin.getConfig().set("prank-enabled." + player.getUniqueId(), enabled);
        plugin.saveConfig();
        if (plugin.networkSyncManager() != null) plugin.networkSyncManager().publishPrank(player, enabled);
        player.sendMessage(enabled ? "§aPranks aktiviert. Admins dürfen jetzt Lag- und Crash-Simulationen starten." : "§cPranks deaktiviert. Lag- und Crash-Simulationen sind für dich gesperrt.");
        if (!enabled) {
            stopLag(player, "§cDeine laufende Lag-Simulation wurde wegen /prank off beendet.");
            Integer task = crashTasks.remove(player.getUniqueId());
            if (task != null) {
                Bukkit.getScheduler().cancelTask(task);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.NAUSEA);
                player.sendMessage("§cDeine laufende Crash-Simulation wurde beendet.");
            }
        }
    }

    private boolean isPrankEnabled(Player player) {
        return plugin.getConfig().getBoolean("prank-enabled." + player.getUniqueId(), plugin.getConfig().getBoolean("prank.default-enabled", true));
    }

    void handleNick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cNur im Spiel verfügbar."); return; }
        if (args.length == 0) {
            if (!player.hasPermission("servertoolkit.nick")) { sender.sendMessage("§cKeine Berechtigung."); return; }
            plugin.contentToolsGuiManager().openNick(player);
            return;
        }
        if (args[0].equalsIgnoreCase("random")) {
            if (!player.hasPermission("servertoolkit.nick")) { sender.sendMessage("§cKeine Berechtigung."); return; }
            applyNick(player, player);
            return;
        }
        if (args[0].equalsIgnoreCase("off")) {
            if (!player.hasPermission("servertoolkit.nick")) { sender.sendMessage("§cKeine Berechtigung."); return; }
            restoreNick(player);
            return;
        }
        if (!player.hasPermission("servertoolkit.nick.others")) { sender.sendMessage("§cKeine Berechtigung."); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage("§cSpieler nicht online."); return; }
        applyNick(target, sender);
    }

    void applyNickFromGui(Player target, CommandSender actor) { applyNick(target, actor); }

    void restoreNickFromGui(Player target, CommandSender actor) {
        if (!actor.hasPermission("servertoolkit.nick.others")) {
            actor.sendMessage("§cKeine Berechtigung.");
            return;
        }
        restoreNick(target);
        actor.sendMessage("§aNick von " + target.getName() + " entfernt.");
    }

    private boolean isNickInUse(String candidate) {
        for (NickSession session : nickSessions.values()) {
            if (session.nick.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private void applyNick(Player player, CommandSender sender) {
        String nick;
        int tries = 0;
        do {
            nick = NICKS.get(ThreadLocalRandom.current().nextInt(NICKS.size()));
            tries++;
        } while (tries < 30 && (Bukkit.getPlayerExact(nick) != null || isNickInUse(nick)));
        String selectedNick = nick;
        var rankSection = plugin.getConfig().getConfigurationSection("ranks");
        List<String> rankIds = rankSection == null ? List.of("member") : new java.util.ArrayList<>(rankSection.getKeys(false));
        String selectedRank = rankIds.isEmpty() ? "member" : rankIds.get(ThreadLocalRandom.current().nextInt(rankIds.size()));
        NickSession old = nickSessions.get(player.getUniqueId());
        PlayerProfile original = old == null ? player.getPlayerProfile() : old.originalProfile;
        String skinName = SKIN_PROFILES.get(ThreadLocalRandom.current().nextInt(SKIN_PROFILES.size()));
        PlayerProfile skinLookup = Bukkit.createProfile(null, skinName);
        player.sendMessage("§eZufälliger Nickname wird eingerichtet …");
        skinLookup.update().whenComplete((skinProfile, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerProfile disguised = Bukkit.createProfile(player.getUniqueId(), selectedNick);
            if (error == null && skinProfile != null && skinProfile.hasTextures()) disguised.setProperties(skinProfile.getProperties());
            else disguised.setProperties(original.getProperties());
            nickSessions.put(player.getUniqueId(), new NickSession(original, selectedNick, selectedRank, skinName));
            player.setPlayerProfile(disguised);
            refreshDisguisedProfileForViewers(player);
            if (plugin.networkSyncManager() != null) {
                plugin.networkSyncManager().publishNick(player, selectedNick, selectedRank, skinName);
            }
            plugin.utilityManager().refreshPlayerDisplay(player);
            String rankDisplay = plugin.getConfig().getString("ranks." + selectedRank + ".display", selectedRank);
            player.sendMessage("§aDein Nick-Profil: §f" + selectedNick + " §7| §e" + rankDisplay
                    + "§a. Skin und Rang-Anzeige sind zufällig; echte Rechte bleiben unverändert.");
            if (sender != player) sender.sendMessage("§aNick für " + player.getName() + " gesetzt: " + selectedNick);
        }));
    }

    private void refreshDisguisedProfileForViewers(Player target) {
        // setPlayerProfile changes the server-side profile, but existing client entities
        // can keep the old GameProfile until they are respawned. Hide/show forces a fresh
        // player-info/entity spawn for every viewer (the target never hides itself).
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            viewer.hidePlayer(plugin, target);
            viewer.showPlayer(plugin, target);
        }
    }

    void restoreNick(Player player) {
        restoreNick(player, true);
    }

    private void restoreNick(Player player, boolean synchronize) {
        NickSession session = nickSessions.remove(player.getUniqueId());
        if (session == null) { player.sendMessage("§7Du bist nicht genickt."); return; }
        player.setPlayerProfile(session.originalProfile);
        refreshDisguisedProfileForViewers(player);
        plugin.utilityManager().refreshPlayerDisplay(player);
        if (synchronize && plugin.networkSyncManager() != null) {
            plugin.networkSyncManager().clearNick(player);
        }
        player.sendMessage("§aDein echter Name und Skin wurden wiederhergestellt.");
    }

    void handleUnnick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cNur im Spiel verfügbar."); return; }
        if (args.length == 0) {
            if (!player.hasPermission("servertoolkit.nick")) { player.sendMessage("§cKeine Berechtigung."); return; }
            restoreNick(player);
            return;
        }
        if (!player.hasPermission("servertoolkit.nick.others")) { player.sendMessage("§cKeine Berechtigung."); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { player.sendMessage("§cSpieler nicht online."); return; }
        restoreNick(target);
        player.sendMessage("§aNick von " + target.getName() + " entfernt.");
    }

    boolean prankEnabled(Player player) { return isPrankEnabled(player); }

    void applySyncedPrank(Player player, boolean enabled) {
        plugin.getConfig().set("prank-enabled." + player.getUniqueId(), enabled);
        plugin.saveConfig();
    }

    boolean hasLag(UUID playerId) { return lagSessions.containsKey(playerId); }
    boolean hasCrash(UUID playerId) { return crashTasks.containsKey(playerId); }

    void togglePrank(Player player) {
        boolean enabled = !isPrankEnabled(player);
        plugin.getConfig().set("prank-enabled." + player.getUniqueId(), enabled);
        plugin.saveConfig();
        if (plugin.networkSyncManager() != null) plugin.networkSyncManager().publishPrank(player, enabled);
        player.sendMessage(enabled ? "§aPranks aktiviert." : "§cPranks deaktiviert.");
        if (!enabled) {
            stopLag(player, "§cDeine laufende Lag-Simulation wurde beendet.");
            Integer task = crashTasks.remove(player.getUniqueId());
            if (task != null) {
                Bukkit.getScheduler().cancelTask(task);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.NAUSEA);
            }
        }
    }

    void guiStartLag(Player target, Player actor) {
        if (!actor.hasPermission("servertoolkit.lag")) { actor.sendMessage("§cKeine Berechtigung."); return; }
        if (target.equals(actor) || !isPrankEnabled(target)) { actor.sendMessage("§cSpieler hat Pranks deaktiviert oder ist du selbst."); return; }
        startLag(target, 8);
        actor.sendMessage("§aLag-Simulation für " + target.getName() + " gestartet.");
    }

    void guiStartCrash(Player target, Player actor) {
        if (!actor.hasPermission("servertoolkit.crash")) { actor.sendMessage("§cKeine Berechtigung."); return; }
        if (target.equals(actor) || !isPrankEnabled(target)) { actor.sendMessage("§cSpieler hat Pranks deaktiviert oder ist du selbst."); return; }
        startCrash(target);
        actor.sendMessage("§aCrash-Simulation für " + target.getName() + " gestartet.");
    }

    void guiStopLag(Player target, Player actor) {
        if (!actor.hasPermission("servertoolkit.lag")) return;
        stopLag(target, "§aDie Lag-Simulation wurde beendet.");
        actor.sendMessage("§aLag-Simulation für " + target.getName() + " gestoppt.");
    }

    void guiStopCrash(Player target, Player actor) {
        if (!actor.hasPermission("servertoolkit.crash")) return;
        Integer task = crashTasks.remove(target.getUniqueId());
        if (task != null) Bukkit.getScheduler().cancelTask(task);
        target.removePotionEffect(PotionEffectType.BLINDNESS);
        target.removePotionEffect(PotionEffectType.NAUSEA);
        target.sendMessage("§aDie Crash-Simulation wurde abgebrochen.");
        actor.sendMessage("§aCrash-Simulation für " + target.getName() + " gestoppt.");
    }

    void applySyncedNick(Player player, String nick, String rankId, String skinName) {
        if (nick == null || !nick.matches("[A-Za-z0-9_]{3,16}") || rankId == null
                || !rankId.matches("[a-zA-Z0-9_-]{1,32}") || skinName == null
                || !skinName.matches("[A-Za-z0-9_]{1,16}")) return;
        NickSession existing = nickSessions.get(player.getUniqueId());
        PlayerProfile original = existing == null ? player.getPlayerProfile() : existing.originalProfile;
        PlayerProfile lookup = Bukkit.createProfile(null, skinName);
        lookup.update().whenComplete((skinProfile, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerProfile disguised = Bukkit.createProfile(player.getUniqueId(), nick);
            if (error == null && skinProfile != null && skinProfile.hasTextures()) disguised.setProperties(skinProfile.getProperties());
            else disguised.setProperties(original.getProperties());
            nickSessions.put(player.getUniqueId(), new NickSession(original, nick, rankId, skinName));
            player.setPlayerProfile(disguised);
            plugin.utilityManager().refreshPlayerDisplay(player);
        }));
    }

    void clearSyncedNick(Player player) {
        restoreNick(player, false);
    }

    String nickName(Player player) {
        NickSession session = nickSessions.get(player.getUniqueId());
        return session == null ? null : session.nick;
    }

    String nickRankId(Player player) {
        NickSession session = nickSessions.get(player.getUniqueId());
        return session == null ? null : session.rankId;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        LagSession lag = lagSessions.remove(id);
        if (lag != null) Bukkit.getScheduler().cancelTask(lag.task);
        Integer crash = crashTasks.remove(id);
        if (crash != null) Bukkit.getScheduler().cancelTask(crash);
        nickSessions.remove(id);
    }

    private record LagSession(int task, Map<PotionEffectType, PotionEffect> previous) {}
    private record NickSession(PlayerProfile originalProfile, String nick, String rankId, String skinName) {}
}
