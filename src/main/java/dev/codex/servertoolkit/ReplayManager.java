package dev.codex.servertoolkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight server-side replay recorder. Captures player movement/equipment and
 * relevant world events at 2 samples per second for the last ten minutes.
 */
public final class ReplayManager implements Listener {
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final long SAMPLE_TICKS = 10L;
    private static final int MAX_EVENTS = 3000;

    private final ServerToolkit plugin;
    private final File replayFolder;
    private final Map<UUID, Deque<Frame>> frames = new HashMap<>();
    private final Map<UUID, Deque<ReplayEvent>> events = new HashMap<>();
    private final Map<UUID, Playback> playbacks = new HashMap<>();

    public ReplayManager(ServerToolkit plugin) {
        this.plugin = plugin;
        this.replayFolder = new File(plugin.getDataFolder(), "replays");
        if (!replayFolder.exists() && !replayFolder.mkdirs()) {
            plugin.getLogger().warning("Replay-Ordner konnte nicht erstellt werden.");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sampleOnlinePlayers, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    private void sampleOnlinePlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;
            Deque<Frame> track = frames.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            track.addLast(Frame.capture(player, now));
            trimFrames(track, now);
        }
        trimAllEvents(now);
    }

    private void trimFrames(Deque<Frame> track, long now) {
        while (!track.isEmpty() && now - track.peekFirst().time() > WINDOW_MS) track.removeFirst();
        while (track.size() > 1250) track.removeFirst();
    }

    private void trimAllEvents(long now) {
        for (Deque<ReplayEvent> track : events.values()) {
            while (!track.isEmpty() && now - track.peekFirst().time() > WINDOW_MS) track.removeFirst();
            while (track.size() > MAX_EVENTS) track.removeFirst();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        recordEvent(event.getPlayer(), "BLOCK_BREAK",
                event.getBlock().getType().name(), event.getBlock().getLocation());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        recordEvent(event.getPlayer(), "BLOCK_PLACE",
                event.getBlockPlaced().getType().name(), event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            recordEvent(player, "DAMAGE", String.format(Locale.US, "%.1f", event.getFinalDamage()), player.getLocation());
        }
    }

    private void recordEvent(Player player, String type, String detail, Location location) {
        long now = System.currentTimeMillis();
        Deque<ReplayEvent> track = events.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        track.addLast(new ReplayEvent(now, type, detail, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ()));
        while (track.size() > MAX_EVENTS) track.removeFirst();
    }

    public void handleReport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("atlas.report")) {
            sender.sendMessage("§cDafür hast du keine Rechte (atlas.report).");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage("§cVerwendung: /report <Spieler> [Grund]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cDer Spieler muss für einen Report aktuell online sein.");
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Frame> track = frames.get(target.getUniqueId());
        if (track == null || track.isEmpty()) {
            sender.sendMessage("§cFür diesen Spieler gibt es noch keine Replay-Daten. Warte mindestens 10 Sekunden.");
            return;
        }
        List<Frame> captured = track.stream().filter(frame -> now - frame.time() <= WINDOW_MS).toList();
        if (captured.isEmpty()) {
            sender.sendMessage("§cKeine Replay-Daten aus den letzten 10 Minuten vorhanden.");
            return;
        }
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kein Grund angegeben";
        if (reason.length() > 180) reason = reason.substring(0, 180);
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(now))
                + "-" + target.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        File file = new File(replayFolder, id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("player", target.getName());
        yaml.set("uuid", target.getUniqueId().toString());
        yaml.set("reporter", sender.getName());
        yaml.set("reason", reason);
        yaml.set("created", now);
        yaml.set("duration-ms", captured.get(captured.size() - 1).time() - captured.get(0).time());
        List<Map<String, Object>> frameData = new ArrayList<>();
        long base = captured.get(0).time();
        for (Frame frame : captured) frameData.add(frame.toMap(base));
        yaml.set("frames", frameData);
        Deque<ReplayEvent> eventTrack = events.get(target.getUniqueId());
        List<Map<String, Object>> eventData = new ArrayList<>();
        if (eventTrack != null) {
            for (ReplayEvent event : eventTrack) {
                if (event.time() >= base && event.time() <= now) eventData.add(event.toMap(base));
            }
        }
        yaml.set("events", eventData);
        try {
            yaml.save(file);
            sender.sendMessage("§aReport gespeichert: §e" + id);
            sender.sendMessage("§7Spieler: §f" + target.getName() + " §8| §7Frames: §f" + captured.size()
                    + " §8| §7Dauer: §f" + formatDuration(captured.get(captured.size() - 1).time() - base));
            sender.sendMessage("§7Ansehen: §f/atlas " + id);
        } catch (IOException ex) {
            plugin.getLogger().severe("Replay konnte nicht gespeichert werden: " + ex.getMessage());
            sender.sendMessage("§cReplay konnte nicht gespeichert werden. Siehe Serverkonsole.");
        }
    }

