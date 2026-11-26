package dev.codex.servertoolkit;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lobby-only account authentication. Passwords are stored as PBKDF2 hashes;
 * authentication is session-scoped and is cleared by the proxy on disconnect.
 */
public final class AuthManager implements Listener {
    private static final String CHANNEL = "allinone:auth";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MILLIS = 60_000L;

    private final ServerToolkit plugin;
    private final File file;
    private final YamlConfiguration data;
    private final SecureRandom random = new SecureRandom();
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    private final Map<UUID, Long> lockedUntil = new HashMap<>();

    public AuthManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auth.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void handle(Player player, String label, String[] args) {
        if (!plugin.isLobbyMode()) {
            player.sendMessage(color("&cLogin-Befehle sind nur in der Lobby verfügbar."));
            return;
        }
        switch (label.toLowerCase(java.util.Locale.ROOT)) {
            case "register" -> register(player, args);
            case "login" -> login(player, args);
            case "logout" -> logout(player);
            default -> player.sendMessage(color("&cUnbekannter Auth-Befehl."));
        }
    }

    private void register(Player player, String[] args) {
        if (isAuthenticated(player)) {
            player.sendMessage(color("&aDu bist bereits angemeldet."));
            return;
        }
        if (hasAccount(player)) {
            player.sendMessage(color("&cDu hast bereits ein Konto. Nutze &f/login <Passwort>&c."));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(color("&eNutzung: /register <Passwort> <Passwort-wiederholen>"));
            return;
        }
        if (args[0].length() < 10 || args[0].length() > 128) {
            player.sendMessage(color("&cDas Passwort muss 10 bis 128 Zeichen lang sein."));
            return;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(color("&cDie Passwörter stimmen nicht überein."));
            return;
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = hash(args[0].toCharArray(), salt);
        if (hash == null) {
            player.sendMessage(color("&cDie Passwortverschlüsselung ist fehlgeschlagen. Bitte melde dich beim Team."));
            return;
        }
        String path = accountPath(player);
        data.set(path + ".name", player.getName());
        data.set(path + ".salt", Base64.getEncoder().encodeToString(salt));
        data.set(path + ".hash", Base64.getEncoder().encodeToString(hash));
        data.set(path + ".algorithm", "PBKDF2WithHmacSHA256");
        data.set(path + ".iterations", ITERATIONS);
        save();
        completeLogin(player);
        player.sendMessage(color("&aDein Konto wurde erstellt und du bist jetzt angemeldet."));
    }

    private void login(Player player, String[] args) {
        if (isAuthenticated(player)) {
            player.sendMessage(color("&aDu bist bereits angemeldet."));
            return;
        }
        if (!hasAccount(player)) {
            player.sendMessage(color("&cDu hast noch kein Konto. Nutze &f/register <Passwort> <Passwort-wiederholen>&c."));
            return;
        }
        long lock = lockedUntil.getOrDefault(player.getUniqueId(), 0L);
        if (lock > System.currentTimeMillis()) {
            player.sendMessage(color("&cZu viele Fehlversuche. Warte " + ((lock - System.currentTimeMillis() + 999) / 1000) + " Sekunden."));
            return;
        }
        if (args.length != 1) {
            player.sendMessage(color("&eNutzung: /login <Passwort>"));
            return;
        }
        String path = accountPath(player);
        try {
            byte[] salt = Base64.getDecoder().decode(data.getString(path + ".salt", ""));
            byte[] expected = Base64.getDecoder().decode(data.getString(path + ".hash", ""));
            byte[] actual = hash(args[0].toCharArray(), salt);
            if (actual != null && MessageDigest.isEqual(expected, actual)) {
                failedAttempts.remove(player.getUniqueId());
                lockedUntil.remove(player.getUniqueId());
                completeLogin(player);
                player.sendMessage(color("&aErfolgreich angemeldet. Willkommen zurück!"));
                return;
            }
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid auth data for " + player.getUniqueId());
        }
        int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
        if (attempts >= MAX_ATTEMPTS) {
            failedAttempts.remove(player.getUniqueId());
            lockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + LOCK_MILLIS);
            player.sendMessage(color("&cZu viele falsche Passwörter. Anmeldung für 60 Sekunden gesperrt."));
        } else {
            player.sendMessage(color("&cFalsches Passwort. Versuche: " + attempts + "/" + MAX_ATTEMPTS));
        }
    }

    private void logout(Player player) {
        if (!plugin.isLobbyMode()) {
            player.sendMessage(color("&cNutze /logout in der Lobby."));
            return;
        }
        authenticated.remove(player.getUniqueId());
        sendProxyState(player, "LOGOUT");
        player.sendMessage(color("&eDu wurdest abgemeldet. Andere Server sind wieder gesperrt."));
    }

    private void completeLogin(Player player) {
        authenticated.add(player.getUniqueId());
        sendProxyState(player, "AUTHENTICATED");
    }

    private void sendProxyState(Player player, String operation) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeUTF(operation);
            out.writeUTF(player.getUniqueId().toString());
            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not send auth state to proxy: " + ex.getMessage());
        }
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    private boolean hasAccount(Player player) {
        return data.contains(accountPath(player) + ".hash");
    }

    private String accountPath(Player player) {
        return "accounts." + player.getUniqueId();
    }

    private byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            plugin.getLogger().severe("PBKDF2 is unavailable: " + ex.getMessage());
            return null;
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().severe("Could not save auth.yml: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isLobbyMode()) return;
        Player player = event.getPlayer();
        authenticated.remove(player.getUniqueId());
        failedAttempts.remove(player.getUniqueId());
        lockedUntil.remove(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (hasAccount(player)) {
                player.sendMessage(color("&6&lAllInOne Auth &8» &eBitte melde dich mit &f/login <Passwort> &ean."));
            } else {
                player.sendMessage(color("&6&lAllInOne Auth &8» &eErstelle dein Konto mit &f/register <Passwort> <Passwort-wiederholen>&e."));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        authenticated.remove(id);
        failedAttempts.remove(id);
        lockedUntil.remove(id);
    }

    private boolean blocked(Player player) {
        return plugin.isLobbyMode() && !isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!blocked(event.getPlayer()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blocked(event.getPlayer())) return;
        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (!(message.startsWith("/login ") || message.equals("/login")
                || message.startsWith("/register ") || message.equals("/register"))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cBitte melde dich zuerst mit /login oder /register an."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cBitte melde dich zuerst an."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && blocked(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && blocked(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { if (blocked(event.getPlayer())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && blocked(player)) event.setCancelled(true);
    }

    private String color(String input) { return ChatColor.translateAlternateColorCodes('&', input); }
}
