# diktat – Funktionen

Stand: aus dem Code des Branches `diktat-edition` gelesen (Oktober 2026), nach dem Entfernen der Tastatur
aktualisiert (Abschnitt 13). Beschreibt, was die App **heute kann**,
wo es im Code liegt und was Luxx1993 damit vorhat. Design und Optik stehen in `docs/design/diktat-design.md`.

Status-Legende: **vorhanden** (läuft heute) · **entfernt** (in der Diktat-Edition gelöscht, siehe Abschnitt 13) ·
**geplant** (von Luxx1993 gewünscht, noch nicht gebaut) · **offen** (Entscheidung von Luxx1993 steht aus).

Pfade beginnen, wenn nicht anders genannt, bei `app/src/main/kotlin/dev/patrickgold/florisboard/`.

---

## 0. Soll-Zustand (maßgeblich)

Die Screens in `docs/design/entwurf-3/` zeigen, was die App können soll. Alles, was dort nicht vorkommt, ist kein Ziel.
Die Abschnitte 1–12 beschreiben, was der Code heute kann; wo sie mehr enthalten als dieser Abschnitt, ist das Mehrangebot
des Originals, das nur dann bleibt, wenn es unten steht.

**Aus den Screens:**
- **Start:** Begrüßung, Statistik (Wörter gesamt, Diesen Monat, Tage in Folge), die zwei aktiven Anbieter (Transkription, Aufräumen), „Letzte Diktate“ mit Kopieren-Taste und „Alle“.
- **Wörterbuch:** Suche/Hinzufügen, **Eigene Wörter**, **Ersetzungen** (gesprochen → geschrieben, läuft nach dem Diktat).
- **Stile:** Aufräum-Stile, genau einer aktiv und läuft nach jedem Diktat; Standard „Aufräumen“, dazu Wörtlich, E-Mail, Chat, Aktenvermerk; „Neuer Stil“.
- **Einstellungen:** Schwebender Button (Stil Wolke, Farbe, Schalter), **Diktat** (Sprachen, KI-Anbieter, Aufnahme: Gedrückt halten und Stille kürzen), **Mehr** (Datei transkribieren, Spracheingabe = Mikrofon-Taste in SwiftKey, Sichern und Wiederherstellen).
- Dazu aus Luxx1993s Wünschen: Icon mit Animationen und Umschalter weiß/schwarz; Verlauf und Statistik behalten; Offline-Spracherkennung behalten.

**Nicht im Soll:** Tastatur in jeder Form, Wear OS, GIFs, Sticker, Scan, Übersetzung, Rechtschreibprüfung, Dictate Cloud (Guthaben).
**Im Code vorhanden, aber nicht in den Screens (Verbleib noch zu klären):** Echtzeit, Bluetooth-Mikro, Audioquelle, Audio beschleunigen, Auto-Enter, Tippgeschwindigkeit, Prompt-Bibliothek, Proxy, Verlauf-Details. Sofortaufnahme und Tipp-Kürzel sind mit der Tastatur entfernt. Vorschlag: unter „Mehr“ oder „Aufnahme“ in der neuen Oberfläche bündeln, nichts davon neu bauen.

---

## 1. Kernablauf

sprechen → (Transkription durch Anbieter) → optional KI-Aufräumen / Stil → Ersetzungen (Wörterbuch) → Text einfügen.

- Zentrale Steuerung: `dictate/DictateController.kt` (ein Prozess-Singleton mit einem Zustand und einem Ausgabeziel).
- Aufnahme: `dictate/audio/RecordingController.kt` (Mikrofonquelle, Bluetooth-Mikro, Pegel, Stille-Erkennung).
- Eigene Wörter werden dem Transkriptions-Prompt angehängt (`DictatePromptDefaults.appendCustomWords`, Aufruf in `DictateController`).
- Ersetzungen (Wörterbuch) laufen **danach**, rein lokal und ohne Tokens (`dictate/data/mappings/DictateMappings.kt`).
- Status: **vorhanden**.

## 2. Wo man diktiert (Eingabewege)

