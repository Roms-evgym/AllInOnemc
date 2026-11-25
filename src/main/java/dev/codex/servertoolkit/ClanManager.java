package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ClanManager {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;

    public ClanManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException exception) {
                plugin.getLogger().severe("Konnte clans.yml nicht erstellen: " + exception.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0) { openGui(player); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui" -> openGui(player);
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "leave" -> leave(player);
            case "disband" -> disband(player);
            case "info" -> info(player);
            case "chat" -> chat(player, args);
            case "top" -> top(player);
            default -> help(player);
        }
    }

    public String clanKey(UUID playerId) {
        ConfigurationSection clans = data.getConfigurationSection("clans");
        if (clans == null) return null;
        for (String key : clans.getKeys(false)) {
            List<String> members = data.getStringList("clans." + key + ".members");
            if (members.contains(playerId.toString())) return key;
        }
        return null;
    }

    public String clanName(UUID playerId) {
        String key = clanKey(playerId);
        return key == null ? null : data.getString("clans." + key + ".name", key);
    }

    public List<String> clanKeysWithOnlineMembers() {
        ConfigurationSection section = data.getConfigurationSection("clans");
        if (section == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            if (!onlineMembers(key).isEmpty()) result.add(key);
        }
        return result;
    }

    public List<Player> onlineMembers(String key) {
        List<Player> result = new ArrayList<>();
        for (String id : data.getStringList("clans." + key + ".members")) {
            try {
                Player player = Bukkit.getPlayer(UUID.fromString(id));
                if (player != null && player.isOnline()) result.add(player);
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    public String displayName(String key) {
        return data.getString("clans." + key + ".name", key);
    }

    public void recordMaceWin(UUID playerId) {
        String key = clanKey(playerId);
        if (key == null) return;
        String path = "clans." + key + ".maceWins";
        data.set(path, data.getInt(path) + 1);
        save();
    }

    private void openGui(Player player) {
        String key = clanKey(player.getUniqueId());
        List<PopupMenuManager.MenuOption> options = new ArrayList<>();
        if (key == null) {
            options.add(new PopupMenuManager.MenuOption("Clan erstellen", "Erstelle einen Clan mit /clan create <Name>", () -> player.sendMessage(color("&eNutze: /clan create <Name>"))));
            options.add(new PopupMenuManager.MenuOption("Einladung annehmen", "Nimmt deine offene Clan-Einladung an", () -> accept(player)));
        } else {
            options.add(new PopupMenuManager.MenuOption("Clan-Informationen", "Zeigt Leitung, Mitglieder und Siege", () -> info(player)));
            options.add(new PopupMenuManager.MenuOption("Mitglieder einladen", "Einladung mit /clan invite <Spieler>", () -> player.sendMessage(color("&eNutze: /clan invite <Spieler>"))));
            if (data.getString("clans." + key + ".leader", "").equals(player.getUniqueId().toString())) {
                options.add(new PopupMenuManager.MenuOption("Clan auflösen", "Löst deinen Clan auf", () -> disband(player)));
            } else {
                options.add(new PopupMenuManager.MenuOption("Clan verlassen", "Verlasse deinen aktuellen Clan", () -> leave(player)));
            }
            options.add(new PopupMenuManager.MenuOption("Clan-Chat Hilfe", "Nachrichten mit /clan chat <Nachricht>", () -> player.sendMessage(color("&eNutze: /clan chat <Nachricht>"))));
        }
        options.add(new PopupMenuManager.MenuOption("Clan-Rangliste", "Zeigt die besten Clans nach Mace-Siegen", () -> top(player)));
        plugin.popupMenuManager().showMenu(player, "AllInOneMC – Clans", key == null ? "Du bist noch in keinem Clan." : "Clan: " + displayName(key), options);
    }

    private void create(Player player, String[] args) {
        if (args.length < 2 || !args[1].matches("[A-Za-z0-9_]{3,16}")) {
            player.sendMessage(color("&cNutzung: /clan create <3-16 Zeichen, Buchstaben/Zahlen>"));
            return;
        }
        if (clanKey(player.getUniqueId()) != null) {
            player.sendMessage(color("&cDu bist bereits in einem Clan."));
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (data.contains("clans." + key)) {
            player.sendMessage(color("&cDieser Clanname ist bereits vergeben."));
            return;
        }
        data.set("clans." + key + ".name", args[1]);
        data.set("clans." + key + ".leader", player.getUniqueId().toString());
        data.set("clans." + key + ".members", List.of(player.getUniqueId().toString()));
        data.set("clans." + key + ".memberNames." + player.getUniqueId(), player.getName());
        data.set("clans." + key + ".maceWins", 0);
        save();
        player.sendMessage(color("&aClan &f" + args[1] + " &awurde gegründet."));
    }

    private void invite(Player player, String[] args) {
        String key = clanKey(player.getUniqueId());
        if (key == null) {
            player.sendMessage(color("&cDu bist in keinem Clan."));
            return;
        }
        if (!data.getString("clans." + key + ".leader", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cNur die Clanleitung kann einladen."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(color("&cNutzung: /clan invite <Spieler>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(color("&cDer Spieler muss online sein."));
            return;
        }
        if (clanKey(target.getUniqueId()) != null) {
            player.sendMessage(color("&cDieser Spieler ist bereits in einem Clan."));
            return;
        }
        data.set("invites." + target.getUniqueId(), key);
        save();
        target.sendMessage(color("&aDu wurdest zu &f" + data.getString("clans." + key + ".name") + " &aeingeladen."));
        target.sendMessage(color("&7Mit &f/clan accept &7nimmst du an."));
        player.sendMessage(color("&aEinladung an &f" + target.getName() + " &agesendet."));
    }

    private void accept(Player player) {
        if (clanKey(player.getUniqueId()) != null) {
            player.sendMessage(color("&cDu bist bereits in einem Clan."));
            return;
        }
        String key = data.getString("invites." + player.getUniqueId());
        if (key == null || !data.contains("clans." + key)) {
            player.sendMessage(color("&cDu hast keine offene Clan-Einladung."));
            return;
        }
        List<String> members = new ArrayList<>(data.getStringList("clans." + key + ".members"));
        if (members.size() >= plugin.getConfig().getInt("clans.max-members", 8)) {
            player.sendMessage(color("&cDieser Clan ist voll."));
            return;
        }
        members.add(player.getUniqueId().toString());
        data.set("clans." + key + ".members", members);
        data.set("clans." + key + ".memberNames." + player.getUniqueId(), player.getName());
        data.set("invites." + player.getUniqueId(), null);
        save();
        player.sendMessage(color("&aDu bist dem Clan &f" + data.getString("clans." + key + ".name") + " &abeigetreten."));
        tellMembers(key, color("&e" + player.getName() + " &7ist dem Clan beigetreten."));
    }

    private void leave(Player player) {
        String key = clanKey(player.getUniqueId());
        if (key == null) {
            player.sendMessage(color("&cDu bist in keinem Clan."));
            return;
        }
        if (data.getString("clans." + key + ".leader", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cAls Clanleitung nutze /clan disband."));
            return;
        }
        List<String> members = new ArrayList<>(data.getStringList("clans." + key + ".members"));
        members.remove(player.getUniqueId().toString());
        data.set("clans." + key + ".members", members);
        data.set("clans." + key + ".memberNames." + player.getUniqueId(), null);
        save();
        player.sendMessage(color("&eDu hast den Clan verlassen."));
        tellMembers(key, color("&e" + player.getName() + " &7hat den Clan verlassen."));
    }

    private void disband(Player player) {
        String key = clanKey(player.getUniqueId());
        if (key == null || !data.getString("clans." + key + ".leader", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cNur die Clanleitung kann den Clan auflösen."));
            return;
        }
        tellMembers(key, color("&cDer Clan wurde aufgelöst."));
        data.set("clans." + key, null);
        save();
    }

    private void info(Player player) {
        String key = clanKey(player.getUniqueId());
        if (key == null) {
            player.sendMessage(color("&cDu bist in keinem Clan."));
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : data.getStringList("clans." + key + ".members")) {
            names.add(data.getString("clans." + key + ".memberNames." + id, id));
        }
        player.sendMessage(color("&6Clan &e" + data.getString("clans." + key + ".name")));
        player.sendMessage(color("&7Leitung: &f" + data.getString("clans." + key + ".memberNames." + data.getString("clans." + key + ".leader"), "unbekannt")));
        player.sendMessage(color("&7Mitglieder: &f" + String.join(", ", names)));
        player.sendMessage(color("&7Mace-Duelsiege: &f" + data.getInt("clans." + key + ".maceWins")));
    }

    private void chat(Player player, String[] args) {
        String key = clanKey(player.getUniqueId());
        if (key == null) {
            player.sendMessage(color("&cDu bist in keinem Clan."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(color("&cNutzung: /clan chat <Nachricht>"));
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        tellMembers(key, color("&8[&6Clan&8] &e" + player.getName() + "&7: " + message));
    }

    private void top(Player player) {
        ConfigurationSection clans = data.getConfigurationSection("clans");
        if (clans == null || clans.getKeys(false).isEmpty()) {
            player.sendMessage(color("&7Noch gibt es keine Clans."));
            return;
        }
        List<String> keys = new ArrayList<>(clans.getKeys(false));
        keys.sort(Comparator.comparingInt((String key) -> data.getInt("clans." + key + ".maceWins")).reversed());
        player.sendMessage(color("&6&m-----&r &eMace-Clanrangliste &6&m-----"));
        for (int i = 0; i < Math.min(5, keys.size()); i++) {
            String key = keys.get(i);
            player.sendMessage(color("&e" + (i + 1) + ". &f" + data.getString("clans." + key + ".name")
                    + " &7- &b" + data.getInt("clans." + key + ".maceWins") + " Siege"));
        }
    }

    private void tellMembers(String key, String message) {
        for (String id : data.getStringList("clans." + key + ".members")) {
            Player member = Bukkit.getPlayer(UUID.fromString(id));
            if (member != null) member.sendMessage(message);
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(color("&6Clan-Befehle: &f/clan create, invite, accept, leave, disband, info, chat, top"));
    }

    private void save() {
        try {
            data.save(file);
            if (plugin.utilityManager() != null) {
                for (Player online : Bukkit.getOnlinePlayers()) plugin.utilityManager().refreshPlayerDisplay(online);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Konnte clans.yml nicht speichern: " + exception.getMessage());
        }
    }

    private String color(String message) { return ChatColor.translateAlternateColorCodes('&', message); }
}
