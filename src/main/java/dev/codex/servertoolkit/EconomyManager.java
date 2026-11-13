package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/** Persistent single-currency economy owned by ServerToolkit. */
public final class EconomyManager {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;

    public EconomyManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getConfig().addDefault("economy.starting-balance", 0.0);
        plugin.getConfig().addDefault("economy.currency-name", "Coins");
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    public BigDecimal balance(UUID uuid) {
        String path = "balances." + uuid;
        if (!data.contains(path)) {
            data.set(path, amount(BigDecimal.valueOf(plugin.getConfig().getDouble("economy.starting-balance", 0.0))).toPlainString());
            save();
        }
        return amount(new BigDecimal(data.getString(path, "0")));
    }

    public String formatted(UUID uuid) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(balance(uuid)) + " " + plugin.getConfig().getString("economy.currency-name", "Coins");
    }

    public boolean has(UUID uuid, double amount) {
        BigDecimal requested = amount(BigDecimal.valueOf(amount));
        return requested.signum() >= 0 && balance(uuid).compareTo(requested) >= 0;
    }

    public boolean withdraw(UUID uuid, double amount) {
        BigDecimal requested = amount(BigDecimal.valueOf(amount));
        BigDecimal current = balance(uuid);
        if (requested.signum() < 0 || current.compareTo(requested) < 0) return false;
        setBalance(uuid, current.subtract(requested));
        return true;
    }

    public boolean deposit(UUID uuid, double amount) {
        BigDecimal requested = amount(BigDecimal.valueOf(amount));
        if (requested.signum() < 0) return false;
        setBalance(uuid, balance(uuid).add(requested));
        return true;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Nutzung: /money <Spieler>"); return; }
            sender.sendMessage(color("&6Dein Guthaben: &e" + formatted(player.getUniqueId())));
            return;
        }
        if (args[0].equalsIgnoreCase("pay")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Nur Spieler können Geld senden."); return; }
            if (!player.hasPermission("servertoolkit.money.pay")) { deny(sender); return; }
            if (args.length != 3) { sender.sendMessage(color("&c/money pay <Spieler> <Betrag>")); return; }
            Player target = Bukkit.getPlayerExact(args[1]);
            BigDecimal value = parse(args[2]);
            if (target == null || value == null || value.signum() <= 0 || target.equals(player)) {
                sender.sendMessage(color("&cSpieler oder Betrag ungültig. Betrag muss größer als 0 sein.")); return;
            }
            BigDecimal current = balance(player.getUniqueId());
            if (current.compareTo(value) < 0) { sender.sendMessage(color("&cDafür reicht dein Guthaben nicht.")); return; }
            setBalance(player.getUniqueId(), current.subtract(value));
            setBalance(target.getUniqueId(), balance(target.getUniqueId()).add(value));
            sender.sendMessage(color("&aDu hast &f" + formattedAmount(value) + " &aan &f" + target.getName() + " &agesendet."));
            target.sendMessage(color("&aDu hast &f" + formattedAmount(value) + " &avon &f" + player.getName() + " &aerhalten."));
            return;
        }
        if (!sender.hasPermission("servertoolkit.eco.admin")) { deny(sender); return; }
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            sender.sendMessage(color("&cDie Geldverwaltung nutzt: /eco <give|take|set> <Spieler> <Betrag>")); return;
        }
        if (args.length < 3 || !(args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            sender.sendMessage(color("&c/eco <give|take|set> <Spieler> <Betrag>")); return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal value = parse(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline() || value == null || value.signum() < 0) {
            sender.sendMessage(color("&cUnbekannter Spieler oder ungültiger Betrag.")); return;
        }
        BigDecimal current = balance(target.getUniqueId());
        BigDecimal updated = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> current.add(value);
            case "take" -> current.subtract(value);
            default -> value;
        };
        if (updated.signum() < 0) { sender.sendMessage(color("&cDas Guthaben kann nicht negativ werden.")); return; }
        setBalance(target.getUniqueId(), updated);
        sender.sendMessage(color("&aGuthaben von &f" + target.getName() + " &aist jetzt &e" + formatted(target.getUniqueId()) + "&a."));
    }

    private BigDecimal parse(String text) {
        try { return amount(new BigDecimal(text.replace(',', '.'))); }
        catch (NumberFormatException ex) { return null; }
    }

    private BigDecimal amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String formattedAmount(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(2);
        return format.format(amount(value)) + " " + plugin.getConfig().getString("economy.currency-name", "Coins");
    }
    private void setBalance(UUID uuid, BigDecimal value) {
        data.set("balances." + uuid, amount(value).toPlainString()); save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.utilityManager() != null) {
            plugin.utilityManager().updateScoreboard(player);
            if (plugin.networkSyncManager() != null) plugin.networkSyncManager().publish(player);
        }
    }

    void applySyncedBalance(UUID uuid, BigDecimal value) {
        if (value.signum() < 0) return;
        data.set("balances." + uuid, amount(value).toPlainString());
        save();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && plugin.utilityManager() != null) plugin.utilityManager().updateScoreboard(player);
    }
    private void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().severe("economy.yml konnte nicht gespeichert werden: " + e.getMessage()); }
    }
    private void deny(CommandSender sender) { sender.sendMessage(color("&cDafür hast du keine Rechte.")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