| Weg | Was es ist | Code | Status |
|---|---|---|---|
| **Schwebender Button („Wolke“)** | Bubble über anderen Apps (Bedienungshilfe-Dienst), schreibt in das fokussierte Feld. Luxx1993s Hauptweg neben SwiftKey. | `dictate/overlay/` | vorhanden |
| **Spracheingabe-Methode** | Dictate als Spracheingabe-Methode, zusammen mit einer fremden Tastatur (SwiftKey) nutzbar. | `dictate/recognition/DictateVoiceInputMethodService.kt` | vorhanden |
| **System-Erkennungsdienst** | Dictate als systemweiter Spracherkenner (Standard-Android-`RecognitionService`). Manche Tastaturen (z. B. Gboard) nutzen ihn nicht. | `dictate/recognition/DictateRecognitionService.kt`, `RecognitionActivity.kt` | vorhanden |
| **Notizen** | Menü am schwebenden Button „Notiz diktieren“ (oder Schalter „Jedes Diktat als Notiz“): Text mit Datum und Uhrzeit in den Ordner „Notizen“ und in die Zwischenablage, ohne Audio, nichts ins Feld. Notizen einzeln oder alle löschen, kopieren, teilen. | `dictate/data/notes/`, `NoteSink.kt`, `DictateNotesScreen.kt` | vorhanden |
| **Datei transkribieren** | Audiodatei wählen oder aus anderer App teilen, Ergebnis in eigener Ansicht. Auch im Menü des schwebenden Buttons. | `dictate/importer/`, `FileTranscriptionActivity.kt` | vorhanden |
| ~~Volle Tastatur (IME)~~ | FlorisBoard-Tastatur mit Diktat-Leiste, Prompts, GIFs, Sticker, Scan, Übersetzung | – | **entfernt** (Abschnitt 13) |
| ~~Wear OS~~ | Diktat von der Uhr | – | **entfernt** (Abschnitt 13) |

## 3. Anbieter (Transkription und Aufräumen)

Zwei getrennte Aufgaben, je ein aktiver Anbieter: **Transkription** und **Aufräumen/Umformulieren** (Rewording).
Eigene Keys pro Anbieter. Liste und Standardmodelle: `lib/dictate-core/.../provider/ProviderRegistry.kt`.
Die Einstellungsseite zeigt nur eingerichtete Anbieter, weitere hinter „Anbieter hinzufügen“ (Filter: Transkription, Umformulieren, Echtzeit, EU).

| Anbieter | Transkription | Aufräumen (Chat) | Echtzeit |
|---|---|---|---|
| ElevenLabs Scribe (Standard `scribe_v2`) | ja | – | ja |
| Anthropic (Claude) (Standard im Code `claude-haiku-4-5-20251001`) | – | ja | – |
| OpenAI | ja | ja | ja |
| Groq | ja | ja | – |
| Google Gemini | ja | ja | ja |
| Mistral AI (Voxtral) | ja | ja | ja |
| Deepgram (Nova-3) | ja | – | ja |
| AssemblyAI (Universal-3 Pro, asynchron) | ja | – | ja |
| AssemblyAI, Modell `universal-3-5-pro-sync` (eine Anfrage, 256 MB / 20 Min.) | ja | – | – |
| Gladia (`solaria-1`, `solaria-3`; EU-West) | ja | – | ja |
| Together AI (Spracherkennung `openai/whisper-large-v3`, `nvidia/parakeet-tdt-0.6b-v3`) | ja | ja | – |
| Cerebras (`gpt-oss-120b`, `qwen-3.8-27b`) | – | ja | – |
| Soniox | ja | – | ja |
| Azure Speech | ja | – | – |
| xAI (Grok) | ja | ja | ja |
| Scaleway, OVHcloud (EU-Server) | ja | ja | – |
| OpenRouter, Together AI, DeepInfra, DeepSeek, SiliconFlow | je nach Anbieter | ja | – |
| Ollama (lokal im Netz, ohne Key) | – | ja | – |
| **Auf dem Gerät (offline)** | ja (Whisper-Modelle) | – | ja |
| **Eigener Server** (OpenAI-kompatibel, benutzerdefiniert) | ja | ja | – |

