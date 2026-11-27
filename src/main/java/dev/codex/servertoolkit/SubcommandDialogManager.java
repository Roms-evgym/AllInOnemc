package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
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

/** Dialog navigation for every command family that has subcommands. */
final class SubcommandDialogManager implements Listener {
    private final ServerToolkit plugin;
    private final Map<UUID,String> pending = new ConcurrentHashMap<>();

    SubcommandDialogManager(ServerToolkit plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }

    boolean openIfRoot(Player p,String root) {
        String key=root.toLowerCase();
        if (!List.of("clan","kit","ah","order","orders","mace","rank","ranks","voice","vanish",
                "banpreset","banhammer","nick","unnick","prank","lag","crash","clanduel").contains(key)) return false;
        switch(key) {
            case "clan" -> clan(p);
            case "kit" -> kit(p);
            case "ah" -> auction(p);
            case "order","orders" -> orders(p);
            case "mace" -> mace(p);
            case "rank","ranks" -> ranks(p);
            case "voice" -> voice(p);
            case "vanish" -> vanish(p);
            case "banpreset" -> presets(p);
            case "banhammer" -> presets(p);
            case "nick" -> nick(p);
            case "unnick" -> nick(p);
            case "prank" -> prank(p);
            case "lag" -> simulation(p,"lag");
            case "crash" -> simulation(p,"crash");
            case "clanduel" -> clanDuel(p);
        }
        return true;
    }

    private void menu(Player p,String title,String desc,PopupMenuManager.MenuOption... options) {
        plugin.popupMenuManager().showMenu(p,title,desc,List.of(options));
    }
    private PopupMenuManager.MenuOption run(Player p,String label,String desc,String command) {
        return new PopupMenuManager.MenuOption(label,desc,()->p.performCommand(command));
    }
    private PopupMenuManager.MenuOption ask(Player p,String label,String desc,String command,String hint) {
        return new PopupMenuManager.MenuOption(label,desc,()->prompt(p,command,hint));
    }
    private void prompt(Player p,String command,String hint) {
        pending.put(p.getUniqueId(),command);
        p.closeInventory();
        p.sendMessage("§e"+hint);
        p.sendMessage("§7Eingabe im Chat · §c-abbrechen §7zum Abbrechen.");
    }

