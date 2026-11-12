package dev.codex.servertoolkit;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class ModerationManager implements Listener {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey hammerPreset;
    private final NamespacedKey vanishAction;
    private final NamespacedKey presetAction;
    private final NamespacedKey banRankAction;
    private final Map<UUID, PendingPresetInput> pendingPresetInputs = new HashMap<>();

    public ModerationManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        this.hammerPreset = new NamespacedKey(plugin, "banhammer_preset");
        this.vanishAction = new NamespacedKey(plugin, "vanish_gui_action");
        this.presetAction = new NamespacedKey(plugin, "banpreset_gui_action");
        this.banRankAction = new NamespacedKey(plugin, "banrank_gui_action");
        if (!data.contains("presets")) {
            data.set("presets.spam.duration", "1d");
            data.set("presets.spam.reason", "Spam");
            data.set("presets.cheating.duration", "30d");
            data.set("presets.cheating.reason", "Cheating");
            data.set("presets.permanent.duration", "perm");
            data.set("presets.permanent.reason", "Schwerer Regelverstoß");
        }
        save();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) Bukkit.getScheduler().runTask(plugin, () -> syncViewer(player));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player target : Bukkit.getOnlinePlayers())
                if (data.getBoolean("vanished." + target.getUniqueId(), false)) syncTarget(target);
        }, 20L, 20L);
    }

    public void handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servertoolkit.ban")) { deny(sender); return; }
        if (args.length < 2) { sender.sendMessage(color("&cNutzung: /ban <Spieler> <Preset>")); return; }
        String id = normalize(args[1]);
        if (id == null || !data.isConfigurationSection("presets." + id)) { sender.sendMessage(color("&cDieses Ban-Preset gibt es nicht.")); return; }
        if (sender instanceof Player player) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!canBanRank(player, target)) {
                sender.sendMessage(color("&cDein Rang darf den Rang &f" +
                        plugin.getConfig().getString("ranks." + plugin.utilityManager().rankId(target) + ".display", plugin.utilityManager().rankId(target)) +
                        " &cnicht bannen."));
                return;
            }
        }
        ban(sender, args[0], id);
    }

    public void handleBanRanks(CommandSender sender) {
        if (!sender.hasPermission("servertoolkit.ban.manage")) { deny(sender); return; }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Die Rang-GUI ist nur im Spiel verfügbar.");
            return;
        }
        openBanRankActors(player);
    }

    private void openBanRankActors(Player player) {
        List<String> ids = ranks();
        Inventory inv = Bukkit.createInventory(new BanRankHolder(), 54, GuiStyle.title("Ban-Rechte: Ausführender Rang"));
        int[] slots = vanishSlots();
        for (int i = 0; i < Math.min(slots.length, ids.size()); i++) {
            String id = ids.get(i);
            inv.setItem(slots[i], banRankItem(Material.PLAYER_HEAD, "&e" + rankDisplay(id),
                    "&7Auswählen, welche Ränge &f" + rankDisplay(id) + " &7bannen darf.", "actor:" + id));
        }
        inv.setItem(49, banRankItem(Material.BARRIER, "&cSchließen", "", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void openBanRankTargets(Player player, String actor) {
        if (!plugin.getConfig().isConfigurationSection("ranks." + actor)) { openBanRankActors(player); return; }
        List<String> ids = ranks();
        Inventory inv = Bukkit.createInventory(new BanRankHolder(), 54, GuiStyle.title("Ban-Rechte: " + rankDisplay(actor)));
        int[] slots = vanishSlots();
        for (int i = 0; i < Math.min(slots.length, ids.size()); i++) {
            String target = ids.get(i);
            boolean allowed = data.getBoolean("ban-ranks." + actor + "." + target, defaultCanBan(actor, target));
            inv.setItem(slots[i], banRankItem(allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                    (allowed ? "&aDarf bannen: " : "&cDarf NICHT bannen: ") + rankDisplay(target),
                    "&7Klicken zum Umschalten", "toggle:" + actor + ":" + target));
        }
        inv.setItem(45, banRankItem(Material.ARROW, "&7« Ränge", "", "back"));
        inv.setItem(49, banRankItem(Material.BARRIER, "&cSchließen", "", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private String rankDisplay(String id) {
        return plugin.getConfig().getString("ranks." + id + ".display", id);
    }

    private boolean defaultCanBan(String actor, String target) {
        int actorWeight = plugin.getConfig().getInt("ranks." + actor + ".weight", 0);
        int targetWeight = plugin.getConfig().getInt("ranks." + target + ".weight", 0);
        return actorWeight > targetWeight;
    }

    private ItemStack banRankItem(Material material, String title, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(title));
        if (!lore.isEmpty()) meta.setLore(List.of(color(lore)));
        meta.getPersistentDataContainer().set(banRankAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onBanRankGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BanRankHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!player.hasPermission("servertoolkit.ban.manage")) { player.closeInventory(); deny(player); return; }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(banRankAction, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("back")) { openBanRankActors(player); return; }
        if (action.startsWith("actor:")) { openBanRankTargets(player, action.substring(6)); return; }
        if (action.startsWith("toggle:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) return;
            String path = "ban-ranks." + parts[1] + "." + parts[2];
            boolean current = data.getBoolean(path, defaultCanBan(parts[1], parts[2]));
            data.set(path, !current);
            save();
            openBanRankTargets(player, parts[1]);
        }
    }

    private static final class BanRankHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private boolean canBanRank(Player source, OfflinePlayer target) {
        if (source.isOp()) return true;
        UtilityManager utility = plugin.utilityManager();
        if (utility == null) return false;
        String actorRank = utility.rankId(source);
        String targetRank = utility.rankId(target);
        return data.getBoolean("ban-ranks." + actorRank + "." + targetRank,
                defaultCanBan(actorRank, targetRank));
    }

    public void handlePreset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servertoolkit.ban.manage")) { deny(sender); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) openPresetMenu(player);
            else sender.sendMessage("Die GUI ist nur im Spiel verfügbar.");
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            ConfigurationSection presets = data.getConfigurationSection("presets");
            sender.sendMessage(color("&6Ban-Presets: &f" + (presets == null ? "keine" : String.join(", ", presets.getKeys(false)))));
            sender.sendMessage(color("&7/banpreset set <id> <dauer> <grund> | /banpreset delete <id>"));
            return;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 4) { sender.sendMessage(color("&c/banpreset set <id> <dauer: 30m|2h|7d|perm> <Grund...>")); return; }
            String id = normalize(args[1]);
            Long duration = durationMillis(args[2]);
            if (id == null || duration == null) { sender.sendMessage(color("&cUngültige ID oder Dauer. Beispiele: 30m, 2h, 7d, perm")); return; }
            data.set("presets." + id + ".duration", args[2].toLowerCase(Locale.ROOT));
            data.set("presets." + id + ".reason", String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
            save(); sender.sendMessage(color("&aPreset &f" + id + " &agespeichert.")); return;
        }
        if (args[0].equalsIgnoreCase("delete")) {
            String id = args.length > 1 ? normalize(args[1]) : null;
            if (id == null || !data.contains("presets." + id)) { sender.sendMessage(color("&c/banpreset delete <id>")); return; }
            data.set("presets." + id, null); save(); sender.sendMessage(color("&aPreset &f" + id + " &agelöscht.")); return;
        }
        sender.sendMessage(color("&c/banpreset [gui|list|set|delete]"));
    }

    private void openPresetMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new PresetHolder(), 54, GuiStyle.title("Ban-Presets"));
        ConfigurationSection presets = data.getConfigurationSection("presets");
        List<String> ids = presets == null ? new ArrayList<>() : new ArrayList<>(presets.getKeys(false));
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        int[] slots = vanishSlots();
        for (int i = 0; i < Math.min(slots.length, ids.size()); i++) {
            String id = ids.get(i);
            inv.setItem(slots[i], presetItem(Material.NAME_TAG, "&c" + id,
                    "&7Dauer: &f" + data.getString("presets." + id + ".duration", "perm")
                            + " &8| &7Grund: &f" + data.getString("presets." + id + ".reason", "Regelverstoß"), "open:" + id));
        }
        inv.setItem(49, presetItem(Material.EMERALD, "&a+ Neues Preset", "&7Klicken und ID im Chat eingeben", "new"));
        inv.setItem(53, presetItem(Material.BARRIER, "&cSchließen", "", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void openPresetEditor(Player player, String id) {
        if (!data.isConfigurationSection("presets." + id)) { openPresetMenu(player); return; }
        Inventory inv = Bukkit.createInventory(new PresetHolder(), 27, GuiStyle.title("Preset: " + id));
        String duration = data.getString("presets." + id + ".duration", "perm");
        String reason = data.getString("presets." + id + ".reason", "Regelverstoß");
        inv.setItem(10, presetItem(Material.WRITABLE_BOOK, "&eGrund ändern", "&7Aktuell: &f" + reason, "reason:" + id));
        inv.setItem(13, presetItem(Material.CLOCK, "&eDauer ändern", "&7Aktuell: &f" + duration, "duration:" + id));
        inv.setItem(16, presetItem(Material.MACE, "&cBanhammer geben", "&7Erstellt einen Hammer für dieses Preset", "hammer:" + id));
        inv.setItem(22, presetItem(Material.ARROW, "&c« Zurück", "", "back"));
        inv.setItem(25, presetItem(Material.TNT, "&4Preset löschen", "&7Öffnet die Löschbestätigung", "delete:" + id));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void openPresetDeleteConfirm(Player player, String id) {
        Inventory inv = Bukkit.createInventory(new PresetHolder(), 27, GuiStyle.title("Preset löschen?"));
        inv.setItem(11, presetItem(Material.LIME_CONCRETE, "&aJa, löschen", "&7Preset: " + id, "delete-confirm:" + id));
        inv.setItem(15, presetItem(Material.RED_CONCRETE, "&cAbbrechen", "", "open:" + id));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private ItemStack presetItem(Material material, String title, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(title));
        if (!lore.isEmpty()) meta.setLore(List.of(color(lore)));
        meta.getPersistentDataContainer().set(presetAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onPresetGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PresetHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(presetAction, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("back")) { openPresetMenu(player); return; }
        if (action.equals("new")) {
            pendingPresetInputs.put(player.getUniqueId(), new PendingPresetInput("new", ""));
            player.closeInventory(); player.sendMessage(color("&eSchreibe eine ID für das neue Preset in den Chat (oder cancel).")); return;
        }
        if (action.startsWith("open:")) { openPresetEditor(player, action.substring(5)); return; }
        if (action.startsWith("reason:")) {
            String id = action.substring(7);
            pendingPresetInputs.put(player.getUniqueId(), new PendingPresetInput("reason", id));
            player.closeInventory(); player.sendMessage(color("&eSchreibe den neuen Bann-Grund in den Chat (oder cancel).")); return;
        }
        if (action.startsWith("duration:")) {
            String id = action.substring(9);
            List<String> durations = List.of("30m", "2h", "1d", "7d", "30d", "perm");
            String current = data.getString("presets." + id + ".duration", "perm");
            int next = (durations.indexOf(current) + 1) % durations.size();
            data.set("presets." + id + ".duration", durations.get(next)); save(); openPresetEditor(player, id); return;
        }
        if (action.startsWith("hammer:")) {
            String id = action.substring(7); handleHammer(player, new String[]{id}); openPresetEditor(player, id); return;
        }
        if (action.startsWith("delete:")) {
            openPresetDeleteConfirm(player, action.substring(7)); return;
        }
        if (action.startsWith("delete-confirm:")) {
            String id = action.substring("delete-confirm:".length());
            data.set("presets." + id, null); save(); player.sendMessage(color("&aPreset &f" + id + " &awurde gelöscht.")); openPresetMenu(player);
        }
    }

    @EventHandler
    public void onPresetChat(AsyncPlayerChatEvent event) {
        PendingPresetInput pending = pendingPresetInputs.remove(event.getPlayer().getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.hasPermission("servertoolkit.ban.manage")) { deny(player); return; }
            if (text.equalsIgnoreCase("cancel")) { player.sendMessage(color("&7Abgebrochen.")); openPresetMenu(player); return; }
            if (pending.field().equals("new")) {
                String id = normalize(text);
                if (id == null || data.isConfigurationSection("presets." + id)) {
                    player.sendMessage(color("&cUngültige oder bereits vorhandene ID.")); openPresetMenu(player); return;
                }
                data.set("presets." + id + ".duration", "1d");
                data.set("presets." + id + ".reason", "Regelverstoß"); save(); openPresetEditor(player, id); return;
            }
            if (text.isBlank() || text.length() > 80) { player.sendMessage(color("&cDer Grund muss 1 bis 80 Zeichen lang sein.")); openPresetEditor(player, pending.id()); return; }
            data.set("presets." + pending.id() + ".reason", text); save(); openPresetEditor(player, pending.id());
        });
    }

    private record PendingPresetInput(String field, String id) {}
    private static final class PresetHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void handleHammer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar."); return; }
        if (!sender.hasPermission("servertoolkit.ban.manage")) { deny(sender); return; }
        String id = args.length > 0 ? normalize(args[0]) : null;
        if (id == null || !data.isConfigurationSection("presets." + id)) { sender.sendMessage(color("&c/banhammer <Preset>")); return; }
        ItemStack hammer = new ItemStack(Material.MACE);
        ItemMeta meta = hammer.getItemMeta();
        meta.setDisplayName(color("&c&lBanhammer &8[&f" + id + "&8]"));
        meta.setLore(java.util.List.of(color("&7Rechtsklick auf einen Spieler"), color("&8Preset: " + id)));
        meta.getPersistentDataContainer().set(hammerPreset, PersistentDataType.STRING, id);
        hammer.setItemMeta(meta);
        for (ItemStack leftover : player.getInventory().addItem(hammer).values())
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        player.sendMessage(color("&aBanhammer für Preset &f" + id + " &aerhalten."));
    }

    public void handleVanish(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar."); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (!sender.hasPermission("servertoolkit.vanish.manage")) { deny(sender); return; }
            openVisibilityRanks(player); return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("visibility")) {
            if (!sender.hasPermission("servertoolkit.vanish.manage")) { deny(sender); return; }
            setVisibility(player, args); return;
        }
        if (!player.hasPermission("servertoolkit.vanish")) { deny(sender); return; }
        String path = "vanished." + player.getUniqueId();
        boolean nowHidden = !data.getBoolean(path, false);
        data.set(path, nowHidden); save();
        syncTarget(player);
        player.sendMessage(color(nowHidden ? "&aVanish aktiviert." : "&eVanish deaktiviert."));
    }

    private List<String> ranks() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");
        List<String> ranks = section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
        ranks.sort(String.CASE_INSENSITIVE_ORDER);
        return ranks;
    }

    private int[] vanishSlots() { return new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}; }

    private void openVisibilityRanks(Player player) {
        List<String> ranks = ranks();
        Inventory inv = Bukkit.createInventory(new VanishHolder(), 54, GuiStyle.title("Vanish: Zuschauer-Rang"));
        int[] slots = vanishSlots();
        for (int i = 0; i < Math.min(slots.length, ranks.size()); i++) {
            String rank = ranks.get(i);
            inv.setItem(slots[i], vanishItem(Material.PLAYER_HEAD, "&cZuschauer: " + rank, "&7Klicken: auswählen", "viewer:" + rank));
        }
        inv.setItem(49, vanishItem(Material.BARRIER, "&cSchließen", "", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void openVisibilityTargets(Player player, String viewerRank) {
        List<String> ranks = ranks();
        Inventory inv = Bukkit.createInventory(new VanishHolder(), 54, GuiStyle.title("Vanish: " + viewerRank));
        List<String> targets = new ArrayList<>(ranks);
        targets.add("*");
        int[] slots = vanishSlots();
        for (int i = 0; i < Math.min(slots.length, targets.size()); i++) {
            String target = targets.get(i);
            boolean allowed = data.getBoolean("visibility." + viewerRank + "." + target,
                    target.equals("*") ? false : data.getBoolean("visibility." + viewerRank + ".*", false));
            String label = target.equals("*") ? "Alle Ränge (Standard)" : "Vanish-Rang: " + target;
            inv.setItem(slots[i], vanishItem(allowed ? Material.LIME_DYE : Material.GRAY_DYE,
                    (allowed ? "&aSichtbar: " : "&cVersteckt: ") + label,
                    "&7Klicken zum Umschalten", "toggle:" + viewerRank + ":" + target));
        }
        inv.setItem(45, vanishItem(Material.ARROW, "&c« Zuschauer-Ränge", "", "back"));
        inv.setItem(49, vanishItem(Material.BARRIER, "&cSchließen", "", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private ItemStack vanishItem(Material material, String name, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (!lore.isEmpty()) meta.setLore(java.util.List.of(color(lore)));
        meta.getPersistentDataContainer().set(vanishAction, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onVanishGuiClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VanishHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(vanishAction, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.equals("back")) { openVisibilityRanks(player); return; }
        if (action.startsWith("viewer:")) {
            openVisibilityTargets(player, action.substring("viewer:".length())); return;
        }
        if (action.startsWith("toggle:")) {
            String[] parts = action.split(":", 3);
            if (parts.length != 3) return;
            String path = "visibility." + parts[1] + "." + parts[2];
            data.set(path, !data.getBoolean(path, parts[2].equals("*")
                    ? false : data.getBoolean("visibility." + parts[1] + ".*", false)));
            save();
            for (Player online : Bukkit.getOnlinePlayers()) syncViewer(online);
            openVisibilityTargets(player, parts[1]);
        }
    }

    private static final class VanishHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private void setVisibility(Player sender, String[] args) {
        if (args.length != 4 || !(args[3].equalsIgnoreCase("on") || args[3].equalsIgnoreCase("off"))) {
            sender.sendMessage(color("&c/vanish visibility <Zuschauer-Rang> <Vanish-Rang|*> <on|off>")); return;
        }
        String viewer = normalize(args[1]), target = args[2].equals("*") ? "*" : normalize(args[2]);
        if (viewer == null || target == null || (!viewer.equals("*") && !plugin.getConfig().isConfigurationSection("ranks." + viewer))
                || (!target.equals("*") && !plugin.getConfig().isConfigurationSection("ranks." + target))) {
            sender.sendMessage(color("&cUnbekannter Rang. Ränge: /rank list")); return;
        }
        data.set("visibility." + viewer + "." + target, args[3].equalsIgnoreCase("on")); save();
        for (Player online : Bukkit.getOnlinePlayers()) syncViewer(online);
        sender.sendMessage(color("&aVanish-Sichtbarkeit für Zuschauer-Rang &f" + viewer + " &aauf &f" + target + " &agesetzt."));
    }

    private void ban(CommandSender source, String name, String id) {
        String reason = data.getString("presets." + id + ".reason", "Regelverstoß");
        String duration = data.getString("presets." + id + ".duration", "perm");
        Long millis = durationMillis(duration);
        if (millis == null) { source.sendMessage(color("&cDas Preset hat eine ungültige Dauer.")); return; }
        Date expiry = millis == 0 ? null : new Date(System.currentTimeMillis() + millis);
        String message = color("&cGebannt: &f" + reason + " &7(Preset: " + id + ")");
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, ChatColor.stripColor(message), expiry, source.getName());
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) online.kickPlayer(message);
        source.sendMessage(color("&a" + name + " wurde mit Preset &f" + id + " &agebannt (&f" + duration + "&a)."));
    }

    @EventHandler
    public void onHammerUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target) || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(hammerPreset, PersistentDataType.STRING);
        if (id == null) return;
        event.setCancelled(true);
        Player source = event.getPlayer();
        if (!source.hasPermission("servertoolkit.ban")) { deny(source); return; }
        if (!data.isConfigurationSection("presets." + id)) { source.sendMessage(color("&cDieses Preset gibt es nicht mehr.")); return; }
        if (!canBanRank(source, target)) {
            source.sendMessage(color("&cDein Rang darf den Rang &f" +
                    plugin.getConfig().getString("ranks." + plugin.utilityManager().rankId(target) + ".display", plugin.utilityManager().rankId(target)) +
                    " &cnicht bannen."));
            return;
        }
        ban(source, target.getName(), id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player joined = event.getPlayer();
            syncViewer(joined);
            syncTarget(joined);
        });
    }

    private void syncTarget(Player target) {
        boolean hidden = data.getBoolean("vanished." + target.getUniqueId(), false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (!hidden || canSee(viewer, target)) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }
    }

    private void syncViewer(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            boolean hidden = data.getBoolean("vanished." + target.getUniqueId(), false);
            if (!hidden || canSee(viewer, target)) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }
    }

    private boolean canSee(Player viewer, Player vanished) {
        if (viewer.isOp() || viewer.hasPermission("servertoolkit.vanish.see")) return true;
        UtilityManager utility = plugin.utilityManager();
        if (utility == null) return false;
        String viewerRank = utility.rankId(viewer), vanishedRank = utility.rankId(vanished);
        return data.getBoolean("visibility." + viewerRank + "." + vanishedRank,
                data.getBoolean("visibility." + viewerRank + ".*", false));
    }

    private Long durationMillis(String value) {
        if (value == null) return null;
        String s = value.toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("0")) return 0L;
        if (!s.matches("[1-9][0-9]*[smhdw]")) return null;
        try {
            long amount = Long.parseLong(s.substring(0, s.length() - 1));
            long factor = switch (s.charAt(s.length() - 1)) { case 's' -> 1000L; case 'm' -> 60000L; case 'h' -> 3600000L; case 'd' -> 86400000L; default -> 604800000L; };
            return Math.multiplyExact(amount, factor);
        } catch (ArithmeticException | NumberFormatException ex) { return null; }
    }

    private String normalize(String value) {
        String id = value.toLowerCase(Locale.ROOT);
        return id.matches("[a-z0-9_-]{1,24}") ? id : null;
    }
    private void deny(CommandSender sender) { sender.sendMessage(color("&cDafür hast du keine Rechte.")); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Moderationseinstellungen konnten nicht gespeichert werden: " + ex.getMessage()); }
    }
}
