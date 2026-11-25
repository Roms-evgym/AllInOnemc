package dev.codex.servertoolkit;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Set;

public final class ServerToolkitBootstrap implements PluginBootstrap {
    public static final TypedKey<Dialog> MENU_DIALOG = DialogKeys.create(Key.key("allinonemc:main_menu"));

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.DIALOG.compose(),
                event -> event.registry().register(MENU_DIALOG, builder -> builder
                        .base(DialogBase.builder(Component.text("AllInOneMC – Hauptmenü"))
                                .externalTitle(Component.text("AllInOneMC"))
                                .build())
                        .type(DialogType.multiAction(List.of(
                                button("Spawn", "Zum Server-Spawn", "menu_spawn"),
                                button("Zufalls-Teleport", "RTP öffnen", "menu_rtp"),
                                button("Shop", "Server-Shop öffnen", "menu_shop"),
                                button("Verkaufen", "Verkaufsmenü öffnen", "menu_sell"),
                                button("Clans", "Clan-Menü öffnen", "menu_clan"),
                                button("Kits", "Kit-Menü öffnen", "menu_kit"),
                                button("Ränge", "Rangverwaltung öffnen", "menu_ranks"),
                                button("Auktionshaus", "Auktionshaus öffnen", "menu_ah"),
                                button("Aufträge", "Spieler-Aufträge öffnen", "menu_orders"),
                                button("Mace-Duelle", "Mace-Menü öffnen", "menu_mace"),
                                button("Server wechseln", "Serverauswahl öffnen", "menu_servers"),
                                button("Homes", "Deine Homes anzeigen", "menu_homes"),
                                button("Voice-Chat", "Voice-Chat-Hilfe öffnen", "menu_voice"),
                                button("Kopfgeld", "Kopfgeld-Menü öffnen", "menu_bounty"),
                                button("Spielzeit", "Spielzeit anzeigen", "menu_playtime")
                        ), null, 2)))
        );

        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG),
                event -> {
                    event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, Set.of(MENU_DIALOG));
                    event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, Set.of(MENU_DIALOG));
                }
        );
    }

    private static ActionButton button(String label, String description, String action) {
        return ActionButton.create(
                Component.text(label),
                Component.text(description),
                180,
                DialogAction.customClick(Key.key("allinonemc:" + action), null)
        );
    }
}
