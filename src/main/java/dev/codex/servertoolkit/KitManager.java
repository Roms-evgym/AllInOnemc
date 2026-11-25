package dev.codex.servertoolkit;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class KitManager implements Listener {
    private final ServerToolkit plugin;
    private final File file;
    private YamlConfiguration data;

    public KitManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) { open(player); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "delete" -> delete(player, args);
            case "cooldown" -> cooldown(player, args);
            case "access" -> access(player, args);
            case "claim" -> {
                if (args.length < 2) player.sendMessage(color("&cNutzung: /kit claim <Name>"));
                else claim(player, normalize(args[1]));
            }
            default -> player.sendMessage(color("&e/kit &7Menü | &e/kit claim <Name> &7Kit holen | &e/kit access <Name> all|rank <Rang>|player <Spieler>"));
        }
    }

    private void create(Player player, String[] args) {
        if (!manage(player)) return;
        String id = args.length > 1 ? normalize(args[1]) : null;
        if (id == null) { player.sendMessage(color("&cNutzung: /kit create <Name>")); return; }
        data.set("kits." + id + ".contents", null);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) data.set("kits." + id + ".contents." + i, contents[i]);
        data.set("kits." + id + ".armor", null);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) data.set("kits." + id + ".armor." + i, armor[i]);
        data.set("kits." + id + ".offhand", player.getInventory().getItemInOffHand());
        if (!data.contains("kits." + id + ".cooldown-seconds")) data.set("kits." + id + ".cooldown-seconds", 86400);
        if (!data.contains("kits." + id + ".access.type")) data.set("kits." + id + ".access.type", "all");
        save();
        player.sendMessage(color("&aKit &f" + id + " &agespeichert. Cooldown: /kit cooldown " + id + " <Sekunden> (0 = keiner)."));
    }

    private void delete(Player player, String[] args) {
        if (!manage(player)) return;
        String id = args.length > 1 ? normalize(args[1]) : null;
        if (id == null || !data.contains("kits." + id)) { player.sendMessage(color("&cNutzung: /kit delete <Name>")); return; }
        data.set("kits." + id, null); save();
        player.sendMessage(color("&aKit &f" + id + " &agelöscht."));
    }

    private void cooldown(Player player, String[] args) {
        if (!manage(player)) return;
        String id = args.length > 1 ? normalize(args[1]) : null;
        if (id == null || args.length < 3 || !data.contains("kits." + id)) { player.sendMessage(color("&cNutzung: /kit cooldown <Name> <Sekunden>")); return; }
        try {
            long seconds = Long.parseLong(args[2]);
            if (seconds < 0 || seconds > 31536000) throw new NumberFormatException();
            data.set("kits." + id + ".cooldown-seconds", seconds); save();
            player.sendMessage(color("&aCooldown für &f" + id + " &aist jetzt " + seconds + " Sekunden."));
        } catch (NumberFormatException ex) { player.sendMessage(color("&cGib Sekunden zwischen 0 und 31536000 ein.")); }
    }

    private boolean manage(Player player) {
        if (player.hasPermission("servertoolkit.kit.manage")) return true;
        player.sendMessage(color("&cDafür hast du keine Rechte.")); return false;
    }

    private void access(Player player, String[] args) {
        if (!manage(player)) return;
        if (args.length < 3) {
            player.sendMessage(color("&cNutzung: /kit access <Name> all|rank <Rang>|player <Spieler>"));
            return;
        }
        String id = normalize(args[1]);
        if (id == null || !data.contains("kits." + id)) { player.sendMessage(color("&cDieses Kit gibt es nicht.")); return; }
        String mode = args[2].toLowerCase(Locale.ROOT);
        String base = "kits." + id + ".access";
        if (mode.equals("all")) {
            data.set(base + ".type", "all"); data.set(base + ".value", null); save();
            player.sendMessage(color("&aKit &f" + id + " &aist wieder für alle Spieler verfügbar.")); return;
        }
        if (mode.equals("rank") && args.length >= 4) {
            String rank = args[3].toLowerCase(Locale.ROOT);
            data.set(base + ".type", "rank"); data.set(base + ".value", rank); save();
            player.sendMessage(color("&aKit &f" + id + " &akann nur noch von Rang &f" + rank + " &abeansprucht werden.")); return;
        }
        if (mode.equals("player") && args.length >= 4) {
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[3]);
            data.set(base + ".type", "player"); data.set(base + ".value", target.getUniqueId().toString());
            data.set(base + ".player-name", target.getName() == null ? args[3] : target.getName()); save();
            player.sendMessage(color("&aKit &f" + id + " &akann nur noch von &f" + (target.getName() == null ? args[3] : target.getName()) + " &abeansprucht werden.")); return;
        }
        player.sendMessage(color("&cNutzung: /kit access <Name> all|rank <Rang>|player <Spieler>"));
    }

    private boolean canClaim(Player player, String id) {
        String base = "kits." + id + ".access";
        String mode = data.getString(base + ".type", "all");
        String value = data.getString(base + ".value", "");
        if (mode.equalsIgnoreCase("rank")) return plugin.utilityManager().rankId(player).equalsIgnoreCase(value);
        if (mode.equalsIgnoreCase("player")) return player.getUniqueId().toString().equalsIgnoreCase(value);
        return true;
    }

    private String accessDescription(String id) {
        String base = "kits." + id + ".access";
        String mode = data.getString(base + ".type", "all");
        String value = data.getString(base + ".value", "");
        if (mode.equalsIgnoreCase("rank")) return "Nur Rang: " + value;
        if (mode.equalsIgnoreCase("player")) return "Nur Spieler: " + data.getString(base + ".player-name", "festgelegt");
        return "Für alle Spieler";
    }

    private void claim(Player player, String id) {
        if (id == null || !data.contains("kits." + id)) { player.sendMessage(color("&cDieses Kit gibt es nicht. Nutze /kit.")); return; }
        if (!canClaim(player, id)) { player.sendMessage(color("&cDu hast keine Berechtigung, dieses Kit zu beanspruchen. Voraussetzung: " + accessDescription(id))); return; }
        String base = "kits." + id;
        long cooldown = data.getLong(base + ".cooldown-seconds", 86400);
        String claimPath = "claims." + player.getUniqueId() + "." + id;
        long readyAt = data.getLong(claimPath, 0) + cooldown * 1000L;
        if (cooldown > 0 && readyAt > System.currentTimeMillis()) {
            long remaining = (readyAt - System.currentTimeMillis() + 999) / 1000;
            player.sendMessage(color("&cDu kannst dieses Kit wieder in " + formatTime(remaining) + " holen.")); return;
        }
        ItemStack[] contents = readItems(base + ".contents", 36);
        ItemStack[] armor = readItems(base + ".armor", 4);
        ItemStack offhand = data.getItemStack(base + ".offhand");
        if (contents == null) { player.sendMessage(color("&cDieses Kit ist ungültig.")); return; }
        if (!player.getInventory().isEmpty() || !empty(player.getInventory().getArmorContents())
                || !empty(new ItemStack[]{player.getInventory().getItemInOffHand()})) {
            player.sendMessage(color("&cLeere zuerst dein Inventar, Rüstung und Nebenhand, damit keine Items verloren gehen.")); return;
        }
        player.getInventory().setStorageContents(contents);
        if (armor != null) player.getInventory().setArmorContents(armor);
        player.getInventory().setItemInOffHand(offhand);
        if (cooldown > 0) { data.set(claimPath, System.currentTimeMillis()); save(); }
        player.sendMessage(color("&aKit &f" + id + " &aerhalten."));
    }

    private void open(Player player) {
        ConfigurationSection kits = data.getConfigurationSection("kits");
        if (kits == null || kits.getKeys(false).isEmpty()) {
            player.sendMessage(color("&7Es gibt noch keine Kits."));
            return;
        }
        List<String> ids = new ArrayList<>(kits.getKeys(false));
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        List<PopupMenuManager.MenuOption> options = new ArrayList<>();
        for (String id : ids) {
            if (!canClaim(player, id)) continue;
            String path = "kits." + id;
            long seconds = data.getLong(path + ".cooldown-seconds", 86400);
            long readyAt = data.getLong("claims." + player.getUniqueId() + "." + id, 0) + seconds * 1000L;
            String state = readyAt > System.currentTimeMillis()
                    ? "Verfügbar in " + formatTime((readyAt - System.currentTimeMillis() + 999) / 1000)
                    : "Jetzt abholbar";
            options.add(new PopupMenuManager.MenuOption(id,
                    "Zugriff: " + accessDescription(id) + " • Cooldown: " + (seconds == 0 ? "Keiner" : formatTime(seconds)) + " • " + state,
                    () -> claim(player, id)));
        }
        if (options.isEmpty()) { player.sendMessage(color("&cEs gibt keine Kits, die du beanspruchen darfst.")); return; }
        plugin.popupMenuManager().showMenu(player, "AllInOneMC – Kits", "Wähle ein Kit zum Abholen.", options);
    }

    private boolean empty(ItemStack[] items) {
        for (ItemStack item : items) if (item != null && item.getType() != Material.AIR) return false;
        return true;
    }
    private ItemStack[] readItems(String path, int size) {
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) items[i] = data.getItemStack(path + "." + i);
        return items;
    }
    private String normalize(String value) {
        String id = value.toLowerCase(Locale.ROOT);
        return id.matches("[a-z0-9_-]{1,24}") ? id : null;
    }
    private String formatTime(long seconds) {
        if (seconds >= 86400) return (seconds / 86400) + "T " + ((seconds % 86400) / 3600) + "h";
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Kits konnten nicht gespeichert werden: " + ex.getMessage()); }
    }

}