Hinweise:
- Luxx1993 nutzt heute **ElevenLabs Scribe** + **Claude Haiku 5.5**. Das Modell wird im Konto des Anbieters eingetragen (nicht der Code-Standard).
- **Dictate Cloud** (Guthaben-Angebot des Originals) ist **entfernt** (Abschnitt 13). Luxx1993 nutzt eigene Keys.
- Seit Oktober 2026 eingebaut: **Gladia**, **Cerebras** (zum Aufräumen) und **AssemblyAI Sync API** (seit 21.10.2026 kein eigener Eintrag mehr, sondern das Modell `universal-3-5-pro-sync` bei AssemblyAI; ein Key, Regionen Global/EU; ein früherer Sync-Eintrag wird beim Start übernommen). Mit echten Keys noch nicht getestet; Modell-IDs und Endpunkte stammen aus den Doku-Seiten der Anbieter (19.10.2026).
- Proxy-Einstellung (HTTP/SOCKS mit Login) vorhanden: `app/settings/dictate/DictateProxyScreen.kt`.
- Offline-Modelle werden heruntergeladen und verwaltet: `dictate/provider/LocalModelManager.kt`, `ModelDownloadService.kt`.

## 4. Sprachen

- Mehrere Diktiersprachen wählbar, zwischen denen durchgeschaltet wird („Transkriptionssprachen“): `app/settings/dictate/DictateLanguagesScreen.kt`, `dictate/DictateLanguages.kt`.
- Status: **vorhanden**.

## 5. Aufnahme-Einstellungen (Gruppe „Aufnahme“)

Alle in `app/settings/dictate/DictateScreen.kt`:

- **Aufnahme mit dem Button** (`prefs.dictate.recordMode`, `DictateRecordMode`): **Antippen** zum Starten, nochmal zum Beenden (Standard) oder **Gedrückt halten** zum Aufnehmen, loslassen zum Senden. Wirkt am schwebenden Button (`DictateBubbleController.attachTouch`). Im Halten-Modus öffnet kurzes Tippen das Prompt-Menü, Ziehen vor dem Start verschiebt den Button. Ersetzt den alten Tastatur-Schalter `dictate__push_to_talk`.
- **Echtzeit-Transkription** (Streaming, wenn der Anbieter es kann)
- **Langdiktat**: aus / an. An heißt: lange Diktate am schwebenden Button werden schon während des Sprechens in Abschnitten transkribiert, geschnitten an Sprechpausen (Silero-VAD, optional Smart Turn v3, maximale Pause einstellbar); Formatieren, Stil und Ersetzungen laufen am Ende einmal über alles. Gilt nur beim **Antippen** (nicht beim Gedrückthalten), nicht mit Echtzeit-Transkription und nicht für die Spracheingabe in SwiftKey. Die frühere Variante „manuell“ (Weiter-Taste der Tastatur) zählt jetzt als automatisch.
- Andere Audio-Wiedergabe pausieren; Bluetooth-Mikrofon; Audioquelle wählen; Bildschirm anlassen
- **Stille Aufnahmen überspringen**; **lange Pausen herausschneiden**; **Audio beschleunigen** (Tonhöhe bleibt)
- Status: **vorhanden**.

## 6. Ausgabe-Einstellungen (Gruppe „Ausgabe“)

- **Auto-Enter** nach dem Diktat
- **Sofort einfügen** statt Tippanimation; **Tippgeschwindigkeit**
- **In Absätze teilen** (lange Transkripte an Satzgrenzen)
- **Erneut-senden-Taste** bei Fehler; **haptisches Feedback**
- **Letztes Diktat merken** (erneut einfügbar)
- Status: **vorhanden**.

## 7. Aufräumen / Umformulieren (KI)

- Schalter „Umformulieren aktivieren“, eigener Anbieter, Key (kann den der Transkription mitnutzen) und Modell: `app/settings/dictate/DictateRewordingScreen.kt`.
- **Denkaufwand** (Reasoning effort): aus, minimal, niedrig, mittel, hoch, benutzerdefiniert – global und pro Prompt überschreibbar.
- Status: **vorhanden**.

## 8. Stile (im Code: „Prompts“)

Im Design heißen sie **Stile**, im Code **Prompts** (`dictate/data/prompts/PromptModel.kt`, UI `DictatePromptsScreen.kt`).

