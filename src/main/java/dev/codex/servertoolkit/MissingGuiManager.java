package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MissingGuiManager implements Listener {
    private final ServerToolkit plugin;
    private final Map<UUID, String> chatActions = new ConcurrentHashMap<>();

    MissingGuiManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void show(Player p, String title, String description, List<PopupMenuManager.MenuOption> options) {
        plugin.popupMenuManager().showMenu(p, title, description, options);
    }

    private void option(List<PopupMenuManager.MenuOption> options, String label, String description, Runnable action) {
        options.add(new PopupMenuManager.MenuOption(label, description, action));
    }

    private void mainOption(Player p, List<PopupMenuManager.MenuOption> options, String label, String command, String permission) {
        if (permission == null || p.hasPermission(permission)) {
            option(options, label, "/" + command, () -> p.performCommand(command));
        }
    }

    void openMain(Player p) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        if (plugin.getConfig().getString("mode", "LOBBY").equalsIgnoreCase("LOBBY"))
            mainOption(p, o, "Abmelden", "logout", null);
        mainOption(p,o,"Geld & Überweisen","money",null);
        mainOption(p,o,"Kopfgeld","bounty",null);
        mainOption(p,o,"Spielzeit","playtime",null);
        mainOption(p,o,"Statistiken","stats",null);
        mainOption(p,o,"Homes","homes",null);
        mainOption(p,o,"Teleport-Anfrage","tpa",null);
        mainOption(p,o,"Anfrage annehmen","tpaccept",null);
        mainOption(p,o,"Anfrage ablehnen","tpdeny",null);
        mainOption(p,o,"Zum Spawn","spawn",null);
        mainOption(p,o,"Code einlösen","code",null);
        mainOption(p,o,"Codes erstellen","setcode","servertoolkit.code.create");
        mainOption(p,o,"Fly umschalten","fly",null);
        mainOption(p,o,"God-Modus","god",null);
        mainOption(p,o,"Hinsetzen / Aufstehen","sit",null);
        mainOption(p,o,"Zur Lobby","hub",null);
        mainOption(p,o,"Eigene Enderchest","echest",null);
        mainOption(p,o,"Banhammer-Menü","banhammer","servertoolkit.ban.manage");
        mainOption(p,o,"Lag-Simulation","lag","servertoolkit.lag");
        mainOption(p,o,"Crash-Simulation","crash","servertoolkit.crash");
        mainOption(p,o,"Shop","shop",null);
        mainOption(p,o,"Verkaufen","sell",null);
        mainOption(p,o,"Clans","clan",null);
        mainOption(p,o,"Kits","kit",null);
        mainOption(p,o,"Ränge","ranks",null);
        mainOption(p,o,"Auktionshaus","ah",null);
        mainOption(p,o,"Aufträge","orders",null);
        mainOption(p,o,"Clan-Duell starten","clanduel","servertoolkit.clanduel");
        mainOption(p,o,"Fakebase platzieren","fakebase","servertoolkit.admin");
        option(o,"Fake-Erzader","Erztyp und Anzahl eingeben",() -> prompt(p,"fakore","Gib Erztyp und optionale Anzahl ein (z. B. DIAMOND_ORE 8):"));
        mainOption(p,o,"Mace","mace",null);
        mainOption(p,o,"Server wechseln","servers",null);
        mainOption(p,o,"Voice-Chat","voice",null);
        mainOption(p,o,"Nick & Skin","nick",null);
        mainOption(p,o,"Prank-Einstellungen","prank",null);
        mainOption(p,o,"Ban-Rechte","banrank","servertoolkit.ban.manage");
        mainOption(p,o,"Ban-Presets","banpreset","servertoolkit.ban.manage");
        mainOption(p,o,"Spieler-Inventar ansehen","invsee","servertoolkit.staff");
        mainOption(p,o,"Spieler-Enderchest ansehen","ecsee","servertoolkit.staff");
        mainOption(p,o,"Gegenstand entsorgen","trash",null);
        mainOption(p,o,"Spieler bannen","ban","servertoolkit.ban");
        mainOption(p,o,"Spieler bestrafen","punish","servertoolkit.ban");
        mainOption(p,o,"Vanish","vanish","servertoolkit.vanish");
        mainOption(p,o,"Admin-Geldverwaltung","eco","servertoolkit.eco.admin");
        mainOption(p,o,"Spawn setzen","setspawn","servertoolkit.admin");
        option(o,"Schließen","Menü schließen",() -> {});
        show(p,"AllInOneMC – Hauptmenü","Wähle eine Funktion aus.",o);
    }

    void openStats(Player p) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        option(o,"Meine Statistiken","Kills, Deaths und gesamte Spielzeit",() -> p.performCommand("stats me"));
        for (Player target : Bukkit.getOnlinePlayers()) {
            option(o,"Stats: " + target.getName(),"Statistiken dieses Spielers anzeigen",
                    () -> p.performCommand("stats " + target.getName()));
        }
        option(o,"Spieler suchen","Auch Offline-Spieler über ihren Namen suchen",
                () -> prompt(p,"stats-target","Gib den Spielernamen ein:"));
        option(o,"Zurück","Hauptmenü öffnen",() -> openMain(p));
        show(p,"Statistiken","Wähle deine eigenen oder die Statistiken eines Spielers.",o);
    }

    void openCode(Player p) { prompt(p,"code","Gib deinen Einlösecode ein:"); }

    void openMoney(Player p) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        option(o,"Guthaben: " + plugin.economyManager().formatted(p.getUniqueId()),"Aktueller Kontostand",() -> {});
        option(o,"Geld senden","Spieler auswählen",() -> openTargets(p,"pay"));
        option(o,"Hauptmenü","Zurück",() -> openMain(p));
        show(p,"Geld & Überweisen","Wähle eine Aktion.",o);
    }

    void openTargets(Player p, String action) {
        String title = switch (action) {
            case "pay" -> "Geld senden – Empfänger";
            case "tpa" -> "Teleport – Ziel auswählen";
            case "bounty" -> "Kopfgeld – Ziel auswählen";
            case "playtime" -> "Spielzeit – Spieler auswählen";
            case "fly" -> "Fly – Spieler auswählen";
            case "god" -> "God – Spieler auswählen";
            case "lag" -> "Lag-Simulation – Ziel";
            case "crash" -> "Crash-Simulation – Ziel";
            case "eco-give", "eco-take", "eco-set" -> "Geldverwaltung – Spieler";
            default -> "Spieler auswählen";
        };
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(p) && List.of("pay","tpa","bounty").contains(action)) continue;
            option(o,target.getName(),"Spieler auswählen",() -> selectTarget(p,target.getUniqueId(),action));
        }
        option(o,"Zurück","Hauptmenü öffnen",() -> openMain(p));
        show(p,title,"Wähle einen Online-Spieler aus.",o);
    }

    private void selectTarget(Player p, UUID id, String action) {
        Player target = Bukkit.getPlayer(id);
        if (target == null) { p.sendMessage("§cSpieler ist nicht mehr online."); return; }
        switch (action) {
            case "pay", "bounty", "eco-give", "eco-take", "eco-set" ->
                    prompt(p,"amount:" + action + ":" + target.getName(),"Gib den Betrag für " + target.getName() + " ein:");
            case "tpa" -> p.performCommand("tpa " + target.getName());
            case "playtime" -> p.performCommand("playtime " + target.getName());
            case "fly", "god", "invsee", "ecsee" -> p.performCommand(action + " " + target.getName());
            case "lag" -> plugin.contentToolsManager().guiStartLag(target,p);
            case "crash" -> plugin.contentToolsManager().guiStartCrash(target,p);
            case "ban" -> prompt(p,"ban-reason:" + target.getName(),"Gib den Ban-Grund für " + target.getName() + " ein:");
            case "punish" -> prompt(p,"punish-preset:" + target.getName(),"Gib die Preset-ID für " + target.getName() + " ein:");
            default -> {}
        }
    }

    void openHomes(Player p, boolean delete) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        ConfigurationSection homes = plugin.getConfig().getConfigurationSection("named-homes." + p.getUniqueId());
        if (homes != null) for (String name : homes.getKeys(false)) {
            option(o,(delete ? "Löschen: " : "Teleport: ") + name,"Home auswählen",
                    () -> { if (delete) { p.performCommand("delhome " + name); openHomes(p,true); } else p.performCommand("home " + name); });
        }
        if (plugin.getConfig().contains("homes." + p.getUniqueId()) && (homes == null || !homes.contains("home"))) {
            option(o,delete ? "Löschen: home" : "Teleport: home","Altes Home",
                    () -> { if (delete) { p.performCommand("delhome home"); openHomes(p,true); } else p.performCommand("home home"); });
        }
        if (!delete) {
            option(o,"Home hier setzen","Standardname: home",() -> p.performCommand("sethome home"));
            option(o,"Home mit Namen setzen","Namen eingeben",() -> prompt(p,"sethome:named","Gib einen Home-Namen ein:"));
            option(o,"Homes löschen","Home auswählen",() -> openHomes(p,true));
        }
        option(o,"Hauptmenü","Zurück",() -> openMain(p));
        show(p,delete ? "Home löschen" : "Deine Homes", "Wähle eine Aktion.",o);
    }

    void openSetCode(Player p) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        option(o,"Rang-Code erstellen","Code und Rang-ID eingeben",() -> prompt(p,"setcode-code:rank","Gib den neuen Code ein:"));
        option(o,"Geld-Code erstellen","Code und Geldbetrag eingeben",() -> prompt(p,"setcode-code:money","Gib den neuen Code ein:"));
        option(o,"Hauptmenü","Zurück",() -> openMain(p));
        show(p,"Belohnungscode erstellen","Wähle den Belohnungstyp.",o);
    }

    void openEco(Player p) {
        List<PopupMenuManager.MenuOption> o = new ArrayList<>();
        option(o,"Geld geben","Spieler auswählen",() -> openTargets(p,"eco-give"));
        option(o,"Geld abziehen","Spieler auswählen",() -> openTargets(p,"eco-take"));
        option(o,"Guthaben setzen","Spieler auswählen",() -> openTargets(p,"eco-set"));
        option(o,"Hauptmenü","Zurück",() -> openMain(p));
        show(p,"Admin-Geldverwaltung","Wähle eine Aktion.",o);
    }

    private void prompt(Player p, String action, String instruction) {
        boolean shown = plugin.popupMenuManager().showInputDialog(p, "Eingabe", instruction,
                "Wert eingeben", 160, input -> handleDialogInput(p, action, input));
        if (shown) return;
        chatActions.put(p.getUniqueId(),action);
        p.closeInventory();
        p.sendMessage("§e" + instruction);
        p.sendMessage("§7Schreibe den Wert in den Chat. Mit §c-abbrechen §7brichst du ab.");
    }

    private void handleDialogInput(Player player, String action, String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isBlank() || input.length() > 160 || input.chars().anyMatch(Character::isISOControl)) {
            player.sendMessage("§cUngültige Eingabe."); return;
        }
        if (action.startsWith("stats-target")) {
            if (!safeName(input)) { player.sendMessage("§cUngültiger Spielername."); return; }
            player.performCommand("stats " + input); return;
        }
        if (action.startsWith("sethome:")) {
            String name = safeName(input);
            if (name == null) { player.sendMessage("§cUngültiger Home-Name."); return; }
            player.performCommand("sethome " + name); return;
        }
        if (action.startsWith("code")) {
            String code = safeToken(input);
            if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
            player.performCommand("code " + code); return;
        }
        if (action.startsWith("setcode-code:")) {
            String type = action.substring("setcode-code:".length());
            String code = safeToken(input);
            if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
            prompt(player,"setcode-value:" + type + ":" + code,
                    type.equals("rank") ? "Gib jetzt die Rang-ID ein:" : "Gib jetzt den Geldbetrag ein:"); return;
        }
        if (action.startsWith("setcode-value:")) {
            String[] parts = action.split(":",3);
            if (parts.length != 3) return;
            String value = parts[1].equals("money")
                    ? (input.matches("[0-9]+([.,][0-9]{1,2})?") ? input.replace(',','.') : null)
                    : safeToken(input);
            if (value == null) { player.sendMessage("§cUngültige Eingabe."); return; }
            player.performCommand("setcode " + parts[2] + " " + parts[1] + " " + value); return;
        }
        if (action.equals("fakore")) {
            if (!input.matches("[A-Za-z0-9_]{2,32}( [1-9][0-9]{0,2})?")) {
                player.sendMessage("§cUngültig. Beispiel: DIAMOND_ORE 8"); return;
            }
            player.performCommand("fakore " + input); return;
        }
        if (action.startsWith("ban-reason:")) {
            String reason = input.replaceAll("[^A-Za-z0-9 _.,!?-]","");
            if (reason.isBlank() || reason.length() > 100) { player.sendMessage("§cUngültiger Grund."); return; }
            player.performCommand("ban " + action.substring("ban-reason:".length()) + " " + reason); return;
        }
        if (action.startsWith("punish-preset:")) {
            String preset = safeToken(input);
            if (preset == null) { player.sendMessage("§cUngültige Preset-ID."); return; }
            player.performCommand("punish " + action.substring("punish-preset:".length()) + " " + preset); return;
        }
        if (action.startsWith("amount:")) {
            String[] parts = action.split(":",3);
            if (parts.length != 3 || !input.matches("[0-9]+([.,][0-9]{1,2})?")) {
                player.sendMessage("§cBitte einen gültigen Betrag eingeben."); return;
            }
            String amount = input.replace(',','.');
            switch (parts[1]) {
                case "pay" -> player.performCommand("pay " + parts[2] + " " + amount);
                case "bounty" -> player.performCommand("bounty " + parts[2] + " " + amount);
                case "eco-give", "eco-take", "eco-set" -> player.performCommand("eco " + parts[1].substring(4) + " " + parts[2] + " " + amount);
                default -> {}
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String action = chatActions.remove(event.getPlayer().getUniqueId());
        if (action == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin,() -> {
            if (input.equalsIgnoreCase("-abbrechen")) { player.sendMessage("§cAbgebrochen."); return; }
            if (action.startsWith("sethome:")) {
                String name = safeName(input);
                if (name == null) { player.sendMessage("§cUngültiger Home-Name."); return; }
                player.performCommand("sethome " + name); return;
            }
            if (action.startsWith("code")) {
                String code = safeToken(input);
                if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
                player.performCommand("code " + code); return;
            }
            if (action.startsWith("setcode-code:")) {
                String type = action.substring("setcode-code:".length());
                String code = safeToken(input);
                if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
                prompt(player,"setcode-value:" + type + ":" + code,
                        type.equals("rank") ? "Gib jetzt die Rang-ID ein:" : "Gib jetzt den Geldbetrag ein:"); return;
            }
            if (action.startsWith("setcode-value:")) {
                String[] parts = action.split(":",3);
                if (parts.length != 3) return;
                String value = parts[1].equals("money")
                        ? (input.matches("[0-9]+([.,][0-9]{1,2})?") ? input.replace(',','.') : null)
                        : safeToken(input);
                if (value == null) { player.sendMessage("§cUngültige Eingabe."); return; }
                player.performCommand("setcode " + parts[2] + " " + parts[1] + " " + value); return;
            }
            if (action.equals("fakore")) {
                if (!input.matches("[A-Za-z0-9_]{2,32}( [1-9][0-9]{0,2})?")) {
                    player.sendMessage("§cUngültig. Beispiel: DIAMOND_ORE 8"); return;
                }
                player.performCommand("fakore " + input); return;
            }
            if (action.startsWith("ban-reason:")) {
                String reason = input.replaceAll("[^A-Za-z0-9 _.,!?-]","");
                if (reason.isBlank() || reason.length() > 100) { player.sendMessage("§cUngültiger Grund."); return; }
                player.performCommand("ban " + action.substring("ban-reason:".length()) + " " + reason); return;
            }
            if (action.startsWith("punish-preset:")) {
                String preset = safeToken(input);
                if (preset == null) { player.sendMessage("§cUngültige Preset-ID."); return; }
                player.performCommand("punish " + action.substring("punish-preset:".length()) + " " + preset); return;
            }
            if (action.startsWith("amount:")) {
                String[] parts = action.split(":",3);
                if (parts.length != 3 || !input.matches("[0-9]+([.,][0-9]{1,2})?")) {
                    player.sendMessage("§cBitte einen gültigen Betrag eingeben."); return;
                }
                String amount = input.replace(',','.');
                switch (parts[1]) {
                    case "pay" -> player.performCommand("pay " + parts[2] + " " + amount);
                    case "bounty" -> player.performCommand("bounty " + parts[2] + " " + amount);
                    case "eco-give", "eco-take", "eco-set" -> player.performCommand("eco " + parts[1].substring(4) + " " + parts[2] + " " + amount);
                    default -> {}
                }
            }
        });
    }

    private String safeToken(String input) { return input.matches("[A-Za-z0-9_-]{3,32}") ? input : null; }
    private String safeName(String input) { return input.matches("[A-Za-z0-9_-]{1,16}") ? input : null; }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { chatActions.remove(event.getPlayer().getUniqueId()); }
}