    public void handleAtlas(Player viewer, String[] args) {
        if (!viewer.hasPermission("atlas.view")) {
            viewer.sendMessage("§cDafür hast du keine Rechte (atlas.view).");
            return;
        }
        if (args.length == 0) {
            listReplays(viewer);
            return;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            stopPlayback(viewer, true);
            return;
        }
        if (args[0].equalsIgnoreCase("help")) {
            viewer.sendMessage("§e/atlas §7– Reports auflisten");
            viewer.sendMessage("§e/atlas <ID> §7– Replay ansehen");
            viewer.sendMessage("§e/atlas stop §7– Replay beenden");
            return;
        }
        startPlayback(viewer, args[0]);
    }

    private void listReplays(Player viewer) {
        File[] files = replayFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            viewer.sendMessage("§7Es wurden noch keine Reports gespeichert.");
            return;
        }
        java.util.Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        viewer.sendMessage("§6§lAtlas §8– §7Gespeicherte Reports:");
        int shown = 0;
        for (File file : files) {
            if (shown++ >= 15) break;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            viewer.sendMessage("§e" + yaml.getString("id", file.getName().replace(".yml", ""))
                    + " §8| §f" + yaml.getString("player", "Unbekannt")
                    + " §8| §7" + yaml.getString("reason", "Kein Grund"));
        }
        viewer.sendMessage("§7Replay starten: §f/atlas <ID>");
    }

    private void startPlayback(Player viewer, String id) {
        stopPlayback(viewer, false);
        if (!id.matches("[0-9]{8}-[0-9]{6}-[a-z0-9_-]{1,32}")) {
            viewer.sendMessage("§cUngültige Replay-ID. Nutze /atlas für die Liste.");
            return;
        }
        File file = new File(replayFolder, id + ".yml");
        if (!file.isFile()) {
            viewer.sendMessage("§cReplay nicht gefunden: " + id);
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> rawFrames = yaml.getList("frames");
        if (rawFrames == null || rawFrames.size() < 2) {
            viewer.sendMessage("§cDieses Replay enthält zu wenige Bewegungsdaten.");
            return;
        }
        List<Frame> replayFrames = new ArrayList<>();
        for (Object raw : rawFrames) {
            if (!(raw instanceof Map<?, ?> map)) continue;
            Frame frame = Frame.fromMap(map);
            if (frame != null && Bukkit.getWorld(frame.world()) != null) replayFrames.add(frame);
        }
        if (replayFrames.size() < 2) {
            viewer.sendMessage("§cDie Replay-Welt ist nicht geladen oder die Daten sind unvollständig.");
            return;
        }
        replayFrames.sort(Comparator.comparingLong(Frame::time));
        List<ReplayEvent> replayEvents = new ArrayList<>();
        for (Object raw : yaml.getMapList("events")) {
            ReplayEvent event = ReplayEvent.fromMap(raw);
            if (event != null) replayEvents.add(event);
        }
        Frame first = replayFrames.get(0);
        World world = Bukkit.getWorld(first.world());
        Location start = first.location(world);
        Location returnLocation = viewer.getLocation().clone();
        GameMode previousMode = viewer.getGameMode();
        ArmorStand ghost = world.spawn(start, ArmorStand.class, stand -> {
            stand.setCustomName("§c" + yaml.getString("player", "Reported player"));
            stand.setCustomNameVisible(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCanPickupItems(false);
            stand.setPersistent(false);
            applyEquipment(stand, first);
        });
        viewer.setGameMode(GameMode.SPECTATOR);
        viewer.teleport(start);
        viewer.setSpectatorTarget(ghost);
        Playback playback = new Playback(viewer, ghost, returnLocation, previousMode,
                replayFrames, replayEvents, yaml.getString("player", "Spieler"), System.currentTimeMillis());
        playbacks.put(viewer.getUniqueId(), playback);
        playback.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickPlayback(playback), 1L, 1L);
        viewer.sendMessage("§aAtlas-Replay gestartet: §e" + id);
        viewer.sendMessage("§7Spieler: §f" + playback.playerName + " §8| §7/atlas stop zum Beenden");
    }

    private void tickPlayback(Playback playback) {
        if (!playback.viewer.isOnline() || !playback.ghost.isValid()) {
            stopPlayback(playback.viewer, false);
            return;
        }
        long elapsed = System.currentTimeMillis() - playback.startedAt;
        int index = 0;
        while (index + 1 < playback.frames.size() && playback.frames.get(index + 1).time() <= elapsed) index++;
        Frame frame = playback.frames.get(index);
        World world = Bukkit.getWorld(frame.world());
        if (world == null) {
            stopPlayback(playback.viewer, true);
            return;
        }
        Location location = frame.location(world);
        playback.ghost.teleport(location);
        if (playback.appliedFrame != index) {
            applyEquipment(playback.ghost, frame);
            playback.appliedFrame = index;
        }
        while (playback.nextEvent < playback.events.size()
                && playback.events.get(playback.nextEvent).time() <= elapsed) {
            ReplayEvent event = playback.events.get(playback.nextEvent++);
            String description = switch (event.type()) {
                case "BLOCK_BREAK" -> "§cBlock abgebaut: §f" + event.detail();
                case "BLOCK_PLACE" -> "§aBlock platziert: §f" + event.detail();
                case "DAMAGE" -> "§cSchaden erhalten: §f" + event.detail();
                default -> "§7" + event.type() + ": " + event.detail();
            };
            playback.viewer.sendActionBar(description);
        }
        if (elapsed >= playback.frames.get(playback.frames.size() - 1).time()) {
            playback.viewer.sendMessage("§aReplay beendet.");
            stopPlayback(playback.viewer, true);
        }
    }

    private void stopPlayback(Player viewer, boolean notify) {
        Playback playback = playbacks.remove(viewer.getUniqueId());
        if (playback == null) {
            if (notify) viewer.sendMessage("§7Du siehst gerade kein Replay.");
            return;
        }
        if (playback.task != null) playback.task.cancel();
        if (playback.ghost.isValid()) playback.ghost.remove();
        if (viewer.isOnline()) {
            viewer.setSpectatorTarget(null);
            viewer.setGameMode(playback.previousMode);
            viewer.teleport(playback.returnLocation);
            if (notify) viewer.sendMessage("§eAtlas-Replay beendet. Du bist an deinen Ausgangsort zurückgekehrt.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Playback playback = playbacks.get(event.getPlayer().getUniqueId());
        if (playback != null) stopPlayback(event.getPlayer(), false);
    }

    private static void applyEquipment(ArmorStand stand, Frame frame) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;
        equipment.setItemInMainHand(cloneOrAir(frame.mainHand()));
        equipment.setHelmet(cloneOrAir(frame.helmet()));
        equipment.setChestplate(cloneOrAir(frame.chestplate()));
        equipment.setLeggings(cloneOrAir(frame.leggings()));
        equipment.setBoots(cloneOrAir(frame.boots()));
    }

    private static ItemStack cloneOrAir(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item.clone();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private record Frame(long time, String world, double x, double y, double z, float yaw, float pitch,
                         double health, ItemStack mainHand, ItemStack helmet, ItemStack chestplate,
                         ItemStack leggings, ItemStack boots) {
        static Frame capture(Player player, long time) {
            Location location = player.getLocation();
            var inventory = player.getInventory();
            return new Frame(time, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), player.getHealth(),
                    inventory.getItemInMainHand().clone(), cloneOrNull(inventory.getHelmet()),
                    cloneOrNull(inventory.getChestplate()), cloneOrNull(inventory.getLeggings()),
                    cloneOrNull(inventory.getBoots()));
        }
        private static ItemStack cloneOrNull(ItemStack item) { return item == null ? null : item.clone(); }
        Location location(World world) { return new Location(world, x, y, z, yaw, pitch); }
        Map<String, Object> toMap(long base) {
            Map<String, Object> map = new HashMap<>();
            map.put("time", time - base); map.put("world", world); map.put("x", x); map.put("y", y); map.put("z", z);
            map.put("yaw", yaw); map.put("pitch", pitch); map.put("health", health);
            map.put("main", mainHand); map.put("helmet", helmet); map.put("chestplate", chestplate);
            map.put("leggings", leggings); map.put("boots", boots);
            return map;
        }
        static Frame fromMap(Map<?, ?> map) {
            try {
                return new Frame(number(map, "time").longValue(), String.valueOf(map.get("world")),
                        number(map, "x").doubleValue(), number(map, "y").doubleValue(), number(map, "z").doubleValue(),
                        number(map, "yaw").floatValue(), number(map, "pitch").floatValue(),
                        number(map, "health").doubleValue(), item(map.get("main")), item(map.get("helmet")),
                        item(map.get("chestplate")), item(map.get("leggings")), item(map.get("boots")));
            } catch (Exception ex) { return null; }
        }
    }

    private record ReplayEvent(long time, String type, String detail, String world, double x, double y, double z) {
        Map<String, Object> toMap(long base) {
            Map<String, Object> map = new HashMap<>();
            map.put("time", time - base); map.put("type", type); map.put("detail", detail);
            map.put("world", world); map.put("x", x); map.put("y", y); map.put("z", z);
            return map;
        }
        static ReplayEvent fromMap(Map<?, ?> map) {
            try {
                return new ReplayEvent(number(map, "time").longValue(), String.valueOf(map.get("type")),
                        String.valueOf(map.get("detail")), String.valueOf(map.get("world")),
                        number(map, "x").doubleValue(), number(map, "y").doubleValue(), number(map, "z").doubleValue());
            } catch (Exception ex) { return null; }
        }
    }

    private static Number number(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing " + key);
        return number;
    }
    private static ItemStack item(Object value) { return value instanceof ItemStack stack ? stack.clone() : null; }

    private static final class Playback {
        final Player viewer;
        final ArmorStand ghost;
        final Location returnLocation;
        final GameMode previousMode;
        final List<Frame> frames;
        final List<ReplayEvent> events;
        final String playerName;
        final long startedAt;
        int appliedFrame = -1;
        int nextEvent;
        BukkitTask task;
        Playback(Player viewer, ArmorStand ghost, Location returnLocation, GameMode previousMode,
                 List<Frame> frames, List<ReplayEvent> events, String playerName, long startedAt) {
            this.viewer = viewer; this.ghost = ghost; this.returnLocation = returnLocation;
            this.previousMode = previousMode; this.frames = frames; this.events = events;
            this.playerName = playerName; this.startedAt = startedAt;
        }
    }
}
