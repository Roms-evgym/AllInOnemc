package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ContentToolsGuiManager {
    private final ServerToolkit plugin;

    ContentToolsGuiManager(ServerToolkit plugin) { this.plugin = plugin; }

    private void show(Player player, String title, String description, List<PopupMenuManager.MenuOption> options) {
        plugin.popupMenuManager().showMenu(player,title,description,options);
    }

    private void add(List<PopupMenuManager.MenuOption> options,String label,String description,Runnable action) {
        options.add(new PopupMenuManager.MenuOption(label,description,action));
    }

    void openNick(Player player) {
        List<PopupMenuManager.MenuOption> options = new ArrayList<>();
        add(options,"Zufälligen Nick aktivieren","Nickname und Skin zufällig setzen",() -> {
            if (player.hasPermission("servertoolkit.nick")) player.performCommand("nick random");
            else player.sendMessage("§cKeine Berechtigung.");
        });
        add(options,"Eigenen Nick entfernen","Originalnamen und Skin wiederherstellen",() -> plugin.contentToolsManager().restoreNick(player));
        if (player.hasPermission("servertoolkit.nick.others")) {
            add(options,"Spieler nicken","Zielspieler auswählen",() -> openTargets(player,"nick"));
            add(options,"Nick eines Spielers entfernen","Zielspieler auswählen",() -> openTargets(player,"unnick"));
        }
        add(options,"Hauptmenü","Zurück",() -> plugin.missingGuiManager().openMain(player));
        show(player,"Nick & Skin","Wähle eine Aktion.",options);
    }

    void openPrank(Player player) {
        boolean enabled = plugin.contentToolsManager().prankEnabled(player);
        List<PopupMenuManager.MenuOption> options = new ArrayList<>();
        add(options,enabled ? "Pranks deaktivieren" : "Pranks aktivieren",
                enabled ? "Lag-/Crash-Simulationen für dich sperren" : "Simulationen für dich erlauben",
                () -> {
                    if (player.hasPermission("servertoolkit.prank.toggle"))
                        plugin.contentToolsManager().togglePrank(player);
                    else player.sendMessage("§cKeine Berechtigung.");
                    openPrank(player);
                });
        add(options,"Hauptmenü","Zurück",() -> plugin.missingGuiManager().openMain(player));
        show(player,"Prank-Einstellungen","Aktueller Status: " + (enabled ? "AKTIV" : "DEAKTIVIERT"),options);
    }

    void openTargets(Player actor, String type) {
        String title = switch (type) {
            case "nick" -> "Nick – Spieler auswählen";
            case "unnick" -> "Unnick – Spieler auswählen";
            case "lag" -> "Lag-Simulation – Ziel auswählen";
            default -> "Crash-Simulation – Ziel auswählen";
        };
        List<PopupMenuManager.MenuOption> options = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(actor)) continue;
            String detail = type.equals("nick") ? "Zufälligen Nick und Skin setzen"
                    : type.equals("unnick") ? "Nick und Skin zurücksetzen"
                    : "Prank-Status: " + (plugin.contentToolsManager().prankEnabled(target) ? "Aktiviert" : "Deaktiviert");
            add(options,target.getName(),detail,() -> select(actor,target.getUniqueId(),type));
        }
        add(options,"Zurück","Vorheriges Menü",() -> {
            if (type.equals("nick") || type.equals("unnick")) openNick(actor);
            else plugin.missingGuiManager().openMain(actor);
        });
        show(actor,title,"Wähle einen Online-Spieler aus.",options);
    }

    private void select(Player actor, UUID targetId, String type) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) { actor.sendMessage("§cSpieler ist nicht mehr online."); return; }
        ContentToolsManager tools = plugin.contentToolsManager();
        switch (type) {
            case "nick" -> {
                if (actor.hasPermission("servertoolkit.nick.others")) tools.applyNickFromGui(target,actor);
                else actor.sendMessage("§cKeine Berechtigung.");
            }
            case "unnick" -> tools.restoreNickFromGui(target,actor);
            case "lag" -> tools.guiStartLag(target,actor);
            case "crash" -> tools.guiStartCrash(target,actor);
            default -> { }
        }
    }
}