Ein Prompt hat:
- Name und Anweisungstext an die KI. Text in `[eckigen Klammern]` wird **unverändert eingefügt** (Textbaustein/Snippet).
- **Auswahl erforderlich**: wirkt auf den markierten Text und ersetzt ihn.
- **Automatisch anwenden**: läuft nach jedem Diktat von selbst (das ist der „Aktive Stil“ / „Nach jedem Diktat anwenden“ im Entwurf).
- **Denkaufwand** pro Prompt.
- Reihenfolge per langem Drücken und Ziehen; **Export/Import** als `dictate_prompts.json`.
- **Prompt-Bibliothek** der Community zum Hinzufügen (`DictatePromptLibraryScreen.kt`, `dictate/data/prompts/PromptLibrary*.kt`).
- Außerdem „Zeichensetzung & Stil“ für die Transkription selbst (keiner / Standard / eigener Text).
- **Mitgelieferte Stile** Aufräumen (aktiv), Wörtlich, E-Mail, Chat, Aktenvermerk: formuliert nach Dictate Keyboards eigenen Prompts (Füllwörter entfernen, Grammatik, E-Mail, freundlich/kürzer, formell/Stichpunkte), Wortlaut von Luxx1993 freigegeben. Neue Installation: diese fünf statt der alten Beispiel-Prompts. Bestehende: einmalig ergänzt (`DictateLegacyMigrator.seedStylesIfNeeded`), eigene Prompts bleiben, Aufräumen wird nur aktiv, wenn noch kein Prompt automatisch läuft.
- **Tab „Stile“** (`app/main/StylesTab.kt`): zeigt alle Prompts außer Textbausteinen. Aktiv ist der Prompt mit „Automatisch anwenden“, sonst der zuletzt gewählte (`prefs.dictate.activeStyleId`). Der Schalter setzt „Automatisch anwenden“ nur auf diesen einen Stil; Tippen auf eine andere Karte macht sie aktiv, langes Drücken (oder Tippen auf den aktiven Stil) öffnet den Editor im Tab: Name und Anweisung ändern, speichern, löschen. „Neuer Stil“ legt einen eigenen Stil an. Reihenfolge, Denkaufwand, Import/Export und Bibliothek bleiben unter „Stile verwalten“.

## 9. Wörterbuch

Zwei getrennte Dinge (im Entwurf in einem Tab „Wörterbuch“):

1. **Eigene Wörter** (`DictateCustomWordsSection.kt`): Namen und Fachbegriffe, die dem Sprachmodell mitgegeben werden, damit es sie richtig schreibt. Zählt Tokens (das Sprachmodell liest nur begrenzt viel), Import/Export als Textdatei.
2. **Ersetzungen / Mappings** (`DictateMappingsScreen.kt`, `DictateMappings.kt`): Suchen-und-Ersetzen auf dem fertigen Text, exakt und lokal. Pro Regel: Groß-/Kleinschreibung beachten, nur ganze Wörter.

Status: **vorhanden**. Das persönliche Wörterbuch der Tastatur ist **entfernt** (auch der Import eigener Wörter dorthin).
Tab „Wörterbuch“ (`app/main/DictionaryTab.kt`): Feld zum Suchen und Hinzufügen, Wörter als Chips (Tippen = entfernen), Ersetzungen als Liste, Tippen öffnet den Ersetzungen-Bildschirm. Import/Export und Token-Anzeige bleiben unter Alle Einstellungen › Diktat.

## 10. Schwebender Button („Wolke“)

Einstellungen in `app/settings/dictate/DictateFloatingButtonScreen.kt`:

