package dev.codex.servertoolkit;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.dialog.ClickCallback;
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

        if (plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                FloodgateApi api = FloodgateApi.getInstance();
                if (api != null && api.isFloodgatePlayer(player.getUniqueId())) {
                    openBedrockMenu(api, player);
                    return;
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Floodgate-Formular konnte nicht geöffnet werden: " + exception.getMessage());
            }
        }

        openJavaMenu(player);
    }

    private void openBedrockMenu(FloodgateApi api, Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6AllInOneMC")
                .content("§7Wähle einen Bereich aus.");

        addBedrockButton(form, player, "§aSpawn", "spawn");
        addBedrockButton(form, player, "§bZufalls-Teleport", "rtp");
        addBedrockButton(form, player, "§6Shop", "shop");
        addBedrockButton(form, player, "§eVerkaufen", "sell hand");
        addBedrockButton(form, player, "§dClans", "clan");
        addBedrockButton(form, player, "§cMace-Duelle", "mace");
        addBedrockButton(form, player, "§fRänge", "ranks");
        addBedrockButton(form, player, "§9Server wechseln", "servers");
        addBedrockButton(form, player, "§aHomes", "homes");
        addBedrockButton(form, player, "§bVoice-Chat", "voice");

        api.sendForm(player.getUniqueId(), form);
    }

    private void addBedrockButton(SimpleForm.Builder form, Player player, String label, String command) {
        form.button(label, response -> player.performCommand(command));
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
