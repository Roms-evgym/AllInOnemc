# ServerToolkit

Ein Paper-Plugin für ein Survival-SMP mit Mace-PvP. Es läuft auf Paper 26.3 und Java 25.

## Funktionen

- Survival: `/spawn`, `/setspawn`, `/rtp`
- Proxy-Lobby: `/hub` und `/lobby` senden den BungeeCord-kompatiblen Connect zur Lobby (`proxy.lobby-server`, Standard `Lobby`).
- Lobby-Modus: Adventure, Spawn-Teleport, Schutz vor Bauen/Schaden/Drops/Hunger/Item-Aufheben, Rettung aus dem Void sowie Kompass- und `/servers`-Auswahlmenü. Zielnamen und Symbole stehen unter `lobby.selector.servers` in `config.yml`.
- Mehrere Homes: `/sethome [Name]`, `/home [Name]`, `/homes`, `/delhome <Name>`; Home-Limits sind pro Rang einstellbar.
- Clans: `/clan create|invite|accept|leave|disband|info|chat|top`
- Mace: `/mace duel`, `/mace accept`, `/mace deny`, `/mace stats`, `/mace top`
- Mace-Events mit Anmeldung und einfachem K.-o.-Turnier: `/mace event open|join|close|start|status|cancel`
- Moderation: `/fly`, `/god`, `/invsee`, `/ecsee`
- Ban-Presets: `/banpreset gui` verwaltet Presets in einer GUI; bannen mit `/ban <Name> <Preset>`, Hammer mit `/banhammer <Preset>`.
- Vanish: `/vanish`; Rangsichtbarkeit mit `/vanish visibility <Zuschauer-Rang> <Vanish-Rang|*> <on|off>`.
- Komfort: `/echest` öffnet deine eigene Enderchest; `/trash` ist ein Papierkorb
- Sitzen: `/sit` zum Hinsetzen und Aufstehen, ohne GSit-Abhängigkeit
- Admin-Weltwerkzeuge: `/fakebase` platziert eine kleine Täuschungsbasis; `/fakore <erz> [anzahl]` setzt eine Erzader unter dem Admin (1–64 Blöcke)
- Ränge: `/rank gui` öffnet die Rangverwaltung. Dort lassen sich Ränge erstellen, Prefix/Farbe und Anzeigename ändern, Gewicht und Home-Limit setzen, Berechtigungen umschalten und Ränge an Online-Spieler vergeben. Definitionen stehen in `config.yml`, Zuweisungen in `plugins/ServerToolkit/ranks.yml`.
- Voice-Menü: `/voice` zeigt die Simple-Voice-Chat-Hilfe; `/voice color <&Farbcode>` ändert die Menüfarbe. Die Client-GUI öffnet sich standardmäßig mit `V`.
- Inventar-Menüs verwenden eine gemeinsame AllInOne-Survival-Menüoptik mit dunklem Rand und roten Akzenten.
- Eigenes Economy-System: `/money`, `/money pay <Spieler> <Betrag>`, `/pay <Spieler> <Betrag>`; Admins nutzen `/eco give|take|set <Spieler> <Betrag>`. Guthaben liegt in `plugins/ServerToolkit/economy.yml`.
- Spieler-Markt nutzt die eigene Economy: `/ah`, `/ah sell <Preis>`, `/ah cancel <ID>`
- Aufträge mit Treuhand-Belohnung: `/order create <Material> <Menge> <Belohnung>`, `/order fill <ID>`, `/order cancel <ID>`, `/orders`

Rangpräfixe erscheinen im Chat-Anzeigenamen und in der TAB-Spielerliste. Vanish synchronisiert die Sichtbarkeit fortlaufend mit Paper `hidePlayer`, damit versteckte Spieler auch bei TAB-Plugins ausgeblendet bleiben. Vanilla-Join- und Leave-Meldungen sind deaktiviert. Die Sidebar zeigt das Guthaben der eingebauten Economy an und aktualisiert es alle fünf Sekunden. Simple Voice Chat stellt seine GUI clientseitig bereit; `/voice` erklärt, wie man sie öffnet.

## Einrichtung

1. Mit `mvn package -DskipTests` bauen. Die JAR liegt unter `target/servertoolkit-1.13.0.jar`.
2. JAR nach `plugins/` auf dem Paper-Backend laden und den Server neu starten.
3. Als Operator den Survival-Spawn mit `/setspawn` setzen.
4. Die zwei Mace-Arena-Punkte mit `/mace setarena a` und `/mace setarena b` setzen.
5. Eigene Ränge mit `/rank create <id> <prefix>` anlegen und mit `/rank set <Spieler> <id>` vergeben. Rechte werden über `/rank perm <rang> add|remove <node>` und Farben über `/rank color <rang> <&code>` gesetzt. Für Rangverwaltung ist `servertoolkit.rank.manage` nötig.
6. Vanish für Ränge über `/rank perm <rang> add servertoolkit.vanish` erlauben. Die Sichtbarkeitsmatrix wird unter `plugins/ServerToolkit/moderation.yml` gespeichert.

Crafty-Einträge vom Typ BungeeCord sind Proxys und können dieses Paper-Plugin nicht ausführen. Für Lobby-Schutz und Lobby-Spawn muss ein eigener Paper-Lobby-Backend-Server verwendet werden.