- Ein/Aus, Bedienungshilfe-Dienst und Mikrofonfreigabe einrichten
- **Wann zeigen**: sobald ein Feld Fokus hat · nur bei offener Tastatur (`DictateFloatingButtonShowWhen.kt`). Solange Dictates eigene Spracheingabe offen ist, tritt der Button zurück.
- **App-Auswahl**: überall · nur in bestimmten Apps · überall außer in bestimmten Apps (`DictateFloatingButtonAppsScreen.kt`)
- **Stil**: Ring, Pille, Kugel, **Wolke** (Standard), Aurora, Gitterkugel (`DictateFloatingButtonDesign.kt`). Alle Stile tragen die Farben der gewählten Wolken-Palette: Pille, Ring und Kugel eine Glasfläche (`dictate/ui/GlassDrawable.kt`) statt flacher Farbe, beim Aufnehmen eine Stufe tiefer statt Signalrot; Fertig und Fehler im Grün und Rot der Wolke.
- Größe, an Rand einrasten, Farbe, ausblenden wenn untätig, **Position pro App merken**, Vibration beim Tippen
- **Rückgängig-Taste**; **jedes Diktat in die Zwischenablage kopieren**
- **Einfügen**: über die Eingabeverbindung der Bedienungshilfe, sonst den Feldinhalt, sonst Einfügen per Zwischenablage; drei Versuche. Nimmt die App den Text trotzdem nicht an, liegt er in der Zwischenablage **und** wartet zwei Minuten: Tippen ins Feld (gleiche App) oder auf die Wolke fügt ihn ein, doppelt eingefügt wird nicht. Scheitert auch das, bleibt nur die Zwischenablage. Das Log (`DictateOverlay`, Zeile `commit path=…`) nennt die App, nie den Text.
- **Menü am Button**: Datei transkribieren, Verlauf, Einstellungen (`overlay/BubbleMenuAction.kt`) – einzeln abschaltbar
- Zustände der Wolke: Ruhe, Zuhören (folgt dem Mikrofonpegel), Denken, Erfolg, Fehler (`ui/AudioReactiveCloudOrbView.kt`)
- Optik der Wolke und Paletten: siehe Design-Dokument Abschnitt 4. An den Prototyp **angeglichen** (5 Paletten, Farbe im Tab Einstellungen); dieselbe Wolke zeigen Spracheingabe und Erkennungsdienst.

## 11. Verlauf

- Verlauf der Diktate an/aus, **Audio mit aufbewahren**, Speichergrenzen (maximale Einträge, automatisch löschen nach X Tagen, Audio-Speichergrenze)
- Einträge ansehen, kopieren, Audio abspielen, Verlauf leeren, **in einen Ordner exportieren** (optional mit Aufnahme)
- Code: `dictate/data/history/`, `app/settings/dictate/DictateHistoryScreen.kt`
- Status: **vorhanden**. Luxx1993 will Verlauf und Statistik **behalten**.

## 12. Statistik

- Diktate, Wörter, Zeichen, gesprochene Zeit, Wörter pro Diktat, Sprechtempo (WPM), Umformulierungen, **gesparte Zeit gegenüber Tippen**, **Tage in Folge** (mit Bestwert), Aktivität der letzten 7 Tage, Zurücksetzen, Teilen
- Code: `dictate/data/stats/DictateStats.kt`, `app/settings/dictate/DictateStatsScreen.kt`
- Die Startseite zeigt Wörter gesamt, Diesen Monat (neu: `statsMonthIndex`/`statsMonthWords`, zählt ab dem ersten Diktat des Monats, ältere Diktate zählen nicht nachträglich) und Tage in Folge (0, sobald ein Tag ausgelassen wurde).
- Status: **vorhanden**.

## 13. Tastatur und Zubehör entfernen

Luxx1993s Vorgabe: **die Tastatur und alles, was damit zusammenhängt, soll so weit wie möglich raus.**
Status: **erledigt** (Oktober 2026, drei Schritte, nach jedem Schritt gebaut; Unit-Tests grün).
Den Schalter `dictationOnly` und `app/DictationOnly.kt` gibt es nicht mehr: die App kann keine volle Tastatur mehr bauen.
Updates des Originals lassen sich damit praktisch nicht mehr übernehmen (so gewollt).

**Entfernt:**
1. Wear OS: Modul `:wear`, `dictate/wear/`, Wear-Protokoll in `lib/dictate-core`, Wear-Seite, `play-services-wearable`.
2. Dictate Cloud: Guthaben-Anbieter, Cloud-Seite, Play Billing, Guthaben-Hinweise, Wallet-Felder im Anbieterkonto, Server (`cloud/`).
3. Tastatur: `FlorisImeService`, Rechtschreibprüfung, das ganze Paket `ime/` (Layouts, Wortvorschläge, Gesten,
   Zwischenablage, Emoji, Themes, Smartbar …), GIF, Sticker, Scan (ML Kit), Übersetzung (Bergamot), eigene Symbole,
   Tipp-Kürzel, die Tastatur-Oberfläche in `dictate/ui/`, Erweiterungen (`app/ext`, `lib/ext`), Theme-Engine `lib/snygg`,
   Devtools, Was-ist-neu-Tour und Changelog-Dialog, alle Tastatur-Einstellungsseiten, 21 MB Tastatur-Assets,
   Coil, die Tastatur-Hinweise in der Smartbar (Bewerten, Spenden, Meilensteine), Sofortaufnahme,
   Aufnahme-Animation, Prompt-Layout, „Sprache folgt Tastatur“, die Einrichtungs-Schritte „Tastatur aktivieren/auswählen“,
   ~1.800 unbenutzte Texte (in allen Sprachen) und weitere Ressourcen.

