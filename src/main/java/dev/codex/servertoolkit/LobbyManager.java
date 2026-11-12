package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.inventory.EquipmentSlot;

/** Lobby-only protections, selector item/menu, and BungeeCord server switching. */
public final class LobbyManager implements Listener {
    private final ServerToolkit plugin;
    private final NamespacedKey selectorKey;

    public LobbyManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.selectorKey = new NamespacedKey(plugin, "lobby_server_selector");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onJoin(Player player) {
        if (!plugin.isLobbyMode()) return;
        if (plugin.getConfig().getBoolean("lobby.force-adventure", true)) player.setGameMode(GameMode.ADVENTURE);
        if (plugin.getConfig().getBoolean("lobby.selector.give-on-join", true)) giveSelector(player);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public void openMenu(Player player) {
        if (!plugin.isLobbyMode()) {
            player.sendMessage(color("&cDie Serverauswahl gibt es nur auf der Lobby."));
            return;
        }
        var servers = plugin.getConfig().getConfigurationSection("lobby.selector.servers");
        if (servers == null || servers.getKeys(false).isEmpty()) {
            player.sendMessage(color("&cEs sind keine Zielserver konfiguriert."));
            return;
        }
        SelectorHolder holder = new SelectorHolder();
        Inventory menu = Bukkit.createInventory(holder, 27,
                color(plugin.getConfig().getString("lobby.selector.title", "&8Server auswählen")));
        holder.inventory = menu;
        for (String id : servers.getKeys(false)) {
            String path = "lobby.selector.servers." + id;
            String target = plugin.getConfig().getString(path + ".target", "").trim();
            if (target.isEmpty()) continue;
            Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", "COMPASS"));
            if (material == null || !material.isItem()) material = Material.COMPASS;
            int slot = plugin.getConfig().getInt(path + ".slot", -1);
            if (slot < 0 || slot >= menu.getSize() || menu.getItem(slot) != null) continue;
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(color(plugin.getConfig().getString(path + ".name", "&c" + id)));
            List<String> lore = plugin.getConfig().getStringList(path + ".lore");
            if (!lore.isEmpty()) meta.setLore(lore.stream().map(this::color).toList());
            icon.setItemMeta(meta);
            menu.setItem(slot, icon);
            holder.targets.put(slot, target);
        }
        GuiStyle.frame(menu);
        player.openInventory(menu);
    }

    public void connectToServer(Player player, String target) {
        if (target == null || target.isBlank()) {
            player.sendMessage(color("&cDer Zielserver ist nicht konfiguriert."));
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            player.sendMessage(color("&6Verbinde mit &f" + target + "&6 ..."));
        } catch (IOException exception) {
            player.sendMessage(color("&cDie Verbindung konnte nicht gestartet werden."));
            plugin.getLogger().warning("Proxy-Verbindung fehlgeschlagen: " + exception.getMessage());
        }
    }

    private void giveSelector(Player player) {
        Material selectorType = Material.matchMaterial(plugin.getConfig().getString("lobby.selector.item", "COMPASS"));
        if (selectorType == null || !selectorType.isItem()) selectorType = Material.COMPASS;
        ItemStack selector = new ItemStack(selectorType);
        ItemMeta meta = selector.getItemMeta();
        meta.setDisplayName(color(plugin.getConfig().getString("lobby.selector.item-name", "&c&lServer auswählen")));
        meta.setLore(List.of(color("&7Rechtsklick zum Öffnen")));
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte) 1);
        selector.setItemMeta(meta);

        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (isSelector(existing)) player.getInventory().setItem(slot, null);
        }
        int preferred = plugin.getConfig().getInt("lobby.selector.item-slot", 4);
        if (preferred >= 0 && preferred < 9 && player.getInventory().getItem(preferred) == null) {
            player.getInventory().setItem(preferred, selector);
        } else {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(selector);
            if (!leftovers.isEmpty()) player.sendMessage(color("&eDein Inventar ist voll. Nutze &f/servers&e für die Serverauswahl."));
        }
    }

    private boolean isSelector(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onSelectorUse(PlayerInteractEvent event) {
        if (!plugin.isLobbyMode() || event.getHand() != EquipmentSlot.HAND || !isSelector(event.getItem())) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SelectorHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) return;
        connectToServer(player, holder.targets.get(event.getSlot()));
        player.closeInventory();
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.isLobbyMode()
                || !plugin.getConfig().getBoolean("lobby.protection.hunger", true)) return;
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (plugin.isLobbyMode() && plugin.getConfig().getBoolean("lobby.protection.item-pickup", true)
                && event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler
    public void onVoidFall(PlayerMoveEvent event) {
        if (!plugin.isLobbyMode() || !plugin.getConfig().getBoolean("lobby.void-rescue.enabled", true)) return;
        int threshold = plugin.getConfig().getInt("lobby.void-rescue.y", -10);
        if (event.getTo() == null || event.getTo().getY() >= threshold) return;
        Location spawn = plugin.loadSpawn();
        if (spawn == null) {
            World world = event.getPlayer().getWorld();
            spawn = world.getSpawnLocation();
        }
        event.getPlayer().teleport(spawn);
        event.getPlayer().sendMessage(color("&aDu wurdest zum Lobby-Spawn zurückgebracht."));
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private static final class SelectorHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> targets = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
}
