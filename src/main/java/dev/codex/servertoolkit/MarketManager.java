package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MarketManager implements CommandExecutor, Listener {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;

    public MarketManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException exception) {
                plugin.getLogger().severe("Konnte market.yml nicht erstellen: " + exception.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur im Spiel verfügbar.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("ah")) {
            if (args.length == 0) openAuctionHouse(player);
            else if (args[0].equalsIgnoreCase("sell")) createListing(player, args);
            else if (args[0].equalsIgnoreCase("cancel")) cancelListing(player, args);
            else help(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("orders")) {
            showOrders(player);
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(color("&6Aufträge: &f/order create <Material> <Menge> <Belohnung>, /order fill <ID>, /order cancel <ID>"));
        } else if (args[0].equalsIgnoreCase("create")) createOrder(player, args);
        else if (args[0].equalsIgnoreCase("fill")) fillOrder(player, args);
        else if (args[0].equalsIgnoreCase("cancel")) cancelOrder(player, args);
        else player.sendMessage(color("&6Nutze &f/orders &6für offene Aufträge."));
        return true;
    }

    private void createListing(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cNutzung: /ah sell <Preis>"));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(color("&cHalte den Gegenstand in der Haupthand."));
            return;
        }
        double price = parsePrice(args[1]);
        if (price <= 0) {
            player.sendMessage(color("&cDer Preis muss größer als 0 sein."));
            return;
        }
        int id = data.getInt("nextAuctionId", 1);
        data.set("nextAuctionId", id + 1);
        String path = "auctions." + id;
        data.set(path + ".seller", player.getUniqueId().toString());
        data.set(path + ".sellerName", player.getName());
        data.set(path + ".price", price);
        data.set(path + ".item", hand.serialize());
        player.getInventory().setItemInMainHand(null);
        save();
        player.sendMessage(color("&aAuktion &e#" + id + " &awurde für &e" + price + " &aCoins eingestellt."));
    }

    private void openAuctionHouse(Player player) {
        AuctionHolder holder = new AuctionHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, GuiStyle.title("Auktionshaus"));
        holder.inventory = inventory;
        ConfigurationSection auctions = data.getConfigurationSection("auctions");
        if (auctions != null) {
            int slot = 0;
            for (String id : auctions.getKeys(false)) {
                if (slot >= 45) break;
                ItemStack item = readItem("auctions." + id + ".item");
                if (item == null) continue;
                ItemStack display = item.clone();
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(color("&7Verkäufer: &f" + data.getString("auctions." + id + ".sellerName", "Unbekannt")));
                    lore.add(color("&7Preis: &e" + data.getDouble("auctions." + id + ".price") + " Coins"));
                    lore.add(color("&8Klicke zum Kaufen | ID #" + id));
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
                inventory.setItem(slot, display);
                holder.listings.put(slot, id);
                slot++;
            }
        }
        GuiStyle.frame(inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String id = holder.listings.get(event.getRawSlot());
        if (id != null) buyListing(player, id);
    }

    private void buyListing(Player buyer, String id) {
        String path = "auctions." + id;
        if (!data.contains(path)) {
            buyer.sendMessage(color("&cDiese Auktion ist nicht mehr verfügbar."));
            openAuctionHouse(buyer);
            return;
        }
        UUID sellerId = UUID.fromString(data.getString(path + ".seller"));
        if (sellerId.equals(buyer.getUniqueId())) {
            buyer.sendMessage(color("&cDu kannst deine eigene Auktion nicht kaufen."));
            return;
        }
        ItemStack item = readItem(path + ".item");
        if (item == null) {
            buyer.sendMessage(color("&cDer Gegenstand konnte nicht geladen werden."));
            return;
        }
        double price = data.getDouble(path + ".price");
        if (buyer.getInventory().firstEmpty() < 0) {
            buyer.sendMessage(color("&cDein Inventar ist voll."));
            return;
        }
        if (!plugin.economyManager().has(buyer.getUniqueId(), price) || !plugin.economyManager().withdraw(buyer.getUniqueId(), price)) {
            buyer.sendMessage(color("&cDu hast nicht genug Coins."));
            return;
        }
        Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            removeExactItem(buyer, item);
            plugin.economyManager().deposit(buyer.getUniqueId(), price);
            buyer.sendMessage(color("&cDein Inventar ist voll; der Kauf wurde abgebrochen."));
            return;
        }
        if (!plugin.economyManager().deposit(sellerId, price)) {
            removeExactItem(buyer, item);
            plugin.economyManager().deposit(buyer.getUniqueId(), price);
            buyer.sendMessage(color("&cDie Auszahlung an den Verkäufer ist fehlgeschlagen; der Kauf wurde zurückgerollt."));
            return;
        }
        String sellerName = data.getString(path + ".sellerName", "Spieler");
        data.set(path, null);
        save();
        buyer.sendMessage(color("&aGekauft: &f" + item.getType().name() + " &afür &e" + price + " Coins."));
        Player seller = Bukkit.getPlayer(sellerId);
        if (seller != null) seller.sendMessage(color("&aDeine Auktion &e#" + id + " &awurde von &f" + buyer.getName() + " &agekauft."));
        openAuctionHouse(buyer);
    }

    private void cancelListing(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(color("&cNutzung: /ah cancel <ID>"));
            return;
        }
        String path = "auctions." + args[1];
        if (!data.contains(path) || !data.getString(path + ".seller", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cDiese Auktion gehört dir nicht oder existiert nicht."));
            return;
        }
        ItemStack item = readItem(path + ".item");
        if (item != null) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        data.set(path, null);
        save();
        player.sendMessage(color("&aAuktion abgebrochen und Gegenstand zurückgegeben."));
    }

    private void createOrder(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(color("&cNutzung: /order create <Material> <Menge> <Belohnung>"));
            return;
        }
        Material material = Material.matchMaterial(args[1]);
        int amount;
        double reward;
        try { amount = Integer.parseInt(args[2]); reward = Double.parseDouble(args[3]); }
        catch (NumberFormatException exception) { player.sendMessage(color("&cMenge oder Belohnung ist ungültig.")); return; }
        if (material == null || !material.isItem() || material.isAir() || amount < 1 || amount > 2304 || !Double.isFinite(reward) || reward <= 0) {
            player.sendMessage(color("&cMaterial, Menge oder Belohnung ist ungültig."));
            return;
        }
        if (!plugin.economyManager().has(player.getUniqueId(), reward) || !plugin.economyManager().withdraw(player.getUniqueId(), reward)) {
            player.sendMessage(color("&cDu hast nicht genug Coins für den Treuhandbetrag."));
            return;
        }
        int id = data.getInt("nextOrderId", 1);
        data.set("nextOrderId", id + 1);
        String path = "orders." + id;
        data.set(path + ".owner", player.getUniqueId().toString());
        data.set(path + ".ownerName", player.getName());
        data.set(path + ".material", material.name());
        data.set(path + ".amount", amount);
        data.set(path + ".reward", reward);
        save();
        player.sendMessage(color("&aAuftrag &e#" + id + " &aeingestellt: &f" + amount + "x " + material.name() + " &afür &e" + reward + " Coins."));
    }

    private void showOrders(Player player) {
        ConfigurationSection orders = data.getConfigurationSection("orders");
        player.sendMessage(color("&6&m-----&r &eOffene Aufträge &6&m-----"));
        if (orders == null || orders.getKeys(false).isEmpty()) {
            player.sendMessage(color("&7Keine offenen Aufträge. Erstelle einen mit &f/order create"));
            return;
        }
        int shown = 0;
        for (String id : orders.getKeys(false)) {
            String path = "orders." + id;
            player.sendMessage(color("&e#" + id + " &f" + data.getInt(path + ".amount") + "x " + data.getString(path + ".material")
                    + " &7von &f" + data.getString(path + ".ownerName") + " &7- &a" + data.getDouble(path + ".reward") + " Coins &8(/order fill " + id + ")"));
            if (++shown >= 10) break;
        }
    }

    private void fillOrder(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(color("&cNutzung: /order fill <ID>")); return; }
        String path = "orders." + args[1];
        if (!data.contains(path)) { player.sendMessage(color("&cDieser Auftrag ist nicht mehr offen.")); return; }
        if (data.getString(path + ".owner", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cDu kannst deinen eigenen Auftrag nicht erfüllen."));
            return;
        }
        Material material = Material.matchMaterial(data.getString(path + ".material", ""));
        int amount = data.getInt(path + ".amount");
        if (material == null || countItems(player, material) < amount) {
            player.sendMessage(color("&cDu hast nicht genug Gegenstände für diesen Auftrag."));
            return;
        }
        UUID ownerId = UUID.fromString(data.getString(path + ".owner"));
        double reward = data.getDouble(path + ".reward");
        if (!plugin.economyManager().deposit(player.getUniqueId(), reward)) {
            player.sendMessage(color("&cDie Auszahlung ist fehlgeschlagen. Der Auftrag bleibt offen."));
            return;
        }
        removeItems(player, material, amount);
        data.set(path, null);
        save();
        player.sendMessage(color("&aAuftrag erfüllt: &e" + reward + " Coins &awurden gutgeschrieben."));
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) owner.sendMessage(color("&aDein Auftrag wurde von &f" + player.getName() + " &aerfüllt."));
    }

    private void cancelOrder(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(color("&cNutzung: /order cancel <ID>")); return; }
        String path = "orders." + args[1];
        if (!data.contains(path) || !data.getString(path + ".owner", "").equals(player.getUniqueId().toString())) {
            player.sendMessage(color("&cDieser Auftrag gehört dir nicht oder existiert nicht."));
            return;
        }
        if (!plugin.economyManager().deposit(player.getUniqueId(), data.getDouble(path + ".reward"))) {
            player.sendMessage(color("&cDie Erstattung ist fehlgeschlagen. Der Auftrag bleibt offen."));
            return;
        }
        data.set(path, null);
        save();
        player.sendMessage(color("&aAuftrag aufgehoben; Treuhandbetrag zurückerstattet."));
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && amount > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material) continue;
            int taken = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - taken);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            amount -= taken;
        }
    }

    private void removeExactItem(Player player, ItemStack target) {
        int amount = target.getAmount();
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && amount > 0; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || !item.isSimilar(target)) continue;
            int taken = Math.min(amount, item.getAmount());
            item.setAmount(item.getAmount() - taken);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            amount -= taken;
        }
    }

    private ItemStack readItem(String path) {
        Object raw = data.get(path);
        if (!(raw instanceof Map<?, ?> map)) return null;
        Map<String, Object> serialized = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) if (entry.getKey() instanceof String key) serialized.put(key, entry.getValue());
        try { return ItemStack.deserialize(serialized); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private double parsePrice(String raw) {
        try { double value = Double.parseDouble(raw); return Double.isFinite(value) ? value : -1; } catch (NumberFormatException ignored) { return -1; }
    }

    private void save() {
        try { data.save(file); }
        catch (IOException exception) { plugin.getLogger().severe("Konnte market.yml nicht speichern: " + exception.getMessage()); }
    }

    private void help(Player player) {
        player.sendMessage(color("&6Auktionshaus: &f/ah, /ah sell <Preis>, /ah cancel <ID>"));
        player.sendMessage(color("&6Aufträge: &f/orders, /order create <Material> <Menge> <Belohnung>, /order fill|cancel <ID>"));
    }

    private String color(String message) { return ChatColor.translateAlternateColorCodes('&', message); }

    private static final class AuctionHolder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> listings = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
}
