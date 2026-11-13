package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.permissions.PermissionAttachment;

public final class UtilityManager implements CommandExecutor, Listener {
    private final ServerToolkit plugin;
    private final Set<java.util.UUID> godPlayers = new HashSet<>();
    private final File rankFile;
    private final YamlConfiguration rankData;
    private final NamespacedKey menuAction;
    private final java.util.Map<UUID, PermissionAttachment> rankAttachments = new java.util.HashMap<>();
    private final java.util.Map<UUID, PendingRankInput> pendingRankInputs = new java.util.HashMap<>();

    public UtilityManager(ServerToolkit plugin) {
        this.plugin = plugin;
        ensureRankDefaults();
        this.rankFile = new File(plugin.getDataFolder(), "ranks.yml");
        this.rankData = YamlConfiguration.loadConfiguration(rankFile);
        this.menuAction = new NamespacedKey(plugin, "servertoolkit_menu_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("scoreboard.enabled", false)) return;
            for (Player player : Bukkit.getOnlinePlayers()) updateScoreboard(player);
        }, 100L, 100L);
    }

    private void ensureRankDefaults() {
        String[][] defaults = {
                {"owner", "Owner", "&4&lOWNER", "100", "20"}, {"admin", "Admin", "&c&lADMIN", "90", "12"},
                {"moderator", "Moderator", "&5MOD", "70", "8"}, {"helper", "Helper", "&9HELPER", "50", "5"},
                {"vip", "VIP", "&6VIP", "30", "3"}, {"member", "Spieler", "&7SPIELER", "0", "1"}
        };
        for (String[] rank : defaults) {
            String path = "ranks." + rank[0];
            if (!plugin.getConfig().isConfigurationSection(path)) {
                plugin.getConfig().set(path + ".display", rank[1]);
                plugin.getConfig().set(path + ".prefix", rank[2]);
                plugin.getConfig().set(path + ".weight", Integer.parseInt(rank[3]));
            }
            if (!plugin.getConfig().contains(path + ".max-homes")) plugin.getConfig().set(path + ".max-homes", Integer.parseInt(rank[4]));
        }
        if (!plugin.getConfig().contains("scoreboard.enabled")) plugin.getConfig().set("scoreboard.enabled", true);
        if (!plugin.getConfig().contains("scoreboard.title")) plugin.getConfig().set("scoreboard.title", "&6&lAllInOne Survival");
        plugin.getConfig().addDefault("voice-menu.title", "&8Simple Voice Chat");
        plugin.getConfig().addDefault("voice-menu.accent", "&b");
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        switch (name) {
            case "fly" -> toggleFly(player, args);
            case "god" -> toggleGod(player, args);
            case "invsee" -> openInventory(player, args, false);
            case "echest" -> openEnderChest(player, args, false);
            case "ecsee" -> openEnderChest(player, args, true);
            case "trash" -> openTrash(player);
            case "ranks" -> showRanks(player, args);
            case "rank" -> rankCommand(player, args);
            case "voice" -> handleVoice(player, args);
            default -> { return false; }
        }
        return true;
    }

    private void toggleFly(Player sender, String[] args) {
        Player target = targetOrSelf(sender, args);
        if (target == null) return;
        if (!target.equals(sender) && !sender.hasPermission("servertoolkit.staff")) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (!target.hasPermission("servertoolkit.fly") && !target.isOp()) {
            sender.sendMessage(color("&cDu hast keine Fly-Berechtigung."));
            return;
        }
        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);
        if (!enable && target.getGameMode() == GameMode.SURVIVAL) target.setFlying(false);
        target.sendMessage(color(enable ? "&aFliegen aktiviert." : "&eFliegen deaktiviert."));
        if (!target.equals(sender)) sender.sendMessage(color("&7Fly für &f" + target.getName() + (enable ? " &aaktiviert." : " &edeaktiviert.")));
    }

    private void toggleGod(Player sender, String[] args) {
        Player target = targetOrSelf(sender, args);
        if (target == null) return;
        if (!target.equals(sender) && !sender.hasPermission("servertoolkit.staff")) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (!target.hasPermission("servertoolkit.god") && !target.isOp()) {
            sender.sendMessage(color("&cDu hast keine God-Berechtigung."));
            return;
        }
        boolean enabled;
        if (godPlayers.remove(target.getUniqueId())) enabled = false;
        else { godPlayers.add(target.getUniqueId()); enabled = true; }
        target.sendMessage(color(enabled ? "&aGod-Modus aktiviert." : "&eGod-Modus deaktiviert."));
        if (!target.equals(sender)) sender.sendMessage(color("&7God für &f" + target.getName() + (enabled ? " &aaktiviert." : " &edeaktiviert.")));
    }

    private void openInventory(Player sender, String[] args, boolean unused) {
        if (!sender.hasPermission("servertoolkit.staff") && !sender.isOp()) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&cNutzung: /invsee <Spieler>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(color("&cDer Spieler muss online sein."));
            return;
        }
        sender.openInventory(target.getInventory());
    }

    private void openEnderChest(Player sender, String[] args, boolean other) {
        if (other && !sender.hasPermission("servertoolkit.staff") && !sender.isOp()) {
            sender.sendMessage(color("&cDafür hast du keine Rechte."));
            return;
        }
        if (!other) {
            sender.openInventory(sender.getEnderChest());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&cNutzung: /ecsee <Spieler>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(color("&cDer Spieler muss online sein."));
            return;
        }
        sender.openInventory(target.getEnderChest());
    }

    private void openTrash(Player player) {
        Inventory inventory = Bukkit.createInventory(new TrashHolder(), 54, GuiStyle.title("Papierkorb"));
        player.openInventory(inventory);
        player.sendMessage(color("&7Alles im Papierkorb wird beim Schließen gelöscht."));
    }

    private void showRanks(Player viewer, String[] args) {
        if (args.length == 0) { openRankMenu(viewer); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String id = rankData.getString("players." + target.getUniqueId(), "member");
        viewer.sendMessage(color("&6Rang von &e" + target.getName() + "&6: " + rankPrefix(id) + " &7(" + id + ")"));
    }

    private void rankCommand(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) { openRankMenu(player); return; }
        if (!player.hasPermission("servertoolkit.rank.manage") && !player.isOp()) {
            player.sendMessage(color("&cDafür hast du keine Rechte.")); return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                ConfigurationSectionLike section = new ConfigurationSectionLike(plugin.getConfig().getConfigurationSection("ranks"));
                player.sendMessage(color("&6Ränge: &f" + String.join(", ", section.keys())));
                player.sendMessage(color("&7Erstellen: &f/rank create <id> <prefix>"));
            }
            case "set" -> {
                if (args.length < 3) { player.sendMessage(color("&c/rank set <Spieler> <rang>")); return; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().isConfigurationSection("ranks." + id)) { player.sendMessage(color("&cUnbekannter Rang. /rank list")); return; }
                rankData.set("players." + target.getUniqueId(), id); saveRanks(); refreshPlayer(target.getPlayer());
                if (target.isOnline()) plugin.networkSyncManager().publish(target.getPlayer());
                player.sendMessage(color("&aRang " + rankPrefix(id) + " &afür &f" + args[1] + " &agesetzt."));
            }
            case "remove" -> {
                if (args.length < 2) { player.sendMessage(color("&c/rank remove <Spieler>")); return; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                rankData.set("players." + target.getUniqueId(), null); saveRanks(); refreshPlayer(target.getPlayer());
                if (target.isOnline()) plugin.networkSyncManager().publish(target.getPlayer());
                player.sendMessage(color("&aEigener Rang von &f" + args[1] + " &azurückgesetzt."));
            }
            case "create" -> {
                if (args.length < 3) { player.sendMessage(color("&c/rank create <id> <prefix>")); return; }
                String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                if (id.isBlank()) { player.sendMessage(color("&cDie Rang-ID darf nur a-z, 0-9, _ und - enthalten.")); return; }
                plugin.getConfig().set("ranks." + id + ".prefix", String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                plugin.getConfig().set("ranks." + id + ".display", id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1));
                plugin.saveConfig();
                player.sendMessage(color("&aRang " + rankPrefix(id) + " &aerstellt. Vergabe: &f/rank set <Spieler> " + id));
            }
            case "perm" -> {
                if (args.length < 4) { player.sendMessage(color("&c/rank perm <rang> add|remove <permission>")); return; }
                String id = args[1].toLowerCase(Locale.ROOT), action = args[2].toLowerCase(Locale.ROOT), node = args[3].toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().isConfigurationSection("ranks." + id)) { player.sendMessage(color("&cUnbekannter Rang.")); return; }
                if (!node.matches("[a-z0-9_.:-]+") || node.contains("*")) { player.sendMessage(color("&cBitte ein konkretes Recht ohne Wildcards angeben.")); return; }
                List<String> permissions = plugin.getConfig().getStringList("ranks." + id + ".permissions");
                if (action.equals("add") && !permissions.contains(node)) permissions.add(node);
                else if (action.equals("remove")) permissions.remove(node);
                else if (!action.equals("add")) { player.sendMessage(color("&cAktion muss add oder remove sein.")); return; }
                plugin.getConfig().set("ranks." + id + ".permissions", permissions); plugin.saveConfig();
                for (Player online : Bukkit.getOnlinePlayers()) if (getRankId(online).equals(id)) refreshPlayer(online);
                player.sendMessage(color("&aRecht " + node + " für Rang " + id + " aktualisiert."));
            }
            case "color" -> {
                if (args.length < 3) { player.sendMessage(color("&c/rank color <rang> <Farbcode>")); return; }
                String id = args[1].toLowerCase(Locale.ROOT), code = args[2].toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().isConfigurationSection("ranks." + id)) { player.sendMessage(color("&cUnbekannter Rang.")); return; }
                if (!code.matches("&[0-9a-fk-or]")) { player.sendMessage(color("&cFarbcode z. B. &6, &b oder &d.")); return; }
                String display = plugin.getConfig().getString("ranks." + id + ".display", id).toUpperCase(Locale.ROOT);
                plugin.getConfig().set("ranks." + id + ".prefix", code + display); plugin.saveConfig();
                for (Player online : Bukkit.getOnlinePlayers()) if (getRankId(online).equals(id)) refreshPlayer(online);
                player.sendMessage(color("&aFarbe für Rang " + id + " aktualisiert."));
            }
            case "prefix" -> {
                if (args.length < 3) { player.sendMessage(color("&c/rank prefix <rang> <text mit &-Farbcodes>")); return; }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().isConfigurationSection("ranks." + id)) { player.sendMessage(color("&cUnbekannter Rang.")); return; }
                String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                plugin.getConfig().set("ranks." + id + ".prefix", prefix); plugin.saveConfig();
                for (Player online : Bukkit.getOnlinePlayers()) if (getRankId(online).equals(id)) refreshPlayer(online);
                player.sendMessage(color("&aPrefix für Rang " + id + " aktualisiert."));
            }
            case "homes" -> {
                if (args.length < 3) { player.sendMessage(color("&c/rank homes <rang> <anzahl>")); return; }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!plugin.getConfig().isConfigurationSection("ranks." + id)) { player.sendMessage(color("&cUnbekannter Rang.")); return; }
                int amount; try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { player.sendMessage(color("&cAnzahl muss eine Zahl sein.")); return; }
                if (amount < 1 || amount > 100) { player.sendMessage(color("&cErlaubt sind 1 bis 100 Homes.")); return; }
                plugin.getConfig().set("ranks." + id + ".max-homes", amount); plugin.saveConfig();
                player.sendMessage(color("&aRang " + id + " darf jetzt " + amount + " Homes setzen."));
            }
            default -> player.sendMessage(color("&e/rank gui|list|set|remove|create|perm|color|prefix|homes"));
        }
    }

    private void handleVoice(Player player, String[] args) {
        if (args.length == 0) { openVoiceMenu(player); return; }
        if (!player.hasPermission("servertoolkit.voice.manage") && !player.isOp()) {
            player.sendMessage(color("&cDafür hast du keine Rechte.")); return;
        }
        if (!args[0].equalsIgnoreCase("color") || args.length < 2 || !args[1].matches("&[0-9a-fk-or]")) {
            player.sendMessage(color("&e/voice color <Farbcode> &7(z. B. &b/voice color &d)")); return;
        }
        plugin.getConfig().set("voice-menu.accent", args[1]);
        plugin.getConfig().set("voice-menu.title", args[1] + "Simple Voice Chat");
        plugin.saveConfig();
        player.sendMessage(color("&aVoice-Menüfarbe wurde geändert."));
    }

    private void openRankMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("ranks"), 27, GuiStyle.title("Ränge"));
        if (!player.hasPermission("servertoolkit.rank.manage") && !player.isOp()) {
            player.sendMessage(color("&cDafür hast du keine Rechte.")); return;
        }
        var section = plugin.getConfig().getConfigurationSection("ranks");
        if (section == null) { player.sendMessage(color("&cEs sind keine Ränge eingerichtet.")); return; }
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt(id -> plugin.getConfig().getInt("ranks." + id + ".weight", 0)));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        for (int i = 0; i < Math.min(ids.size(), slots.length); i++) {
            String id = ids.get(i);
            ItemStack item = menuItem(Material.NAME_TAG,
                    rankPrefix(id) + " &f" + plugin.getConfig().getString("ranks." + id + ".display", id),
                    "&7Prefix: " + plugin.getConfig().getString("ranks." + id + ".prefix", ""), "&eKlicken zum Bearbeiten");
            setMenuAction(item, "openrank:" + id);
            inv.setItem(slots[i], item);
        }
        ItemStack create = menuItem(Material.EMERALD, "&aNeuen Rang erstellen", "&7Klicken und Rang-ID im Chat eingeben.");
        setMenuAction(create, "newrank"); inv.setItem(22, create);
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void openRankEditor(Player player, String id) {
        if (!canManageRanks(player) || !plugin.getConfig().isConfigurationSection("ranks." + id)) return;
        Inventory inv = Bukkit.createInventory(new MenuHolder("rank-edit", id), 54, GuiStyle.title("Rang: " + id));
        inv.setItem(10, actionItem(Material.NAME_TAG, "&ePrefix und Farbe ändern", "&7Im Chat eingeben.", "prefix:" + id));
        inv.setItem(12, actionItem(Material.WRITABLE_BOOK, "&eAnzeigename ändern", "&7Im Chat eingeben.", "display:" + id));
        inv.setItem(14, actionItem(Material.PLAYER_HEAD, "&eSpielern diesen Rang geben", "&7Online-Spieler auswählen.", "assign:" + id));
        int homes = plugin.getConfig().getInt("ranks." + id + ".max-homes", 1);
        inv.setItem(16, actionItem(Material.RED_DYE, "&cHomes −", "&7Aktuell: " + homes, "homes-:" + id));
        inv.setItem(17, menuItem(Material.PAPER, "&6Max Homes: &f" + homes, "&7Mit den Knöpfen links und rechts ändern."));
        inv.setItem(18, actionItem(Material.LIME_DYE, "&aHomes +", "&7Aktuell: " + homes, "homes+:" + id));
        int weight = plugin.getConfig().getInt("ranks." + id + ".weight", 0);
        inv.setItem(20, actionItem(Material.RED_DYE, "&cRanggewicht −", "&7Aktuell: " + weight, "weight-:" + id));
        inv.setItem(21, menuItem(Material.PAPER, "&6Gewicht: &f" + weight, "&7Höhere Gewichte erscheinen weiter oben."));
        inv.setItem(22, actionItem(Material.LIME_DYE, "&aRanggewicht +", "&7Aktuell: " + weight, "weight+:" + id));

        List<String> permissions = rankPermissionChoices(id);
        int slot = 28;
        for (String permission : permissions) {
            if (slot > 34) slot = 37;
            if (slot > 43) break;
            boolean enabled = plugin.getConfig().getStringList("ranks." + id + ".permissions").contains(permission);
            ItemStack item = menuItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                    (enabled ? "&a✔ " : "&7✖ ") + permission, "&eKlicken zum " + (enabled ? "Entfernen" : "Hinzufügen"));
            setMenuAction(item, "perm:" + id + ":" + permission); inv.setItem(slot++, item);
        }
        inv.setItem(49, actionItem(Material.ARROW, "&7Zur Rangliste", "", "back"));
        GuiStyle.frame(inv); player.openInventory(inv);
    }

    private void openRankAssignees(Player player, String rankId) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("rank-assign", rankId), 54, GuiStyle.title("Rang vergeben: " + rankId));
        int slot = 10;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot += 2;
            if (slot >= 44) break;
            ItemStack item = menuItem(Material.PLAYER_HEAD, "&f" + target.getName(), "&7Aktuell: " + getRankId(target), "&eKlicken zum Zuweisen");
            setMenuAction(item, "assignplayer:" + rankId + ":" + target.getUniqueId());
            inv.setItem(slot++, item);
        }
        inv.setItem(49, actionItem(Material.ARROW, "&7Zurück", "", "edit:" + rankId));
        GuiStyle.frame(inv); player.openInventory(inv);
    }

    private List<String> rankPermissionChoices(String id) {
        List<String> result = new ArrayList<>(List.of("servertoolkit.fly", "servertoolkit.god", "servertoolkit.staff",
                "servertoolkit.rtp", "servertoolkit.vanish", "servertoolkit.vanish.see", "servertoolkit.kit.manage",
                "servertoolkit.ban", "servertoolkit.ban.manage", "servertoolkit.rank.manage"));
        for (String permission : plugin.getConfig().getStringList("ranks." + id + ".permissions"))
            if (!result.contains(permission)) result.add(permission);
        return result;
    }

    private ItemStack actionItem(Material material, String title, String lore, String action) {
        ItemStack item = menuItem(material, title, lore); setMenuAction(item, action); return item;
    }

    private void setMenuAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(menuAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private boolean canManageRanks(Player player) {
        return player.isOp() || player.hasPermission("servertoolkit.rank.manage");
    }

    private void promptRankInput(Player player, String type, String rankId) {
        pendingRankInputs.put(player.getUniqueId(), new PendingRankInput(type, rankId));
        player.closeInventory();
        player.sendMessage(color(type.equals("prefix")
                ? "&eGib den neuen Prefix mit optionalen &-Farbcodes im Chat ein."
                : type.equals("display") ? "&eGib den neuen Anzeigenamen im Chat ein." : "&eGib eine Rang-ID ein (a-z, 0-9, _ oder -)."));
        player.sendMessage(color("&7Abbrechen mit: &fcancel"));
    }

    @EventHandler
    public void onRankInput(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingRankInput pending = pendingRankInputs.get(uuid);
        if (pending == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (text.equalsIgnoreCase("cancel")) {
                pendingRankInputs.remove(uuid); player.sendMessage(color("&7Rangbearbeitung abgebrochen.")); return;
            }
            if (!canManageRanks(player)) { pendingRankInputs.remove(uuid); player.sendMessage(color("&cDafür hast du keine Rechte.")); return; }
            if (pending.type().equals("newrank")) {
                String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                pendingRankInputs.remove(uuid);
                if (id.isBlank() || plugin.getConfig().isConfigurationSection("ranks." + id)) {
                    player.sendMessage(color("&cUngültige oder bereits vorhandene Rang-ID.")); openRankMenu(player); return;
                }
                plugin.getConfig().set("ranks." + id + ".display", id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1));
                plugin.getConfig().set("ranks." + id + ".prefix", "&7" + id.toUpperCase(Locale.ROOT));
                plugin.getConfig().set("ranks." + id + ".weight", 0); plugin.getConfig().set("ranks." + id + ".max-homes", 1);
                plugin.saveConfig(); player.sendMessage(color("&aRang &f" + id + " &aerstellt.")); openRankEditor(player, id); return;
            }
            if (!plugin.getConfig().isConfigurationSection("ranks." + pending.rankId())) {
                pendingRankInputs.remove(uuid); player.sendMessage(color("&cDieser Rang existiert nicht mehr.")); return;
            }
            if (text.isBlank() || text.length() > 48 || text.chars().anyMatch(Character::isISOControl)) {
                player.sendMessage(color("&cText muss 1 bis 48 Zeichen lang sein.")); return;
            }
            String path = "ranks." + pending.rankId() + (pending.type().equals("prefix") ? ".prefix" : ".display");
            plugin.getConfig().set(path, text); plugin.saveConfig(); pendingRankInputs.remove(uuid);
            for (Player online : Bukkit.getOnlinePlayers()) if (getRankId(online).equals(pending.rankId())) refreshPlayer(online);
            player.sendMessage(color("&aRang wurde gespeichert.")); openRankEditor(player, pending.rankId());
        });
    }

    private void openVoiceMenu(Player player) {
        String accent = plugin.getConfig().getString("voice-menu.accent", "&b");
        Inventory inv = Bukkit.createInventory(new MenuHolder("voice"), 27, GuiStyle.title("Voice Chat"));
        inv.setItem(11, menuItem(Material.NOTE_BLOCK, accent + "Voice-GUI öffnen", "&7Drücke im Spiel &fV&7."));
        inv.setItem(13, menuItem(Material.ENDER_EYE, accent + "Mikrofon & Einstellungen", "&7In der Voice-GUI auf das Zahnrad klicken."));
        inv.setItem(15, menuItem(Material.PLAYER_HEAD, accent + "Gruppe erstellen", "&7Voice-GUI mit &fV&7 öffnen und Gruppe wählen."));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private ItemStack menuItem(Material material, String title, String lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(title)); meta.setLore(List.of(color(lore))); item.setItemMeta(meta); return item;
    }

    private ItemStack menuItem(Material material, String title, String lore, String extraLore) {
        ItemStack item = menuItem(material, title, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(color(lore), color(extraLore)));
        item.setItemMeta(meta);
        return item;
    }

    private String getRankId(OfflinePlayer player) { return rankData.getString("players." + player.getUniqueId(), "member"); }
    public String rankId(Player player) { return getRankId(player); }
    public String rankId(OfflinePlayer player) { return getRankId(player); }

    void applySyncedRank(UUID uuid, String rankId) {
        if (!plugin.getConfig().isConfigurationSection("ranks." + rankId)) {
            plugin.getLogger().warning("Proxy sent unknown rank '" + rankId + "'; keeping it as the player's rank id.");
        }
        rankData.set("players." + uuid, rankId);
        saveRanks();
        refreshPlayer(Bukkit.getPlayer(uuid));
    }
    public int maxHomes(Player player) { return Math.max(1, plugin.getConfig().getInt("ranks." + getRankId(player) + ".max-homes", 1)); }
    private String rankPrefix(String id) { return color(plugin.getConfig().getString("ranks." + id + ".prefix", "&7")); }
    private void saveRanks() { try { rankData.save(rankFile); } catch (IOException e) { plugin.getLogger().severe("Konnte ranks.yml nicht speichern: " + e.getMessage()); } }

    private void refreshPlayer(Player player) {
        if (player == null) return;
        String id = getRankId(player);
        String prefix = rankPrefix(id);
        player.setPlayerListName(prefix + player.getName());
        player.setDisplayName(prefix + player.getName());
        PermissionAttachment previous = rankAttachments.remove(player.getUniqueId());
        if (previous != null) previous.remove();
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String permission : plugin.getConfig().getStringList("ranks." + id + ".permissions")) attachment.setPermission(permission, true);
        rankAttachments.put(player.getUniqueId(), attachment);
        updateScoreboard(player);
    }

    void updateScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", false)) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager(); if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("stinfo", "dummy", color(plugin.getConfig().getString("scoreboard.title", "&6&lAllInOne Survival")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String prefix = rankPrefix(getRankId(player));
        String[] lines = {"&7", "&fRang: " + prefix, "&fWelt: &a" + player.getWorld().getName(), "&1", "&6Geld: &f" + plugin.economyManager().formatted(player.getUniqueId()), "&8"};
        int score = lines.length; for (String line : lines) objective.getScore(color(line)).setScore(score--);
        player.setScoreboard(board);
    }

    @EventHandler public void onRankJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(plugin, () -> refreshPlayer(event.getPlayer())); }
    @EventHandler public void onRankQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = rankAttachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) attachment.remove();
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(menuAction, PersistentDataType.STRING);
        if (action == null) {
            if (holder.type.equals("ranks") && event.getRawSlot() != 22 && item.getType() == Material.NAME_TAG) {
                // Compatibility with rank icons from old menus that do not carry action metadata.
                String rank = plugin.getConfig().getConfigurationSection("ranks").getKeys(false).stream()
                        .filter(id -> item.getItemMeta().getDisplayName().contains(ChatColor.stripColor(rankPrefix(id))))
                        .findFirst().orElse(null);
                if (rank != null) openRankEditor(player, rank);
            }
            return;
        }
        if (action.equals("newrank")) { promptRankInput(player, "newrank", ""); return; }
        if (action.equals("back")) { openRankMenu(player); return; }
        if (action.startsWith("openrank:")) { openRankEditor(player, action.substring("openrank:".length())); return; }
        if (action.startsWith("edit:")) { openRankEditor(player, action.substring("edit:".length())); return; }
        if (action.startsWith("prefix:")) { promptRankInput(player, "prefix", action.substring("prefix:".length())); return; }
        if (action.startsWith("display:")) { promptRankInput(player, "display", action.substring("display:".length())); return; }
        if (action.startsWith("assign:")) { openRankAssignees(player, action.substring("assign:".length())); return; }
        if (action.startsWith("assignplayer:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) return;
            Player target = Bukkit.getPlayer(UUID.fromString(parts[2]));
            if (target == null) { player.sendMessage(color("&cDer Spieler ist nicht mehr online.")); openRankAssignees(player, parts[1]); return; }
            rankData.set("players." + target.getUniqueId(), parts[1]); saveRanks(); refreshPlayer(target);
            plugin.networkSyncManager().publish(target);
            player.sendMessage(color("&aRang &f" + parts[1] + " &aan &f" + target.getName() + " &avergeben."));
            openRankEditor(player, parts[1]); return;
        }
        if (action.startsWith("homes+") || action.startsWith("homes-")) {
            String id = action.substring(7);
            String path = "ranks." + id + ".max-homes";
            int value = plugin.getConfig().getInt(path, 1) + (action.charAt(5) == '+' ? 1 : -1);
            plugin.getConfig().set(path, Math.max(1, Math.min(100, value))); plugin.saveConfig(); openRankEditor(player, id); return;
        }
        if (action.startsWith("weight+") || action.startsWith("weight-")) {
            String id = action.substring(8);
            String path = "ranks." + id + ".weight";
            int value = plugin.getConfig().getInt(path, 0) + (action.charAt(6) == '+' ? 1 : -1);
            plugin.getConfig().set(path, Math.max(-1000, Math.min(1000, value))); plugin.saveConfig(); openRankEditor(player, id); return;
        }
        if (action.startsWith("perm:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) return;
            String path = "ranks." + parts[1] + ".permissions";
            List<String> permissions = plugin.getConfig().getStringList(path);
            if (permissions.remove(parts[2])) { /* toggled off */ }
            else permissions.add(parts[2]);
            plugin.getConfig().set(path, permissions); plugin.saveConfig();
            for (Player online : Bukkit.getOnlinePlayers()) if (getRankId(online).equals(parts[1])) refreshPlayer(online);
            openRankEditor(player, parts[1]);
        }
    }

    private Player targetOrSelf(Player sender, String[] args) {
        if (args.length == 0) return sender;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) sender.sendMessage(color("&cDer Spieler muss online sein."));
        return target;
    }

    @EventHandler
    public void onGodDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && godPlayers.contains(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onTrashClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TrashHolder) event.getInventory().clear();
    }

    private String color(String message) { return ChatColor.translateAlternateColorCodes('&', message); }

    private static final class TrashHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String type; private final String rankId;
        private MenuHolder(String type) { this(type, ""); }
        private MenuHolder(String type, String rankId) { this.type = type; this.rankId = rankId; }
        @Override public Inventory getInventory() { return null; }
    }

    private record PendingRankInput(String type, String rankId) {}

    private record ConfigurationSectionLike(org.bukkit.configuration.ConfigurationSection section) {
        List<String> keys() { return section == null ? List.of() : new ArrayList<>(section.getKeys(false)); }
    }
}
