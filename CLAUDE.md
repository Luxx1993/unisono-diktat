# CLAUDE.md – diktat (Diktat-Edition von Dictate Keyboard)

Kontext für Claude Code. Projektinhaber: Luxx1993 (GitHub). Kommunikation auf Deutsch, den Projektinhaber persönlich ansprechen.

## Was das ist

Luxx1993s eigene, anbieterunabhängige Diktier-App für Android, ein Fork von **Dictate Keyboard**
(DevEmperor, Apache 2.0, basiert auf FlorisBoard). Arbeitsbranch: **`diktat-edition`**.

Kernablauf: sprechen → KI räumt den Text auf → Text wird eingefügt. Luxx1993 nutzt die App über den
**schwebenden Button („Wolke“)** neben SwiftKey, mit eigenen API-Keys: aktuell **ElevenLabs Scribe**
(Transkription) und **Claude Haiku 5.5** (Aufräumen). Gerät: Samsung Galaxy S24 (arm64).

## Stand der Diktat-Edition

- **Tastatur und Zubehör sind entfernt** (Oktober 2026): Tastatur-IME, Rechtschreibprüfung, Wear OS, Dictate Cloud, GIF,
  Sticker, Scan, Übersetzung, Erweiterungen, Theme-Engine, Tastatur-Einstellungen. Details, was blieb und warum, und offene
  Punkte: `docs/FUNKTIONEN.md` Abschnitt 13. Den Gradle-Schalter `dictationOnly` gibt es nicht mehr.
- Es bleiben: Diktat-Logik, schwebender Button, Spracheingabe-Methode (für SwiftKeys Mikrofon-Taste),
  System-Erkennungsdienst, Datei transkribieren, Einstellungen, Verlauf, Statistik, Sichern/Wiederherstellen.
- Maßstab für weitere Arbeit: Es bleibt nur, was die Diktierfunktion laut Screens und das Einrichten in Android als
  Eingabe-/Spracheingabe-Methode technisch brauchen. Behaltenes mit Grund in FUNKTIONEN.md Abschnitt 13 notieren.

## Repository

- README, `SECURITY.md` und die Links in der App gelten für diktat und zeigen auf
  `Luxx1993/unisono-diktat` (vorher `DictateKeyboard`). Die Fork-Verbindung zu `DevEmperor/DictateKeyboard` bleibt (Remote `upstream` zum Abgleich).
- Herkunft bleibt genannt: README „Herkunft und Lizenz“, `NOTICE` (eigener Block oben, Originale unverändert), Über-Seite
  „Basiert auf Dictate Keyboard“. Apache 2.0 verlangt das.
- Noch an DevEmperor gebunden: Downloads der Offline- und Smart-Turn-Modelle (`LocalModelCatalog.REL`, Releases von
  Dictate Keyboard). Die Paket-ID ist seit Oktober 2026 `app.diktat` (vorher `net.devemperor.dictate.debug`); Backups der alten App lassen sich weiter wiederherstellen.

## Bauen

```bash
./gradlew :app:assembleDebug          # Debug-APK, Paket app.diktat
```
APK unter `app/build/outputs/apk/debug/`. Debug-Builds sind mit einem Debug-Schlüssel signiert.

**APK veröffentlichen:** Der Workflow `.github/workflows/apk.yml` baut bei jedem Push auf `diktat-edition` oder `ccr-*` (außer reinen Doku-Änderungen) und legt die APK samt `version.json` auf den Zweig `apk` (auch von Hand startbar: Actions › APK › Run workflow). Der feste Link in der README (`…/raw/apk/Dictate-Diktat-arm64.apk`) und der Update-Button in der App zeigen dann auf die neue Version. Nach jeder abgeschlossenen Änderung, die Lukas auf dem S24 testen soll, pushen, den Lauf abwarten und den Link nennen. Signiert wird mit dem festen diktat-Schlüssel: verschlüsselt als `debug.keystore.enc` auf dem Zweig `signing`, Passwort nur im Secret `DIKTAT_SIGNING_PASSWORD`. Zweig und Secret nie löschen, sonst gibt es kein Update über die installierte App mehr (der erste Schlüssel ging im November 2026 mit einem Container verloren). `scripts/publish-apk.sh` ist das Skript, das der Workflow ausführt; lokal nur mit `--dry-run`.

