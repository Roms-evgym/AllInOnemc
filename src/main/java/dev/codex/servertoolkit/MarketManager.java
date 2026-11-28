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
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
import java.util.Comparator;

public final class MarketManager implements CommandExecutor, Listener {
    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, String> pendingSearch = new HashMap<>();

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
        if (label.equalsIgnoreCase("ah")) {
            if (args.length == 0) openAuctionHouse(player);
            else if (args[0].equalsIgnoreCase("sell")) createListing(player, args);
            else if (args[0].equalsIgnoreCase("cancel")) cancelListing(player, args);
            else help(player);
            return true;
        }
        if (label.equalsIgnoreCase("orders")) {
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

    private void openAuctionHouse(Player player) { openAuctionHouse(player, 0, "newest", ""); }

    private void openAuctionHouse(Player player, int page, String sort, String query) {
        ConfigurationSection auctions = data.getConfigurationSection("auctions");
        AuctionHolder holder = new AuctionHolder();
        holder.page = Math.max(0, page); holder.sort = sort; holder.query = query;
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&6&lAuktionshaus"));
        holder.inventory = inventory;
        List<String> ids = new ArrayList<>();
        if (auctions != null) for (String id : auctions.getKeys(false)) {
            String path = "auctions." + id;
            ItemStack item = readItem(path + ".item");
            if (item == null) continue;
            String seller = data.getString(path + ".sellerName", "Unbekannt");
            String searchable = (item.getType().name() + " " + seller + " " + id).toLowerCase(Locale.ROOT);
            if (!searchable.contains(query.toLowerCase(Locale.ROOT))) continue;
            ids.add(id);
        }
        Comparator<String> comparator = switch (sort) {
            case "price_low" -> Comparator.comparingDouble(id -> data.getDouble("auctions." + id + ".price"));
            case "price_high" -> Comparator.comparingDouble((String id) -> data.getDouble("auctions." + id + ".price")).reversed();
            case "name" -> Comparator.comparing(id -> readItem("auctions." + id + ".item").getType().name());
            default -> Comparator.comparingInt(this::parseAuctionId).reversed();
        };
        ids.sort(comparator);
        int pages = Math.max(1, (ids.size() + 44) / 45);
        holder.page = Math.min(holder.page, pages - 1);
        int start = holder.page * 45;
        for (int index = start; index < Math.min(ids.size(), start + 45); index++) {
            String id = ids.get(index); String path = "auctions." + id;
            ItemStack item = readItem(path + ".item"); if (item == null) continue;
            String seller = data.getString(path + ".sellerName", "Unbekannt");
            double price = data.getDouble(path + ".price");
            ItemStack display = item.clone(); ItemMeta meta = display.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color("&7Verkäufer: &f" + seller)); lore.add(color("&7Preis: &6" + price + " Coins"));
            lore.add(color("&eKlicken zum Kaufen"));
            if (meta != null) { meta.setLore(lore); display.setItemMeta(meta); }
            int slot = index - start; inventory.setItem(slot, display); holder.listings.put(slot, id);
        }
        addControl(inventory, 45, Material.ARROW, "&eVorherige Seite", "page_prev");
        addControl(inventory, 49, Material.HOPPER, "&bSortierung: " + sortLabel(sort), "sort");
        addControl(inventory, 50, Material.COMPASS, "&aSuche: " + (query.isEmpty() ? "Alle" : query), "search");
        addControl(inventory, 53, Material.ARROW, "&eNächste Seite (" + (holder.page + 1) + "/" + pages + ")", "page_next");
        if (ids.isEmpty()) player.sendMessage(color("&7Keine passenden Auktionen gefunden."));
        player.openInventory(inventory);
    }

