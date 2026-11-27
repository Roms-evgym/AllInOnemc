package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ContentToolsGuiManager implements Listener {
    private final ServerToolkit plugin;
    private final NamespacedKey actionKey;

    ContentToolsGuiManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "content_gui_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void openNick(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Nick & Skin");
        holder.inventory = inv;
        add(inv, 11, Material.NAME_TAG, "§aZufälligen Nick aktivieren", "nick-random");
        add(inv, 13, Material.PLAYER_HEAD, "§eEigenen Nick entfernen", "nick-restore");
        if (player.hasPermission("servertoolkit.nick.others")) {
            add(inv, 15, Material.WRITABLE_BOOK, "§bSpieler auswählen", "nick-targets");
        }
        player.openInventory(inv);
    }

    void openPrank(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Prank-Einstellungen");
        holder.inventory = inv;
        boolean enabled = plugin.contentToolsManager().prankEnabled(player);
        add(inv, 11, enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "§aPranks: AKTIV" : "§cPranks: DEAKTIVIERT", "prank-toggle");
        add(inv, 15, Material.BARRIER, "§7Schließen", "close");
        player.openInventory(inv);
    }

    void openTargets(Player actor, String type) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, type.equals("nick") ? "§8Nick: Spieler auswählen"
                : type.equals("lag") ? "§8Lag-Simulation: Ziel" : "§8Crash-Simulation: Ziel");
        holder.inventory = inv;
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(actor) || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName("§f" + target.getName());
                List<String> lore = new ArrayList<>();
                lore.add(type.equals("nick") ? "§7Für Content nicken" : "§7Prank-Status: "
                        + (plugin.contentToolsManager().prankEnabled(target) ? "§aAktiviert" : "§cDeaktiviert"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                        type + ":" + target.getUniqueId());
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }
        add(inv, 49, Material.ARROW, "§7Zurück", "back-" + type);
        actor.openInventory(inv);
    }

    private void add(Inventory inv, int slot, Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)
                || !(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        ContentToolsManager tools = plugin.contentToolsManager();
        switch (action) {
            case "nick-random" -> {
                if (actor.hasPermission("servertoolkit.nick")) actor.performCommand("nick random");
                actor.closeInventory();
            }
            case "nick-restore" -> {
                tools.restoreNick(actor);
                actor.closeInventory();
            }
            case "nick-targets" -> openTargets(actor, "nick");
            case "prank-toggle" -> {
                if (actor.hasPermission("servertoolkit.prank.toggle")) tools.togglePrank(actor);
                actor.closeInventory();
                openPrank(actor);
            }
            case "close" -> actor.closeInventory();
            default -> {
                if (action.startsWith("back-")) {
                    actor.closeInventory();
                    if (action.equals("back-nick")) openNick(actor);
                    else actor.sendMessage("§7Menü geschlossen.");
                    return;
                }
                int colon = action.indexOf(':');
                if (colon < 1) return;
                String type = action.substring(0, colon);
                UUID targetId;
                try { targetId = UUID.fromString(action.substring(colon + 1)); }
                catch (IllegalArgumentException ex) { return; }
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) { actor.sendMessage("§cSpieler ist nicht mehr online."); actor.closeInventory(); return; }
                if (type.equals("nick")) {
                    if (actor.hasPermission("servertoolkit.nick.others")) tools.applyNickFromGui(target, actor);
                } else if (type.equals("lag")) {
                    tools.guiStartLag(target, actor);
                } else if (type.equals("crash")) {
                    tools.guiStartCrash(target, actor);
                }
                actor.closeInventory();
            }
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