**Bleibt wegen Abhängigkeit:**
- **Spracheingabe-Methode** (`dictate/recognition/DictateVoiceInputMethodService.kt`, `res/xml/dictate_voice_ime.xml`,
  Manifest-Eintrag mit `BIND_INPUT_METHOD`): das ist die Eingabemethode, die Android unter den Tastatur-Einstellungen
  anbietet und die SwiftKeys Mikrofon-Taste aufruft (Regel b). Sie ist eigenständig und braucht keinen Tastatur-Code.
- **Erkennungsdienst** (`DictateRecognitionService`, `RecognitionActivity`, `res/xml/recognition_service.xml`): für Apps und
  Tastaturen, die den System-Spracherkenner oder `RECOGNIZE_SPEECH` nutzen (Regel b).
- **Erkennung „eigene Eingabemethode ist offen“** im Bedienungshilfe-Dienst: betrifft jetzt die Spracheingabe-Methode; der
  Button tritt zurück, solange sie zu sehen ist.
- **Paketname und Grundgerüst von FlorisBoard** (`dev.patrickgold.florisboard`, `FlorisApplication`, `FlorisAppActivity`,
  `FlorisPreferenceModel`, `lib/android`, `lib/kotlin`, `lib/compose`, `lib/color`, JetPref): tragen die ganze App, sind aber
  keine Tastatur. Umbenennen wäre reine Kosmetik mit großem Umfang.
- **Spalte `trigger` in der Prompt-Datenbank**: das Tipp-Kürzel ist weg, die Spalte bleibt, damit keine Datenbank-Migration nötig ist.
- **Import aus der alten Dictate-App** (`DictateLegacyMigrator`): keine Tastatur, nur Übernahme alter Einstellungen.

**Entschieden:** Langdiktat bleibt und ist am schwebenden Button angebunden (Abschnitt 5). Halten zum Aufnehmen bleibt und ist am schwebenden Button angebunden, wählbar unter Einstellungen ›
Aufnahme (Antippen oder Gedrückt halten, Abschnitt 5).

**Offen (Entscheidung von Luxx1993):**
- Nicht auf einem Gerät getestet (kein Emulator im Container). Vor dem nächsten Schritt einmal auf dem S24 prüfen:
  Button, Spracheingabe in SwiftKey, Datei transkribieren, Sichern/Wiederherstellen.

## 14. Geplant (von Luxx1993)

Reihenfolge und Details: `CLAUDE.md` und `docs/design/diktat-design.md` Abschnitt 7.

1. App-Icon „Welle um die Welt“: Icon, Themen-Icon, Umschalter weiß/schwarz, Splash-Animation A und In-App-Animation D (in „Datei transkribieren“) **vorhanden**
2. Neue Oberfläche „Entwurf 3“ (Start, Wörterbuch, Stile, Einstellungen): **vorhanden** (`app/main/`), Startbildschirm der App; die alte Einstellungsliste ist „Alle Einstellungen“. Auf dem S24 noch nicht angesehen.
3. Wolken-Shader an den Prototyp angleichen: **vorhanden**, auf dem S24 noch nicht angesehen
4. Anbieter Gladia, Preset Cerebras, AssemblyAI Sync API: **vorhanden**, mit echten Keys ungetestet

## 15. Noch offen (Entscheidung von Luxx1993)

- Welche der vorhandenen Einstellungen bleiben in der neuen Oberfläche sichtbar, welche wandern unter „Mehr“?
- Offline-Spracherkennung: laut Luxx1993 **behalten**; Modellverwaltung muss in die neue Oberfläche passen.
