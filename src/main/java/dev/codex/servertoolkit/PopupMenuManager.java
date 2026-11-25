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