**Google Play:** Vorbereitet in `docs/play-store/` (Anleitung, Datenschutzerklärung, Store-Texte und Formular-Antworten). `scripts/make-upload-key.sh` legt den Upload-Schlüssel an, `scripts/build-play-bundle.sh` baut das signierte `.aab`. Die Debug-APK (Zweig `apk`) und die Play-Version haben verschiedene Signaturen und lassen sich nicht überinstallieren.

## Wichtige Orte im Code

- `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/` – Diktat-Logik (Anbieter, Aufnahme, Overlay)
- `dictate/overlay/` – schwebender Button (Accessibility-Service, Bubble-Controller)
- `dictate/ui/AudioReactiveCloudOrbView.kt` – AGSL-Shader der Wolke (Ebenen wie der Prototyp), `CloudMotion.kt` (Keyframes), `CloudPalette.kt` (5 Paletten)
- `dictate/ui/DiktatGlobe.kt` – Icon-Kugel als Compose-Komponente, Animation D; `DictateWaveOrbView.kt` – dieselbe Kugel als schwebender Button „Welle“ (schwarz/weiß nach Hintergrund, `overlay/BackdropTone.kt`)
- `app/main/` – Oberfläche Entwurf 3: `MainScreen.kt` (4 Tabs, Startbildschirm), `StartTab`, `DictionaryTab`, `StylesTab`, `SettingsTab`, Bausteine `UnisonoComponents.kt`, Icons `UnisonoIcons.kt`
- `app/apptheme/Unisono.kt` – Farben, Schriften, Textstile des Unisono-Looks
- `app/settings/` – Compose-Einstellungen (`HomeScreen.kt` = „Alle Einstellungen“, `dictate/DictateProvidersScreen.kt` …)
- `app/src/main/AndroidManifest.xml` – Launcher-Aliase `dev.patrickgold.florisboard.SettingsLauncherAlias` (weiß) und `…AliasBlack` (schwarz), Splash-Theme `FlorisAppTheme.Splash`
- `app/src/main/res/mipmap-anydpi-v26/app_icon.xml` / `app_icon_black.xml` – adaptives Icon „Welle um die Welt“ (weiß/schwarz), Umschalter in `app/AppIcon.kt`

## Design

Was die App können soll und heute kann: **`docs/FUNKTIONEN.md`** (Abschnitt 0 = Soll laut Screens).
Alle Design-Entscheidungen stehen in **`docs/design/diktat-design.md`**. Das ist die maßgebliche Quelle. Kurz:

- **Oberfläche „Entwurf 3“**: warmer Unisono-Look (Papier `#F4F1EC`, Tinte `#1F1C1A`, Manrope und IBM Plex Mono),
  4 Tabs (Start, Wörterbuch, Stile, Einstellungen), hell und dunkel. HTML-Referenzen in `docs/design/entwurf-3/`.
- **Wolke**: körnige, farbige Glaskugel mit ruhiger Rauch-Animation, 5 Paletten, Standard Lagune.
- **App-Icon „Welle um die Welt“**: Ring plus durchgehende Welle, nur Weiß oder Schwarz, transparenter Hintergrund,
  Umschalter weiß/schwarz; Animation A (Drehen) beim Öffnen, Animation D (Drehen plus gegenläufige Amplitude) in der App.
  Generator und fertige Dateien: `docs/design/icon/` (`python3 diktat_icon.py`).

## Nächste Aufgaben

1. App-Icon ist fertig (Icon, Themen-Icon, Umschalter, Animation A im Splash, Animation D als `DiktatGlobe` in „Datei transkribieren“).
2. Entwurf 3 ist umgesetzt (`app/main/`: Start, Wörterbuch, Stile, Einstellungen); auf dem S24 ansehen und nachziehen.
3. Wolken-Shader ist an den Prototyp angeglichen (`dictate/ui/`: Shader, `CloudMotion`, `CloudPalette`); auf dem S24 ansehen.
4. Anbieter Gladia, Cerebras (Aufräumen) und das AssemblyAI-Sync-Modell (ein Eintrag „AssemblyAI“, Modell `universal-3-5-pro-sync`) sind eingebaut (`ProviderRegistry`); mit echten Keys auf dem S24 testen.
5. Langdiktat ist am Button angebunden (automatisch an Pausen, nur beim Antippen); Halten/Antippen ist wählbar (Einstellungen › Aufnahme). Auf dem S24 testen.

Details und Reihenfolge: Abschnitt 5 und 7 in `docs/design/diktat-design.md`.
