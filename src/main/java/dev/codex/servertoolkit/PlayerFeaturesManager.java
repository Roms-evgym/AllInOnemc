package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerFeaturesManager implements CommandExecutor, Listener {
    private final ServerToolkit plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, UUID> incomingRequests = new HashMap<>();

    public PlayerFeaturesManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "player_features_action");
        plugin.getConfig().addDefault("rtp.worlds.survival", "world");
        plugin.getConfig().addDefault("rtp.worlds.nether", "world_nether");
        plugin.getConfig().addDefault("rtp.worlds.end", "world_the_end");
        plugin.getConfig().addDefault("rtp.min-radius", 500);
        plugin.getConfig().addDefault("rtp.max-radius", 5000);
        plugin.getConfig().addDefault("rtp.cooldown-seconds", 60);
        plugin.getConfig().addDefault("shop.items.DIAMOND.price", 250.0);
        plugin.getConfig().addDefault("shop.items.IRON_INGOT.price", 12.0);
        plugin.getConfig().addDefault("shop.items.GOLD_INGOT.price", 20.0);
        plugin.getConfig().addDefault("shop.items.EMERALD.price", 100.0);
        plugin.getConfig().addDefault("shop.items.ENDER_PEARL.price", 35.0);
        plugin.getConfig().addDefault("shop.items.GOLDEN_APPLE.price", 150.0);
        plugin.getConfig().addDefault("sell-prices.DIAMOND", 50.0);
        plugin.getConfig().addDefault("sell-prices.IRON_INGOT", 3.0);
        plugin.getConfig().addDefault("sell-prices.GOLD_INGOT", 5.0);
        plugin.getConfig().addDefault("sell-prices.EMERALD", 20.0);
        plugin.getConfig().addDefault("sell-prices.COAL", 1.0);
        plugin.getConfig().addDefault("sell-prices.RAW_IRON", 2.0);
        plugin.getConfig().addDefault("sell-prices.RAW_GOLD", 3.0);
        plugin.getConfig().addDefault("sell-prices.COPPER_INGOT", 0.5);
        plugin.getConfig().addDefault("sell-prices.WHEAT", 0.25);
        plugin.getConfig().addDefault("sell-prices.CARROT", 0.25);
        plugin.getConfig().addDefault("sell-prices.POTATO", 0.25);
        plugin.getConfig().addDefault("sell-prices.SUGAR_CANE", 0.2);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "shop" -> openShop(player);
            case "sell" -> sell(player, args);
            case "rtp" -> openRtpMenu(player);
            case "tpa" -> sendRequest(player, args);
            case "tpaccept" -> acceptRequest(player);
            case "tpdeny" -> denyRequest(player);
            default -> { return false; }
        }
        return true;
    }

    private void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(), 54, title("&8Server-Shop"));
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items != null) {
            List<String> ids = new ArrayList<>(items.getKeys(false));
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            int slot = 0;
            for (String id : ids) {
                if (slot >= 45) break;
                Material material = Material.matchMaterial(id);
                double price = plugin.getConfig().getDouble("shop.items." + id + ".price", -1);
                int amount = Math.max(1, Math.min(64, plugin.getConfig().getInt("shop.items." + id + ".amount", 1)));
                if (material == null || !material.isItem() || material.isAir() || price <= 0) continue;
                ItemStack display = new ItemStack(material, amount);
                ItemMeta meta = display.getItemMeta();
                meta.setDisplayName(color("&e" + pretty(id)));
                meta.setLore(List.of(color("&7Preis: &6" + price + " Coins"),
                        color("&7Menge: &f" + amount), color("&aKlicken zum Kaufen")));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "buy:" + id);
                display.setItemMeta(meta);
                inv.setItem(slot++, display);
            }
        }
        inv.setItem(49, menuItem(Material.BARRIER, "&cSchließen", "close"));
        GuiStyle.frame(inv);
        player.openInventory(inv);
    }

    private void buy(Player player, String id) {
        String path = "shop.items." + id;
        Material material = Material.matchMaterial(id);
        double price = plugin.getConfig().getDouble(path + ".price", -1);
        int amount = Math.max(1, Math.min(64, plugin.getConfig().getInt(path + ".amount", 1)));
        if (material == null || !material.isItem() || material.isAir() || price <= 0) {
            player.sendMessage(color("&cDieser Artikel ist nicht verfügbar."));
            return;
        }
        ItemStack purchase = new ItemStack(material, amount);
        if (!canFit(player, purchase)) {
            player.sendMessage(color("&cNicht genug Platz im Inventar."));
            return;
        }
        if (!plugin.economyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage(color("&cDu hast nicht genug Coins. Dein Guthaben: &e" + plugin.economyManager().formatted(player.getUniqueId())));
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(purchase);
        if (!leftovers.isEmpty()) {
            plugin.economyManager().deposit(player.getUniqueId(), price);
            player.sendMessage(color("&cNicht genug Platz. Der Kauf wurde abgebrochen."));
            return;
        }
        player.sendMessage(color("&aGekauft: &f" + amount + "x " + pretty(id) + " &afür &6" + price + " Coins."));
    }

    private boolean canFit(Player player, ItemStack wanted) {
        int capacity = 0;
        int maxStack = wanted.getMaxStackSize();
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) capacity += maxStack;
            else if (existing.isSimilar(wanted)) capacity += Math.max(0, maxStack - existing.getAmount());
            if (capacity >= wanted.getAmount()) return true;
        }
        return false;
    }

    private void sell(Player player, String[] args) {
        String mode = args.length == 0 ? "hand" : args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("hand")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { player.sendMessage(color("&cHalte zuerst einen Gegenstand in der Haupthand.")); return; }
            double unitPrice = plugin.getConfig().getDouble("sell-prices." + hand.getType().name(), -1);
            if (unitPrice <= 0) { player.sendMessage(color("&cDieser Gegenstand kann nicht verkauft werden.")); return; }
            int amount = hand.getAmount();
            double total = unitPrice * amount;
            player.getInventory().setItemInMainHand(null);
            plugin.economyManager().deposit(player.getUniqueId(), total);
            player.sendMessage(color("&aVerkauft: &f" + amount + "x " + pretty(hand.getType().name()) + " &afür &6" + total + " Coins."));
            return;
        }
        if (mode.equals("all")) {
            int sold = 0;
            double total = 0;
            for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                double unitPrice = plugin.getConfig().getDouble("sell-prices." + stack.getType().name(), -1);
                if (unitPrice <= 0) continue;
                sold += stack.getAmount();
                total += unitPrice * stack.getAmount();
                player.getInventory().setItem(slot, null);
            }
            if (sold == 0) { player.sendMessage(color("&cDu hast keine verkaufbaren Gegenstände im Inventar.")); return; }
            plugin.economyManager().deposit(player.getUniqueId(), total);
            player.sendMessage(color("&a" + sold + " Gegenstände für &6" + total + " Coins &averkauft."));
            return;
        }
        player.sendMessage(color("&cNutzung: /sell [hand|all]"));
    }

    private void openRtpMenu(Player player) {
        if (plugin.isLobbyMode()) { player.sendMessage(color("&cRTP ist auf der Lobby deaktiviert.")); return; }
        if (!player.hasPermission("servertoolkit.rtp")) { player.sendMessage(color("&cDafür hast du keine Rechte.")); return; }
        Inventory inv = Bukkit.createInventory(new RtpHolder(), 27, title("&8Zufalls-Teleport"));
        addRtpWorld(inv, 11, "survival", Material.GRASS_BLOCK, "&aOverworld", "Zufälliger sicherer Ort in der Overworld");
        addRtpWorld(inv, 13, "nether", Material.NETHERRACK, "&cNether", "Zufälliger sicherer Ort im Nether");
        addRtpWorld(inv, 15, "end", Material.END_STONE, "&5End", "Zufälliger sicherer Ort im End");
        inv.setItem(22, menuItem(Material.BARRIER, "&cSchließen", "close"));
        player.openInventory(inv);
    }

    private void addRtpWorld(Inventory inv, int slot, String key, Material icon, String name, String lore) {
        String worldName = plugin.getConfig().getString("rtp.worlds." + key, switch (key) {
            case "nether" -> "world_nether";
            case "end" -> "world_the_end";
            default -> "world";
        });
        World world = Bukkit.getWorld(worldName);
        ItemStack item = new ItemStack(world == null ? Material.GRAY_DYE : icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        lines.add(color("&7" + lore));
        lines.add(color(world == null ? "&cWelt nicht geladen: " + worldName : "&eKlicken zum Teleportieren"));
        meta.setLore(lines);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "rtp:" + key);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void randomTeleport(Player player, String dimension) {
        long now = System.currentTimeMillis();
        long readyAt = rtpCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            player.sendMessage(color("&cWarte noch " + ((readyAt - now + 999) / 1000) + " Sekunden bis zum nächsten RTP."));
            return;
        }
        String worldName = plugin.getConfig().getString("rtp.worlds." + dimension, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { player.sendMessage(color("&cDie Welt &f" + worldName + " &cist nicht geladen.")); return; }
        int min = Math.max(0, plugin.getConfig().getInt("rtp.min-radius", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("rtp.max-radius", 5000));
        Location center = world.getSpawnLocation();
        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            int radius = ThreadLocalRandom.current().nextInt(min, max + 1);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location candidate = dimension.equals("nether") ? safeNetherLocation(world, x, z) : safeSurfaceLocation(world, x, z);
            if (candidate == null) continue;
            player.closeInventory();
            player.teleport(candidate);
            rtpCooldowns.put(player.getUniqueId(), now + plugin.getConfig().getLong("rtp.cooldown-seconds", 60) * 1000L);
            player.sendMessage(color("&aDu wurdest sicher in &f" + dimension.toUpperCase(Locale.ROOT) + " &ateleportiert."));
            return;
        }
        player.sendMessage(color("&cKein sicherer Ort gefunden. Bitte versuche es erneut."));
    }

    private Location safeSurfaceLocation(World world, int x, int z) {
        Location probe = new Location(world, x + 0.5, world.getMinHeight(), z + 0.5);
        if (!world.getWorldBorder().isInside(probe)) return null;
        int y = world.getHighestBlockYAt(x, z);
        return safeAt(world, x, y, z);
    }

    private Location safeNetherLocation(World world, int x, int z) {
        Location probe = new Location(world, x + 0.5, world.getMinHeight(), z + 0.5);
        if (!world.getWorldBorder().isInside(probe)) return null;
        int top = Math.min(world.getMaxHeight() - 3, 120);
        int bottom = Math.max(world.getMinHeight() + 2, 32);
        for (int y = top; y >= bottom; y--) {
            Location safe = safeAt(world, x, y, z);
            if (safe != null) return safe;
        }
        return null;
    }

    private Location safeAt(World world, int x, int floorY, int z) {
        if (floorY <= world.getMinHeight() || floorY + 2 >= world.getMaxHeight()) return null;
        Block floor = world.getBlockAt(x, floorY, z);
        Block feet = world.getBlockAt(x, floorY + 1, z);
        Block head = world.getBlockAt(x, floorY + 2, z);
        if (!floor.getType().isSolid() || feet.getType().isSolid() || head.getType().isSolid()
                || feet.isLiquid() || head.isLiquid() || floor.isLiquid()) return null;
        Material material = floor.getType();
        if (material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.FIRE
                || material == Material.SOUL_FIRE || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE
                || material == Material.LAVA) return null;
        return new Location(world, x + 0.5, floorY + 1, z + 0.5);
    }

    private void sendRequest(Player requester, String[] args) {
        if (args.length != 1) { requester.sendMessage(color("&cNutzung: /tpa <Spieler>")); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(requester)) { requester.sendMessage(color("&cSpieler nicht gefunden oder ungültig.")); return; }
        if (incomingRequests.containsKey(target.getUniqueId())) { requester.sendMessage(color("&cDieser Spieler hat bereits eine offene TPA-Anfrage.")); return; }
        incomingRequests.put(target.getUniqueId(), requester.getUniqueId());
        requester.sendMessage(color("&aTPA-Anfrage an &f" + target.getName() + " &agesendet."));
        target.sendMessage(color("&e" + requester.getName() + " &7möchte sich zu dir teleportieren."));
        target.sendMessage(color("&a/tpaccept &7zum Annehmen oder &c/tpdeny &7zum Ablehnen. &8(60 Sekunden)"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (incomingRequests.remove(target.getUniqueId(), requester.getUniqueId())) {
                Player onlineRequester = Bukkit.getPlayer(requester.getUniqueId());
                Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                if (onlineRequester != null) onlineRequester.sendMessage(color("&cDeine TPA-Anfrage an " + target.getName() + " ist abgelaufen."));
                if (onlineTarget != null) onlineTarget.sendMessage(color("&7Die TPA-Anfrage ist abgelaufen."));
            }
        }, 1200L);
    }

    private void acceptRequest(Player target) {
        UUID requesterId = incomingRequests.remove(target.getUniqueId());
        if (requesterId == null) { target.sendMessage(color("&cDu hast keine offene TPA-Anfrage.")); return; }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) { target.sendMessage(color("&cDer anfragende Spieler ist nicht mehr online.")); return; }
        requester.teleport(target.getLocation());
        requester.sendMessage(color("&aTPA angenommen. Du wurdest zu &f" + target.getName() + " &ateleportiert."));
        target.sendMessage(color("&aDu hast die TPA-Anfrage von &f" + requester.getName() + " &aangenommen."));
    }

    private void denyRequest(Player target) {
        UUID requesterId = incomingRequests.remove(target.getUniqueId());
        if (requesterId == null) { target.sendMessage(color("&cDu hast keine offene TPA-Anfrage.")); return; }
        Player requester = Bukkit.getPlayer(requesterId);
        target.sendMessage(color("&7TPA-Anfrage abgelehnt."));
        if (requester != null) requester.sendMessage(color("&c" + target.getName() + " hat deine TPA-Anfrage abgelehnt."));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolder) && !(holder instanceof RtpHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        if (action.equals("close")) { player.closeInventory(); return; }
        if (action.startsWith("buy:")) buy(player, action.substring(4));
        if (action.startsWith("rtp:")) randomTeleport(player, action.substring(4));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        incomingRequests.remove(event.getPlayer().getUniqueId());
        incomingRequests.values().removeIf(id -> id.equals(event.getPlayer().getUniqueId()));
    }

    private ItemStack menuItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(String id) {
        String lower = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(' ');
            if (!part.isEmpty()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String title(String text) { return color(text); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private final Map<UUID, Long> rtpCooldowns = new HashMap<>();
    private static final class ShopHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    private static final class RtpHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