    private void clan(Player p) {
        menu(p,"Clan – Untermenüs","Wähle einen Unterbefehl.",
            ask(p,"Clan erstellen","/clan create <Name>","clan create","Clan-Name (3–16 Zeichen):"),
            ask(p,"Spieler einladen","/clan invite <Spieler>","clan invite","Spielername:");
            run(p,"Einladung annehmen","/clan accept","clan accept"),
            run(p,"Clan verlassen","/clan leave","clan leave"),
            run(p,"Clan auflösen","/clan disband","clan disband"),
            run(p,"Clan-Informationen","/clan info","clan info"),
            ask(p,"Clan-Chat","/clan chat <Nachricht>","clan chat","Clan-Nachricht:"),
            run(p,"Clan-Rangliste","/clan top","clan top"));
    }
    private void kit(Player p) {
        menu(p,"Kits – Untermenüs","Wähle einen Unterbefehl.",
            ask(p,"Kit erstellen","Speichert dein aktuelles Inventar als Kit.","kit create","Kit-ID:"),
            ask(p,"Kit löschen","/kit delete <ID>","kit delete","Kit-ID:"),
            ask(p,"Cooldown setzen","/kit cooldown <ID> <Sekunden>","kit cooldown","Kit-ID und Sekunden, z. B. starter 3600:"),
            ask(p,"Zugriff festlegen","/kit access <ID> all|rank <Rang>|player <Spieler>","kit access","Kit-ID und Zugriff (z. B. starter rank vip):"),
            run(p,"Kit-Verwaltung","Öffnet das Verwaltungsmenü.","kit manage","kit manage"),
            ask(p,"Kit beanspruchen","/kit claim <ID>","kit claim","Kit-ID:"));
    }
    private void auction(Player p) {
        menu(p,"Auktionshaus – Untermenüs","Wähle einen Unterbefehl.",
            run(p,"Auktionen ansehen","Öffnet das Auktionshaus.","ah","ah"),
            ask(p,"Gegenstand verkaufen","Verkauft den Gegenstand in deiner Haupthand.","ah sell","Preis:"),
            ask(p,"Eigene Auktion abbrechen","/ah cancel <ID>","ah cancel","Auktions-ID:"));
    }
    private void orders(Player p) {
        menu(p,"Aufträge – Untermenüs","Wähle einen Unterbefehl.",
            run(p,"Aufträge ansehen","Öffnet die Auftragsübersicht.","orders","orders"),
            ask(p,"Auftrag erstellen","/order create <Material> <Menge> <Belohnung>","order create","Material, Menge und Belohnung (z. B. DIAMOND 16 500):"),
            ask(p,"Auftrag erfüllen","/order fill <ID>","order fill","Auftrags-ID:"),
            ask(p,"Auftrag abbrechen","/order cancel <ID>","order cancel","Auftrags-ID:"));
    }
    private void mace(Player p) {
        menu(p,"Mace – Untermenüs","Wähle eine Aktion.",
            ask(p,"Duell herausfordern","/mace duel <Spieler>","mace duel","Spielername:"),
            run(p,"Duell annehmen","/mace accept","mace accept"),
            run(p,"Duell ablehnen","/mace deny","mace deny"),
            ask(p,"Arena-Punkt setzen","/mace setarena <a|b>","mace setarena","Arena-Punkt A oder B:"),
            run(p,"Eigene Statistiken","/mace stats","mace stats"),
            run(p,"Rangliste","/mace top","mace top"),
            new PopupMenuManager.MenuOption("Event-Untermenü","Join, öffnen, schließen, starten, abbrechen und Status",()->maceEvent(p)));
    }
    private void maceEvent(Player p) {
        menu(p,"Mace-Event","Wähle einen Event-Unterbefehl.",
            run(p,"Beitreten","/mace event join","mace event join"),
            run(p,"Event öffnen","/mace event open","mace event open"),
            run(p,"Event schließen","/mace event close","mace event close"),
            run(p,"Event starten","/mace event start","mace event start"),
            run(p,"Event abbrechen","/mace event cancel","mace event cancel"),
            run(p,"Event-Status","/mace event status","mace event status"));
    }
    private void ranks(Player p) {
        menu(p,"Ränge – Untermenüs","Wähle eine Rang-Aktion.",
            run(p,"Rangliste","/rank list","rank list"),
            ask(p,"Rang vergeben","/rank set <Spieler> <Rang>","rank set","Spielername und Rang-ID:"),
            ask(p,"Rang entfernen","/rank remove <Spieler>","rank remove","Spielername:"),
            ask(p,"Rang erstellen","/rank create <ID>","rank create","Neue Rang-ID:"),
            ask(p,"Berechtigung bearbeiten","/rank perm <Rang> <add|remove> <Permission>","rank perm","Rang, add/remove und Permission-Node:"),
            ask(p,"Rangfarbe ändern","/rank color <Rang> <Farbe>","rank color","Rang-ID und Farbcode (z. B. vip &a):"),
            ask(p,"Rangprefix ändern","/rank prefix <Rang> <Prefix>","rank prefix","Rang-ID und Prefix:"),
            ask(p,"Home-Limit setzen","/rank homes <Rang> <Anzahl>","rank homes","Rang-ID und Anzahl:"));
    }
    private void voice(Player p) {
        menu(p,"Voice-Chat – Untermenüs","Wähle eine Aktion.",
            run(p,"Voice-Chat-Hilfe","/voice","voice"),
            ask(p,"Menüfarbe ändern","/voice color <&Farbcode>","voice color","Farbcode (z. B. &a):"));
    }
    private void vanish(Player p) {
        menu(p,"Vanish – Untermenüs","Wähle eine Aktion.",
            run(p,"Vanish umschalten","/vanish","vanish"),
            run(p,"Sichtbarkeit verwalten","Öffnet die Rang-Auswahl.","vanish gui","vanish gui"));
    }
    private void presets(Player p) {
        menu(p,"Ban-Presets – Untermenüs","Wähle eine Aktion.",
            run(p,"Presets anzeigen","/banpreset list","banpreset list"),
            ask(p,"Preset erstellen/bearbeiten","/banpreset set <ID> <Dauer> <Grund>","banpreset set","ID, Dauer und Grund (z. B. spam 7d Spam):"),
            ask(p,"Preset löschen","/banpreset delete <ID>","banpreset delete","Preset-ID:"),
            ask(p,"Banhammer erhalten","/banhammer <Preset>","banhammer","Preset-ID:"));
    }
    private void nick(Player p) {
        menu(p,"Nick & Skin – Untermenüs","Wähle eine Aktion.",
            run(p,"Zufälligen Nick setzen","/nick random","nick random"),
            ask(p,"Eigenen Nick setzen","/nick <Name>","nick","Gewünschter Nickname:"),
            run(p,"Zufälligen Skin setzen","/nick skin random","nick skin random"),
            run(p,"Nick zurücksetzen","/nick off","nick off"),
            run(p,"Unnick","/unnick","unnick"));
    }
    private void prank(Player p) {
        menu(p,"Prank – Untermenüs","Wähle eine Aktion.",
            run(p,"Pranks aktivieren","/prank on","prank on"),
            run(p,"Pranks deaktivieren","/prank off","prank off"),
            run(p,"Status anzeigen","/prank status","prank status"));
    }
    private void simulation(Player p,String root) {
        menu(p,root.equals("lag")?"Lag-Simulation":"Crash-Simulation","Wähle einen Unterbefehl.",
            run(p,"Simulation starten","Startet die sichere Simulation gemäß den Einstellungen.",""+root,root),
            ask(p,"Simulation für Spieler stoppen","/ "+root+" stop <Spieler>",""+root+" stop","Spielername:"));
    }
    private void clanDuel(Player p) {
        menu(p,"Clan-Duell – Untermenüs","Wähle eine Aktion.",
            ask(p,"Clan herausfordern","/clanduel <Clan>","clanduel","Clan-Name:"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        String command=pending.remove(e.getPlayer().getUniqueId());
        if(command==null)return;
        e.setCancelled(true);
        String input=e.getMessage().trim();
        Player p=e.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(input.equalsIgnoreCase("-abbrechen")){p.sendMessage("§cAbgebrochen.");return;}
            if(input.isBlank()||input.length()>160||input.chars().anyMatch(Character::isISOControl)){
                p.sendMessage("§cUngültige Eingabe.");return;
            }
            p.performCommand(command+" "+input);
        });
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent e){pending.remove(e.getPlayer().getUniqueId());}
}