    private void addControl(Inventory inv, int slot, Material material, String name, String action) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name)); meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "market_action"), org.bukkit.persistence.PersistentDataType.STRING, action);
        item.setItemMeta(meta); inv.setItem(slot, item);
    }
    private String sortLabel(String sort) {
        return switch (sort) { case "price_low" -> "Preis aufsteigend"; case "price_high" -> "Preis absteigend"; case "name" -> "Name"; default -> "Neueste"; };
    }
    private String nextSort(String sort) {
        return switch (sort) { case "newest" -> "price_low"; case "price_low" -> "price_high"; case "price_high" -> "name"; default -> "newest"; };
    }
    private void askSearch(Player player, String kind) {
        pendingSearch.put(player.getUniqueId(), kind);
        player.closeInventory();
        player.sendMessage(color("&eSchreibe deinen Suchbegriff in den Chat. &7Schreibe &cabbrechen &7zum Abbrechen."));
    }
    @EventHandler
    public void onSearchChat(AsyncPlayerChatEvent event) {
        String kind = pendingSearch.remove(event.getPlayer().getUniqueId());
        if (kind == null) return;
        event.setCancelled(true);
        String query = event.getMessage().equalsIgnoreCase("abbrechen") ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            if (kind.equals("ah")) openAuctionHouse(event.getPlayer(), 0, "newest", query);
            else showOrders(event.getPlayer(), 0, "newest", query);
        });
    }

    @EventHandler
    public void onOrdersClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof OrdersHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        if (event.getRawSlot() >= 45) {
            String action = controlAction(event.getCurrentItem());
            if ("page_prev".equals(action)) showOrders(player, Math.max(0, holder.page - 1), holder.sort, holder.query);
            else if ("page_next".equals(action)) showOrders(player, holder.page + 1, holder.sort, holder.query);
            else if ("sort".equals(action)) showOrders(player, 0, nextSort(holder.sort), holder.query);
            else if ("search".equals(action)) askSearch(player, "orders");
            return;
        }
        String id = holder.orders.get(event.getRawSlot());
        if (id != null) fillOrder(player, new String[]{"fill", id});
    }

    private String controlAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                new org.bukkit.NamespacedKey(plugin, "market_action"),
                org.bukkit.persistence.PersistentDataType.STRING, "");
    }

    private int parseAuctionId(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return -1; }
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlot() >= 45) {
            String action = controlAction(event.getCurrentItem());
            if ("page_prev".equals(action)) openAuctionHouse(player, Math.max(0, holder.page - 1), holder.sort, holder.query);
            else if ("page_next".equals(action)) openAuctionHouse(player, holder.page + 1, holder.sort, holder.query);
            else if ("sort".equals(action)) openAuctionHouse(player, 0, nextSort(holder.sort), holder.query);
            else if ("search".equals(action)) askSearch(player, "ah");
            return;
        }
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

    private void showOrders(Player player) { showOrders(player, 0, "newest", ""); }

    private void showOrders(Player player, int page, String sort, String query) {
        ConfigurationSection orders = data.getConfigurationSection("orders");
        OrdersHolder holder = new OrdersHolder();
        holder.page = Math.max(0, page); holder.sort = sort; holder.query = query;
        Inventory inventory = Bukkit.createInventory(holder, 54, color("&6&lSpieler-Aufträge"));
        holder.inventory = inventory;
        List<String> ids = new ArrayList<>();
        if (orders != null) for (String id : orders.getKeys(false)) {
            String path = "orders." + id;
            String materialName = data.getString(path + ".material", "STONE");
            String owner = data.getString(path + ".ownerName", "Spieler");
            if (!(materialName + " " + owner + " " + id).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            ids.add(id);
        }
        Comparator<String> comparator = switch (sort) {
            case "price_low" -> Comparator.comparingDouble(id -> data.getDouble("orders." + id + ".reward"));
            case "price_high" -> Comparator.comparingDouble((String id) -> data.getDouble("orders." + id + ".reward")).reversed();
            case "name" -> Comparator.comparing(id -> data.getString("orders." + id + ".material", ""));
            default -> Comparator.comparingInt(this::parseAuctionId).reversed();
        };
        ids.sort(comparator);
        int pages = Math.max(1, (ids.size() + 44) / 45);
        holder.page = Math.min(holder.page, pages - 1);
        int start = holder.page * 45;
        for (int index = start; index < Math.min(ids.size(), start + 45); index++) {
            String id = ids.get(index); String path = "orders." + id;
            String owner = data.getString(path + ".ownerName", "Spieler");
            Material material = Material.matchMaterial(data.getString(path + ".material", "STONE"));
            if (material == null || !material.isItem()) material = Material.PAPER;
            int amount = data.getInt(path + ".amount"); double reward = data.getDouble(path + ".reward");
            ItemStack display = new ItemStack(material, Math.max(1, Math.min(64, amount)));
            ItemMeta meta = display.getItemMeta();
            meta.setDisplayName(color("&e#" + id + " &f" + amount + "x " + material.name()));
            meta.setLore(List.of(color("&7Von: &f" + owner), color("&7Belohnung: &6" + reward + " Coins"), color("&aKlicken zum Erfüllen")));
            display.setItemMeta(meta);
            int slot = index - start; inventory.setItem(slot, display); holder.orders.put(slot, id);
        }
        addControl(inventory, 45, Material.ARROW, "&eVorherige Seite", "page_prev");
        addControl(inventory, 49, Material.HOPPER, "&bSortierung: " + sortLabel(sort), "sort");
        addControl(inventory, 50, Material.COMPASS, "&aSuche: " + (query.isEmpty() ? "Alle" : query), "search");
        addControl(inventory, 53, Material.ARROW, "&eNächste Seite (" + (holder.page + 1) + "/" + pages + ")", "page_next");
        if (ids.isEmpty()) player.sendMessage(color("&7Keine passenden Aufträge gefunden."));
        player.openInventory(inventory);
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
        private Inventory inventory; private int page; private String sort = "newest"; private String query = "";
        private final Map<Integer, String> listings = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class OrdersHolder implements InventoryHolder {
        private Inventory inventory; private int page; private String sort = "newest"; private String query = "";
        private final Map<Integer, String> orders = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
}
