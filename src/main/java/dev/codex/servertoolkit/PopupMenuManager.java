package dev.codex.servertoolkit;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;

public final class PopupMenuManager {
    private final JavaPlugin plugin;

    public PopupMenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieses Menü kann nur ein Spieler öffnen.");
            return;
        }
        if (!player.hasPermission("servertoolkit.menu.open")) {
            player.sendMessage("§cDu darfst das Menü nicht öffnen.");
            return;
        }
        List<MenuOption> options = new ArrayList<>();
        addMainOption(player, options, "Spawn", "Zum Server-Spawn", "spawn", "spawn");
        addMainOption(player, options, "Zufalls-Teleport", "RTP-Menü öffnen", "rtp", "rtp");
        addMainOption(player, options, "Shop", "Server-Shop öffnen", "shop", "shop");
        addMainOption(player, options, "Verkaufen", "Verkaufsmenü öffnen", "sell hand", "sell");
        addMainOption(player, options, "Clans", "Clan-Menü öffnen", "clan", "clan");
        addMainOption(player, options, "Kits", "Kit-Menü öffnen", "kit", "kit");
        addMainOption(player, options, "Ränge", "Rangverwaltung öffnen", "ranks", "ranks");
        addMainOption(player, options, "Auktionshaus", "Auktionshaus öffnen", "ah", "ah");
        addMainOption(player, options, "Aufträge", "Spieler-Aufträge öffnen", "orders", "orders");
        addMainOption(player, options, "Mace-Duelle", "Mace-Menü öffnen", "mace", "mace");
        addMainOption(player, options, "Server wechseln", "Serverauswahl öffnen", "servers", "servers");
        addMainOption(player, options, "Homes", "Deine Homes anzeigen", "homes", "homes");
        addMainOption(player, options, "Voice-Chat", "Voice-Chat-Hilfe öffnen", "voice", "voice");
        addMainOption(player, options, "Kopfgeld", "Kopfgeld anzeigen", "bounty", "bounty");
        addMainOption(player, options, "Spielzeit", "Spielzeit anzeigen", "playtime", "playtime");
        showMenu(player, "AllInOneMC – Hauptmenü", "Wähle einen Bereich aus.", options);
    }

    private void addMainOption(Player player, List<MenuOption> options, String label,
                               String description, String command, String permissionSuffix) {
        String permission = "servertoolkit.menu." + permissionSuffix;
        if (!player.hasPermission(permission)) return;
        options.add(new MenuOption(label, description, () -> {
            if (player.hasPermission(permission)) player.performCommand(command);
            else player.sendMessage("§cDafür hast du keine Berechtigung.");
        }));
    }

    public void showMenu(Player player, String title, String description, List<MenuOption> options) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                FloodgateApi api = FloodgateApi.getInstance();
                if (api != null && api.isFloodgatePlayer(player.getUniqueId())) {
                    SimpleForm.Builder form = SimpleForm.builder().title(title).content(description);
                    for (MenuOption option : options) form.button(option.label());
                    SimpleForm built = form.validResultHandler(response -> {
                        int selected = response.clickedButtonId();
                        if (selected >= 0 && selected < options.size()) options.get(selected).action().run();
                    }).build();
                    api.sendForm(player.getUniqueId(), built);
                    return;
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Floodgate-Formular konnte nicht geöffnet werden: " + exception.getMessage());
            }
        }

        List<ActionButton> actions = new ArrayList<>();
        for (MenuOption option : options) {
            actions.add(ActionButton.create(Component.text(option.label()),
                    Component.text(option.description()), 180,
                    DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player target && target.getUniqueId().equals(player.getUniqueId())) {
                            option.action().run();
                        }
                    }, ClickCallback.Options.builder().uses(1).build())));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title)).build())
                .type(DialogType.multiAction(actions, null, 2)));
        player.showDialog(dialog);
    }

    public record MenuOption(String label, String description, Runnable action) {}

    private void openBedrockMenu(FloodgateApi api, Player player) {
        SimpleForm form = SimpleForm.builder()
                .title("§l§6AllInOneMC")
                .content("§7Wähle einen Bereich aus.")
                .button("§aSpawn")
                .button("§bZufalls-Teleport")
                .button("§6Shop")
                .button("§eVerkaufen")
                .button("§dClans")
                .button("§cMace-Duelle")
                .button("§fRänge")
                .button("§9Server wechseln")
                .button("§aHomes")
                .button("§bVoice-Chat")
                .validResultHandler(response -> {
                    String[] commands = {"spawn", "rtp", "shop", "sell hand", "clan", "mace", "ranks", "servers", "homes", "voice"};
                    int selected = response.clickedButtonId();
                    if (selected >= 0 && selected < commands.length) {
                        player.performCommand(commands[selected]);
                    }
                })
                .build();
        api.sendForm(player.getUniqueId(), form);
    }

    private void openJavaMenu(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        addJavaButton(actions, "Spawn", "spawn");
        addJavaButton(actions, "Zufalls-Teleport", "rtp");
        addJavaButton(actions, "Shop", "shop");
        addJavaButton(actions, "Verkaufen", "sell hand");
        addJavaButton(actions, "Clans", "clan");
        addJavaButton(actions, "Mace-Duelle", "mace");
        addJavaButton(actions, "Ränge", "ranks");
        addJavaButton(actions, "Server wechseln", "servers");
        addJavaButton(actions, "Homes", "homes");
        addJavaButton(actions, "Voice-Chat", "voice");

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("AllInOneMC – Menü"))
                        .build())
                .type(DialogType.multiAction(actions, null, 2)));

        player.showDialog(dialog);
    }

    private void addJavaButton(List<ActionButton> actions, String label, String command) {
        actions.add(ActionButton.create(
                Component.text(label),
                Component.text("Öffnet: /" + command),
                180,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player player) {
                        player.performCommand(command);
                    }
                }, ClickCallback.Options.builder().uses(1).build())
        ));
    }
}
