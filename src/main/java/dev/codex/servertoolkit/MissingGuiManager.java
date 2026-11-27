package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MissingGuiManager implements Listener {
    private final ServerToolkit plugin;
    private final NamespacedKey key;
    private final Map<UUID, String> chatActions = new ConcurrentHashMap<>();

    MissingGuiManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "missing_gui_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void openMain(Player p) {
        Menu menu = menu("§8AllInOne – Funktionen", 54);
        add(menu, 10, Material.GOLD_INGOT, "§6Geld & Überweisen", "money");
        add(menu, 11, Material.TARGET, "§cKopfgeld", "bounty");
        add(menu, 12, Material.CLOCK, "§eSpielzeit", "playtime");
        add(menu, 13, Material.RESPAWN_ANCHOR, "§aHomes", "homes");
        add(menu, 14, Material.ENDER_PEARL, "§bTeleport-Anfrage", "tpa");
        add(menu, 15, Material.LIME_DYE, "§aAnfrage annehmen", "tpaccept");
        add(menu, 16, Material.RED_DYE, "§cAnfrage ablehnen", "tpdeny");
        add(menu, 17, Material.COMPASS, "§aZum Spawn", "spawn");
        if (p.hasPermission("servertoolkit.lag")) add(menu, 18, Material.CLOCK, "§cLag-Simulation", "lag");
        add(menu, 19, Material.PAPER, "§eCode einlösen", "code");
        if (p.hasPermission("servertoolkit.code.create")) add(menu, 20, Material.WRITABLE_BOOK, "§6Codes erstellen", "setcode");
        add(menu, 21, Material.FEATHER, "§bFly umschalten", "fly");
        add(menu, 22, Material.TOTEM_OF_UNDYING, "§dGod-Modus", "god");
        add(menu, 23, Material.PLAYER_HEAD, "§eHinsetzen / Aufstehen", "sit");
        add(menu, 24, Material.COMPASS, "§aZur Lobby", "hub");
        add(menu, 25, Material.ENDER_CHEST, "§5Eigene Enderchest", "echest");
        if (p.hasPermission("servertoolkit.ban.manage")) add(menu, 26, Material.MACE, "§cBanhammer-Menü", "banhammer");
        if (p.hasPermission("servertoolkit.crash")) add(menu, 27, Material.BARRIER, "§cCrash-Simulation", "crash");
        add(menu, 28, Material.CHEST, "§6Shop", "shop");
        add(menu, 29, Material.HOPPER, "§eVerkaufen", "sell");
        add(menu, 30, Material.SHIELD, "§bClans", "clan");
        add(menu, 31, Material.CHEST_MINECART, "§aKits", "kit");
        add(menu, 32, Material.NAME_TAG, "§dRänge", "ranks");
        add(menu, 33, Material.DIAMOND, "§6Auktionshaus", "ah");
        add(menu, 34, Material.WRITABLE_BOOK, "§eAufträge", "orders");
        if (p.hasPermission("servertoolkit.clanduel")) add(menu, 35, Material.IRON_SWORD, "§cClan-Duell starten", "clanduel");
        if (p.hasPermission("servertoolkit.admin")) {
            add(menu, 36, Material.CRAFTING_TABLE, "§6Fakebase platzieren", "fakebase");
            add(menu, 52, Material.IRON_ORE, "§6Fake-Erzader", "fakore");
        }
        add(menu, 37, Material.MACE, "§cMace", "mace");
        add(menu, 38, Material.ENDER_EYE, "§bServer wechseln", "servers");
        add(menu, 39, Material.NOTE_BLOCK, "§dVoice-Chat", "voice");
        add(menu, 40, Material.NAME_TAG, "§eNick & Skin", "nick");
        add(menu, 41, Material.FIREWORK_ROCKET, "§cPrank-Einstellungen", "prank");
        if (p.hasPermission("servertoolkit.ban.manage")) {
            add(menu, 42, Material.ANVIL, "§cBan-Rechte", "banrank");
            add(menu, 43, Material.BOOK, "§cBan-Presets & Banhammer", "banpreset");
        }
        if (p.hasPermission("servertoolkit.staff")) {
            add(menu, 45, Material.CHEST, "§cSpieler-Inventar ansehen", "invsee");
            add(menu, 46, Material.ENDER_CHEST, "§5Spieler-Enderchest ansehen", "ecsee");
        }
        if (p.hasPermission("servertoolkit.ban")) {
            add(menu, 50, Material.IRON_AXE, "§cSpieler bannen", "ban");
            add(menu, 51, Material.PAPER, "§cSpieler bestrafen", "punish");
        }
        if (p.hasPermission("servertoolkit.vanish")) add(menu, 44, Material.POTION, "§8Vanish", "vanish");
        if (p.hasPermission("servertoolkit.eco.admin")) add(menu, 47, Material.EMERALD, "§2Admin-Geldverwaltung", "eco");
        if (p.hasPermission("servertoolkit.admin")) add(menu, 48, Material.BEACON, "§cSpawn setzen", "setspawn");
        add(menu, 53, Material.BARRIER, "§7Schließen", "close");
        p.openInventory(menu.inventory);
    }

    void openCode(Player p) { prompt(p, "code", "Gib deinen Einlösecode ein:"); }

    void openMoney(Player p) {
        Menu menu = menu("§8Geld & Überweisen", 27);
        add(menu, 11, Material.GOLD_INGOT, "§6Guthaben: §e" + plugin.economyManager().formatted(p.getUniqueId()), "balance");
        add(menu, 15, Material.PLAYER_HEAD, "§aGeld senden", "pay-targets");
        add(menu, 22, Material.ARROW, "§7Hauptmenü", "main");
        p.openInventory(menu.inventory);
    }

    void openTargets(Player p, String action) {
        String title = switch (action) {
            case "pay" -> "§8Geld senden – Empfänger";
            case "tpa" -> "§8Teleport – Ziel auswählen";
            case "bounty" -> "§8Kopfgeld – Ziel auswählen";
            case "playtime" -> "§8Spielzeit – Spieler auswählen";
            case "fly" -> "§8Fly – Spieler auswählen";
            case "god" -> "§8God – Spieler auswählen";
            case "eco-give", "eco-take", "eco-set" -> "§8Geldverwaltung – Spieler";
            default -> "§8Spieler auswählen";
        };
        Menu menu = menu(title, 54);
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(p) && List.of("pay", "tpa", "bounty").contains(action)) continue;
            if (slot >= 45) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + target.getName());
                meta.setLore(List.of("§7Klicken zum Auswählen"));
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                        "target:" + action + ":" + target.getUniqueId());
                head.setItemMeta(meta);
            }
            menu.inventory.setItem(slot++, head);
        }
        add(menu, 49, Material.ARROW, "§7Zurück", "main");
        p.openInventory(menu.inventory);
    }

    void openHomes(Player p, boolean delete) {
        Menu menu = menu(delete ? "§8Home löschen" : "§8Deine Homes", 54);
        ConfigurationSection homes = plugin.getConfig().getConfigurationSection("named-homes." + p.getUniqueId());
        int slot = 0;
        if (homes != null) {
            for (String name : homes.getKeys(false)) {
                if (slot >= 45) break;
                add(menu, slot++, delete ? Material.TNT : Material.RED_BED,
                        (delete ? "§cLöschen: §f" : "§aTeleport: §f") + name,
                        (delete ? "delhome:" : "home:") + name);
            }
        }
        if (!delete) {
            add(menu, 47, Material.LIME_BED, "§aHome hier setzen", "sethome");
            add(menu, 48, Material.WRITABLE_BOOK, "§eHome mit Namen setzen", "sethome-named");
            add(menu, 50, Material.TNT, "§cHomes löschen", "homes-delete");
        }
        add(menu, 49, Material.ARROW, "§7Hauptmenü", "main");
        p.openInventory(menu.inventory);
    }

    void openSetCode(Player p) {
        Menu menu = menu("§8Belohnungscode erstellen", 27);
        add(menu, 11, Material.NAME_TAG, "§eRang-Code erstellen", "setcode-rank");
        add(menu, 15, Material.GOLD_INGOT, "§6Geld-Code erstellen", "setcode-money");
        add(menu, 22, Material.ARROW, "§7Hauptmenü", "main");
        p.openInventory(menu.inventory);
    }

    void openEco(Player p) {
        Menu menu = menu("§8Admin-Geldverwaltung", 27);
        add(menu, 10, Material.EMERALD, "§aGeld geben", "eco-give");
        add(menu, 13, Material.REDSTONE, "§cGeld abziehen", "eco-take");
        add(menu, 16, Material.GOLD_BLOCK, "§6Guthaben setzen", "eco-set");
        add(menu, 22, Material.ARROW, "§7Hauptmenü", "main");
        p.openInventory(menu.inventory);
    }

    private Menu menu(String title, int size) {
        Menu holder = new Menu();
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder;
    }

    private void add(Menu menu, int slot, Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        menu.inventory.setItem(slot, item);
    }

    private void prompt(Player p, String action, String instruction) {
        chatActions.put(p.getUniqueId(), action);
        p.closeInventory();
        p.sendMessage("§e" + instruction);
        p.sendMessage("§7Schreibe den Wert in den Chat. Mit §c-abbrechen §7brichst du ab.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String action = chatActions.remove(event.getPlayer().getUniqueId());
        if (action == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("-abbrechen")) {
                player.sendMessage("§cAbgebrochen.");
                return;
            }
            if (action.startsWith("sethome:")) {
                String name = safeName(input);
                if (name == null) { player.sendMessage("§cUngültiger Home-Name."); return; }
                player.performCommand("sethome " + name);
                return;
            }
            if (action.startsWith("code")) {
                String code = safeToken(input);
                if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
                player.performCommand("code " + code);
                return;
            }
            if (action.startsWith("setcode-code:")) {
                String code = safeToken(input);
                if (code == null) { player.sendMessage("§cUngültiger Code."); return; }
                String type = action.substring("setcode-code:".length());
                prompt(player, "setcode-value:" + type + ":" + code,
                        type.equals("rank") ? "Gib jetzt die Rang-ID ein:" : "Gib jetzt den Geldbetrag ein:");
                return;
            }
            if (action.startsWith("setcode-value:")) {
                String[] parts = action.split(":", 3);
                if (parts.length != 3) { player.sendMessage("§cUngültige Eingabe."); return; }
                String value = parts[1].equals("money")
                        ? (input.matches("[0-9]+([.,][0-9]{1,2})?") ? input.replace(',', '.') : null)
                        : safeToken(input);
                if (value == null) { player.sendMessage("§cUngültige Eingabe."); return; }
                player.performCommand("setcode " + parts[2] + " " + parts[1] + " " + value);
                return;
            }
            if (action.equals("fakore")) {
                if (!input.matches("[A-Za-z0-9_]{2,32}( [1-9][0-9]{0,2})?")) {
                    player.sendMessage("§cUngültig. Beispiel: DIAMOND_ORE 8"); return;
                }
                player.performCommand("fakore " + input);
                return;
            }
            if (action.startsWith("ban-reason:")) {
                String reason = input.replaceAll("[^A-Za-z0-9 _.,!?-]", "");
                if (reason.isBlank() || reason.length() > 100) { player.sendMessage("§cUngültiger Grund."); return; }
                player.performCommand("ban " + action.substring("ban-reason:".length()) + " " + reason);
                return;
            }
            if (action.startsWith("punish-preset:")) {
                String preset = safeToken(input);
                if (preset == null) { player.sendMessage("§cUngültige Preset-ID."); return; }
                player.performCommand("punish " + action.substring("punish-preset:".length()) + " " + preset);
                return;
            }
            if (action.startsWith("amount:")) {
                String[] parts = action.split(":", 3);
                if (parts.length != 3 || !input.matches("[0-9]+([.,][0-9]{1,2})?")) {
                    player.sendMessage("§cBitte einen gültigen Betrag eingeben."); return;
                }
                String amount = input.replace(',', '.');
                switch (parts[1]) {
                    case "pay" -> player.performCommand("pay " + parts[2] + " " + amount);
                    case "bounty" -> player.performCommand("bounty " + parts[2] + " " + amount);
                    case "eco-give", "eco-take", "eco-set" -> player.performCommand("eco " + parts[1].substring(4) + " " + parts[2] + " " + amount);
                    default -> { }
                }
            }
        });
    }

    private String safeToken(String input) {
        return input.matches("[A-Za-z0-9_-]{3,32}") ? input : null;
    }

    private String safeName(String input) {
        return input.matches("[A-Za-z0-9_-]{1,16}") ? input : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)
                || !(event.getView().getTopInventory().getHolder() instanceof Menu)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { p.closeInventory(); return; }
        if (action.equals("main")) { openMain(p); return; }
        switch (action) {
            case "money" -> openMoney(p);
            case "bounty" -> openTargets(p, "bounty");
            case "playtime" -> openTargets(p, "playtime");
            case "homes" -> openHomes(p, false);
            case "homes-delete" -> openHomes(p, true);
            case "tpa" -> openTargets(p, "tpa");
            case "balance" -> openMoney(p);
            case "tpaccept", "tpdeny", "sit", "hub", "setspawn", "spawn", "echest", "shop", "sell", "clan", "kit", "ranks",
                    "ah", "orders", "mace", "servers", "voice", "nick", "prank", "banrank", "vanish", "clanduel", "fakebase", "lag", "crash" -> {
                p.performCommand(action);
                p.closeInventory();
            }
            case "banpreset", "banhammer" -> { p.performCommand("banpreset gui"); p.closeInventory(); }
            case "fakore" -> prompt(p, "fakore", "Gib Erztyp und optionale Anzahl ein (z. B. DIAMOND_ORE 8):");
            case "code" -> openCode(p);
            case "setcode" -> openSetCode(p);
            case "setcode-rank" -> prompt(p, "setcode-code:rank", "Gib den neuen Code ein:");
            case "setcode-money" -> prompt(p, "setcode-code:money", "Gib den neuen Code ein:");
            case "eco" -> openEco(p);
            case "eco-give", "eco-take", "eco-set" -> openTargets(p, action);
            case "pay-targets" -> openTargets(p, "pay");
            case "fly", "god", "invsee", "ecsee", "ban", "punish" -> openTargets(p, action);
            case "sethome" -> { p.performCommand("sethome"); p.closeInventory(); }
            case "sethome-named" -> prompt(p, "sethome:named", "Gib einen Home-Namen ein:");
            default -> {
                if (action.startsWith("target:")) {
                    String[] parts = action.split(":", 3);
                    if (parts.length != 3) return;
                    Player target = Bukkit.getPlayer(UUID.fromString(parts[2]));
                    if (target == null) { p.sendMessage("§cSpieler ist nicht mehr online."); return; }
                    switch (parts[1]) {
                        case "pay", "bounty", "eco-give", "eco-take", "eco-set" ->
                                prompt(p, "amount:" + parts[1] + ":" + target.getName(), "Gib den Betrag für " + target.getName() + " ein:");
                        case "tpa" -> { p.performCommand("tpa " + target.getName()); p.closeInventory(); }
                        case "playtime" -> { p.performCommand("playtime " + target.getName()); p.closeInventory(); }
                        case "fly", "god", "invsee", "ecsee" -> { p.performCommand(parts[1] + " " + target.getName()); p.closeInventory(); }
                        case "ban" -> prompt(p, "ban-reason:" + target.getName(), "Gib den Ban-Grund für " + target.getName() + " ein:");
                        case "punish" -> prompt(p, "punish-preset:" + target.getName(), "Gib die Preset-ID für " + target.getName() + " ein:");
                        default -> { }
                    }
                } else if (action.startsWith("home:")) {
                    p.performCommand("home " + action.substring(5)); p.closeInventory();
                } else if (action.startsWith("delhome:")) {
                    p.performCommand("delhome " + action.substring(8)); openHomes(p, true);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { chatActions.remove(event.getPlayer().getUniqueId()); }

    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
