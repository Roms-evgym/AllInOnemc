package dev.codex.servertoolkit;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Persistent redeemable rewards. Each player may redeem a code only once. */
public final class RedeemCodeManager {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;

    public RedeemCodeManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "codes.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void handleRedeem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können Codes einlösen.");
            return;
        }
        if (args.length != 1) {
            player.sendMessage(color("&cNutzung: /code <Code>"));
            return;
        }
        String code = normalize(args[0]);
        if (code == null || !data.isConfigurationSection("codes." + code)) {
            player.sendMessage(color("&cDieser Code ist ungültig."));
            return;
        }
        String path = "codes." + code;
        List<String> redeemed = new ArrayList<>(data.getStringList(path + ".redeemed"));
        String uuid = player.getUniqueId().toString();
        if (redeemed.contains(uuid)) {
            player.sendMessage(color("&cDu hast diesen Code bereits eingelöst."));
            return;
        }

        String type = data.getString(path + ".type", "");
        if (type.equals("rank")) {
            String rank = data.getString(path + ".value", "");
            if (!plugin.utilityManager().setRankFromCode(player, rank)) {
                player.sendMessage(color("&cDer Rang dieses Codes ist nicht mehr verfügbar. Bitte melde dich beim Team."));
                return;
            }
            player.sendMessage(color("&aCode eingelöst! Dein neuer Rang: &f" + rank));
        } else if (type.equals("money")) {
            BigDecimal amount;
            try { amount = new BigDecimal(data.getString(path + ".value", "0")); }
            catch (NumberFormatException ex) { player.sendMessage(color("&cDieser Code enthält einen ungültigen Betrag.")); return; }
            if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
                player.sendMessage(color("&cDieser Code enthält einen ungültigen Betrag."));
                return;
            }
            plugin.economyManager().deposit(player.getUniqueId(), amount.doubleValue());
            player.sendMessage(color("&aCode eingelöst! Du hast &e" + amount.stripTrailingZeros().toPlainString()
                    + " " + plugin.getConfig().getString("economy.currency-name", "Coins") + " &aerhalten."));
        } else {
            player.sendMessage(color("&cDieser Code ist ungültig konfiguriert."));
            return;
        }

        redeemed.add(uuid);
        data.set(path + ".redeemed", redeemed);
        save();
    }

    public void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servertoolkit.code.create")) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (args.length != 3) {
            sender.sendMessage(color("&c/setcode <Code> rank <Rang>"));
            sender.sendMessage(color("&c/setcode <Code> money <Betrag>"));
            return;
        }
        String code = normalize(args[0]);
        if (code == null) {
            sender.sendMessage(color("&cDer Code darf 3-32 Zeichen enthalten (a-z, 0-9, _ oder -)."));
            return;
        }
        String path = "codes." + code;
        if (data.isConfigurationSection(path)) {
            sender.sendMessage(color("&cDieser Code existiert bereits."));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String value;
        if (type.equals("rank")) {
            value = args[2].toLowerCase(Locale.ROOT);
            if (!plugin.getConfig().isConfigurationSection("ranks." + value)) {
                sender.sendMessage(color("&cDiesen Rang gibt es nicht."));
                return;
            }
        } else if (type.equals("money")) {
            BigDecimal amount;
            try { amount = new BigDecimal(args[2].replace(',', '.')); }
            catch (NumberFormatException ex) { sender.sendMessage(color("&cUngültiger Geldbetrag.")); return; }
            if (amount.scale() > 2 || amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
                sender.sendMessage(color("&cDer Betrag muss größer als 0 und höchstens 1.000.000.000 sein (max. 2 Nachkommastellen)."));
                return;
            }
            value = amount.stripTrailingZeros().toPlainString();
        } else {
            sender.sendMessage(color("&cTyp muss rank oder money sein."));
            return;
        }
        data.set(path + ".type", type);
        data.set(path + ".value", value);
        data.set(path + ".redeemed", new ArrayList<String>());
        save();
        sender.sendMessage(color("&aCode &f" + code + " &aerstellt: &f" + type + " " + value));
    }

    private String normalize(String input) {
        String code = input.toLowerCase(Locale.ROOT);
        return code.matches("[a-z0-9_-]{3,32}") ? code : null;
    }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("codes.yml konnte nicht gespeichert werden: " + ex.getMessage()); }
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
