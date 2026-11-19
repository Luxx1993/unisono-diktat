/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app

import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.setup.NotificationPermissionState
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonAppScope
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonDesign
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonShowWhen
import dev.patrickgold.florisboard.dictate.DictateRecordMode
import dev.patrickgold.florisboard.dictate.DictateLongformMode
import dev.patrickgold.florisboard.dictate.audio.AudioSpeedUp
import dev.patrickgold.florisboard.dictate.audio.DictateAudioSource
import dev.patrickgold.florisboard.dictate.DictateFloatingButtonSize
import dev.patrickgold.florisboard.dictate.DictateReasoningEffort
import dev.patrickgold.florisboard.dictate.data.mappings.DictateMappings
import dev.patrickgold.florisboard.dictate.overlay.BubbleAnchors
import dev.patrickgold.florisboard.dictate.overlay.BubbleApps
import dev.patrickgold.florisboard.dictate.provider.DictateProxyType
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.lib.util.VersionName
import dev.patrickgold.jetpref.datastore.annotations.Preferences
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.model.PreferenceMigrationEntry
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import dev.patrickgold.jetpref.datastore.model.PreferenceType

val FlorisPreferenceStore = jetprefDataStoreOf(FlorisPreferenceModel::class)

@Preferences
abstract class FlorisPreferenceModel : PreferenceModel() {
    companion object {
        const val NAME = "florisboard-app-prefs"
    }

    val devtools = Devtools()
    inner class Devtools {
        val enabled = boolean(
            key = "devtools__enabled",
            default = false,
        )
        // Makes the floating button insert as if the accessibility input connection did not exist, i.e.
        // the way it must on Android 12 and older, where that API is not there at all. Without this the
        // whole node/placeholder/paste half of the insert path (issue #314) is unreachable on a modern
        // phone — and an emulator cannot stand in for it, because the apps whose fields misreport their
        // placeholder are exactly the ones not installed there.
        val forceLegacyInsertion = boolean(
            key = "devtools__force_legacy_insertion",
            default = false,
        )
    }

    val dictate = Dictate()
    inner class Dictate {
        // --- Provider keyring (multi-provider, roadmap section 4.x) ------------------------------
        // Per-provider credentials (API key + chosen models + custom base URL), keyed by provider id.
        // This is the source of truth for keys/models; transcriptionProviderId / rewordingProviderId
        // below are just the *active* pointers into this keyring. See ProviderAccounts.
        val providerAccounts = custom(
            key = "dictate__provider_accounts",
            default = ProviderAccounts.Empty,
            serializer = ProviderAccounts.Serializer,
        )
        // Guard so the one-time import of the legacy flat prefs (apiKey/transcriptionModel/… below)
        // into the keyring runs exactly once. See DictateProviderMigrator.
        val providerAccountsMigrated = boolean(
            key = "dictate__provider_accounts_migrated",
            default = false,
        )

        // Active transcription provider id, matching a ProviderRegistry id that supports speech-to-text
        // ("openai", "groq") or a "custom:<uuid>" endpoint. The actual key/model live in the keyring.
        val transcriptionProviderId = string(
            key = "dictate__transcription_provider_id",
            default = "openai",
        )

        // On-device offline fallback (issue #104): when the active provider is a cloud one and its call
        // fails because the device is offline (after the normal retries), retry once on-device using the
        // local provider's downloaded model. No effect if the local model isn't installed, or if the
        // active provider is already the local one.
        val localFallbackEnabled = boolean(
            key = "dictate__local_fallback_enabled",
            default = false,
        )

        // --- Network proxy (roadmap 5.6) ---------------------------------------------------------
        // Optional proxy applied to *every* provider API call (transcription, rewording, model
        // listing, connection test). Disabled by default; built into a ProxyConfig via ProxyConfig.of
        // and forwarded to OkHttp. HTTP proxies support user/password; SOCKS5 credentials are not
        // forwarded (JVM limitation). See dictateProxyConfig().
        val proxyEnabled = boolean(
            key = "dictate__proxy_enabled",
            default = false,
        )
        val proxyType = enum(
            key = "dictate__proxy_type",
            default = DictateProxyType.HTTP,
        )
        val proxyHost = string(
            key = "dictate__proxy_host",
            default = "",
        )
        val proxyPort = string(
            key = "dictate__proxy_port",
            default = "8080",
        )
        val proxyUsername = string(
            key = "dictate__proxy_username",
            default = "",
        )
        val proxyPassword = string(
            key = "dictate__proxy_password",
            default = "",
        )
        /**
         * DEPRECATED: the app-wide switch, now [dev.patrickgold.florisboard.dictate.provider.ProviderAccount.trustUserCerts]
         * per account (#383). Read once by the migrator, which turns it off; never for a live call.
         */
        val trustUserCertificates = boolean(
            key = "dictate__trust_user_certificates",
            default = false,
        )
        /**
         * Seconds a request may go without a byte before it counts as failed (issue #337).
         *
         * One number for both halves of the wait — the gap between bytes and the budget for the whole
         * call — because they are the same question to the person waiting, and telling the two apart
         * takes knowing how OkHttp works. Two minutes is right for a cloud provider; the reason this is
         * adjustable at all is the self-hosted end of the range, where a model on a slow machine can
         * think for longer than that before it answers. Uploads are not capped by this: while bytes are
         * moving, every one of them starts the clock again.
         *
         * It also ends the automatic retries: once a request has been going this long, it fails rather
         * than starting another attempt, which the resend chip leaves to the user (#438).
         *
         * The file import ignores anything lower than its own, more generous limits — a screen with a
         * cancel button on it is not the place to give up early.
         */
        val requestTimeout = int(
            key = "dictate__request_timeout",
            default = 120,
        )

        // --- DEPRECATED flat credential prefs (migration source only) ----------------------------
        // Kept solely so DictateProviderMigrator can copy them into the keyring once. Do not read these
        // for live calls anymore – use providerAccounts[transcriptionProviderId].
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val apiKey = string(
            key = "dictate__api_key",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val transcriptionModel = string(
            key = "dictate__transcription_model",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val customBaseUrl = string(
            key = "dictate__custom_base_url",
            default = "",
        )
        // Pause/duck other apps' audio while recording (default on, as in the legacy Dictate).
        val audioFocus = boolean(
            key = "dictate__audio_focus",
            default = true,
        )
        // Route recording through a connected Bluetooth (SCO) microphone when available.
        val useBluetoothMic = boolean(
            key = "dictate__use_bluetooth_mic",
            default = false,
        )
        // Which audio source the recorder captures from (issue #62). Default keeps the device MIC
        // (current behavior); VOICE_RECOGNITION/UNPROCESSED skip phone audio processing that can hurt
        // transcription. Ignored on the Bluetooth-SCO path (always VOICE_COMMUNICATION).
        val audioInputSource = enum(
            key = "dictate__audio_input_source",
            default = DictateAudioSource.DEFAULT,
        )
        // Keep the screen awake while a recording is in progress (default on).
        val keepScreenAwake = boolean(
            key = "dictate__keep_screen_awake",
            default = true,
        )
        // Skip transcription when a local Silero VAD finds no speech in the recording, so silent clips
        // don't produce "ghost text" hallucinations or waste API credits (issue #93). Default on.
        val skipSilentRecordings = boolean(
            key = "dictate__skip_silent_recordings",
            default = true,
        )
        // Trim long internal pauses (> ~2 s of silence) out of a recording before it's uploaded, using the
        // same local Silero VAD as the silence gate (issue #232). Every spoken segment is kept in full;
        // only the dead time between them is collapsed, so a dictation with big gaps sends less audio (less
        // cost/latency) without losing a word. Default on. Ignored while long-form dictation is active — it
        // does its own segment-cutting.
        val trimSilentGaps = boolean(
            key = "dictate__trim_silent_gaps",
            default = true,
        )
        // Play the recording faster before uploading it, without raising its pitch (issue #272). Stored as
        // a percentage of the original speed: 100 = off, 150 = 1.5x, which bills two thirds of what was
        // spoken. Where #93 and #232 remove dead time, this shortens the speech itself — so it is off by
        // default: it is the only one of the three that can change what the model hears.
        val audioSpeedUpPercent = int(
            key = "dictate__audio_speed_up_percent",
            default = AudioSpeedUp.MIN_PERCENT,
        )
        // Break long *plain* transcripts into paragraphs (issue #225): once at least this many words have
        // accumulated, the next sentence end starts a new paragraph. 0 = off (default). Deterministic and
        // only applied to a pure transcript — never to reworded / auto-formatted output, which already
        // carries its own paragraphing.
        val paragraphSplitWords = int(
            key = "dictate__paragraph_split_words",
            default = 0,
        )
        // Long-press the send (mic) button while recording to transcribe with the on-device model instead
        // of the configured cloud provider (issue #228), for a quick offline one-off without digging
        // through the provider settings. Toggled from the on-device model dialog. Off by default. Only
        // applies to a plain recording — in long-form / streaming there is no plain send button to hold.
        val longPressSendLocalModel = boolean(
            key = "dictate__long_press_send_local_model",
            default = false,
        )
        // How the floating button records: tap to start and tap again to stop, or hold to record and release
        // to send. Replaces the keyboard-era switch `dictate__push_to_talk`, which only the removed keyboard
        // read; migrate() drops it. Tap by default, as the button has always worked.
        val recordMode = enum(
            key = "dictate__record_mode",
            default = DictateRecordMode.TAP,
        )
        // Minutes the on-device model may sit idle before it is unloaded from RAM to free memory (models
        // are ~100 MB up to ~700 MB). It is always also freed immediately on an Android memory-pressure
        // signal; this timer additionally covers the "keyboard alive but not dictating" window. 0 = only
        // on memory pressure (no idle timer). Default 5 minutes.
        val localModelUnloadMinutes = int(
            key = "dictate__local_model_unload_minutes",
            default = 5,
        )
        // Haptic feedback on dictation state changes (issue #166): a short buzz on record start/stop, a
        // double on transcription done, a longer one when a rewording/LLM prompt finished — so the user
        // knows blindly when to look back at the screen. Off by default. Amplitude honours the system
        // haptic intensity.
        val hapticFeedback = boolean(
            key = "dictate__haptic_feedback",
            default = false,
        )

        // Floating dictation button (issue #88): the in-app master toggle. The bubble only shows when
        // this is on AND the DictateAccessibilityService is enabled in the system accessibility settings
        // (the latter is the actual permission; this lets the user hide the bubble without digging into
        // system settings). Default off — opt-in feature.
        val floatingButtonEnabled = boolean(
            key = "dictate__floating_button_enabled",
            default = false,
        )
        // When the floating button comes up (issue #439): always, as soon as a field is selected, or only while a
        // keyboard is open. Replaces the boolean `dictate__floating_button_show_with_dictate_keyboard`,
        // which migrate() carries over.
        val floatingButtonShowWhen = enum(
            key = "dictate__floating_button_show_when",
            default = DictateFloatingButtonShowWhen.ALWAYS,
        )
        // Visual style of the floating button, see DictateFloatingButtonDesign. diktat's own is the wave (WAVE, the
        // app icon, docs/design/diktat-design.md §4); an install that never chose one gets it.
        val floatingButtonDesign = enum(
            key = "dictate__floating_button_design",
            default = DictateFloatingButtonDesign.WAVE,
        )
        // Overall size of the floating button (scales the skin dimensions).
        val floatingButtonSize = enum(
            key = "dictate__floating_button_size",
            default = DictateFloatingButtonSize.MEDIUM,
        )
        // Whether the floating button snaps to the nearest screen edge after being dragged. Default on;
        // turn off to leave it wherever it is dropped (still kept within the screen bounds).
        val floatingButtonSnapToEdge = boolean(
            key = "dictate__floating_button_snap_to_edge",
            default = true,
        )
        // Colour of the floating button. For the cloud it picks the palette (CloudPalette, by its main tone);
        // the default is the standard palette Lagune. Dictate's old default light blue reads as Lagune too.
        val floatingButtonColor = custom(
            key = "dictate__floating_button_color",
            default = Color(0xFF3FA9C8),
            serializer = ColorPreferenceSerializer,
        )
        // Fade + shrink the button to a small dot after a few seconds of inactivity; tap to restore.
        val floatingButtonAutoDim = boolean(
            key = "dictate__floating_button_auto_dim",
            default = true,
        )
        // Remember the button's position separately per app.
        val floatingButtonRememberPosition = boolean(
            key = "dictate__floating_button_remember_position",
            default = true,
        )
        // The remembered positions themselves (issue #323). Each is an anchor — a side of the screen plus
        // a share of the travel — not a pixel pair, so a position keeps its meaning when the screen it was
        // made on changes shape: a rotation, a foldable opening, a move into split screen. Persisted
        // because the map used to live in the accessibility service and nowhere else, which made
        // "remember" true only until that service was next restarted.
        val floatingButtonPositions = custom(
            key = "dictate__floating_button_positions",
            default = BubbleAnchors.Empty,
            serializer = BubbleAnchors.Serializer,
        )
        // Which apps the button may appear over (issue #392). ALL by default, so the setting changes
        // nothing until someone opens it. Note the load order: the accessibility service can connect
        // before the preference store has finished loading, so the first value the bubble sees is this
        // default — it fails open, and the flow re-emits the real one a moment later.
        val floatingButtonAppScope = enum(
            key = "dictate__floating_button_app_scope",
            default = DictateFloatingButtonAppScope.ALL,
        )
        // The apps themselves. One list for both directions of floatingButtonAppScope, so switching the
        // mode keeps the selection rather than silently emptying it.
        val floatingButtonApps = custom(
            key = "dictate__floating_button_apps",
            default = BubbleApps.Empty,
            serializer = BubbleApps.Serializer,
        )
        // Vibrate briefly when the button is tapped.
        val floatingButtonHaptic = boolean(
            key = "dictate__floating_button_haptic",
            default = true,
        )
        // Optional undo control on the floating button (issue #133): when on, an undo button appears
        // next to the bubble after a dictation and removes the last inserted text in one tap. Off by
        // default to keep the overlay minimal; needs "remember last dictation" to have something to undo.
        val floatingButtonUndoEnabled = boolean(
            key = "dictate__floating_button_undo_enabled",
            default = false,
        )
        // Safety net (issue #214): unconditionally copy every floating-button dictation to the system
        // clipboard, so nothing is lost if the accessibility insert is silently swallowed (the known
        // "green check but no text" failure) — the user can then just paste it manually. Off by default
        // because it overwrites the clipboard on every dictation (and shows a system clipboard toast on
        // some OEMs like Samsung).
        val floatingButtonCopyToClipboard = boolean(
            key = "dictate__floating_button_copy_to_clipboard",
            default = false,
        )
        // Extra entries in the menu the hold opens (issue #408), below the prompts. All off by default:
        // for a Gboard + bubble user that menu is the only route to their prompts, so nothing joins it
        // unasked. The hold is also the only free gesture the button has — #357 is why these are menu
        // entries and not a second gesture.
        val floatingButtonMenuTranscribeFile = boolean(
            key = "dictate__floating_button_menu_transcribe_file",
            default = false,
        )
        val floatingButtonMenuHistory = boolean(
            key = "dictate__floating_button_menu_history",
            default = false,
        )
        // The "dictate a note" entry of that menu: on from the start, it is the way to the notes folder.
        val floatingButtonMenuNote = boolean(
            key = "dictate__floating_button_menu_note",
            default = true,
        )
        // Every dictation of the floating button becomes a note instead of text in the field.
        val notesAlways = boolean(
            key = "dictate__notes_always",
            default = false,
        )
        val floatingButtonMenuSettings = boolean(
            key = "dictate__floating_button_menu_settings",
            default = false,
        )
        // Whether the user has opened the floating-button screen at least once (clears the "New" badge).
        val floatingButtonHintSeen = boolean(
            key = "dictate__floating_button_hint_seen",
            default = false,
        )
        // --- Output behavior (roadmap section 10) ------------------------------------------------
        // Press Enter / trigger the editor action automatically after committing a transcription.
        val autoEnter = boolean(
            key = "dictate__auto_enter",
            default = false,
        )
        // Commit the transcription all at once (true) or "type" it out character by character (false).
        val instantOutput = boolean(
            key = "dictate__instant_output",
            default = true,
        )
        // Real-time (streaming) transcription (issue #128): transcribe while speaking, for providers
        // that support it (OpenAI realtime, Soniox, Deepgram, …). Global switch; falls back to batch when
        // the selected provider has no realtime support. Default off.
        val realtimeTranscription = boolean(
            key = "dictate__realtime_transcription",
            default = false,
        )
        // Whether the streaming transcript stays out of sight until the recording is stopped (issue #345).
        // The stream runs either way — this only decides whether its provisional words are typed into the
        // field as they arrive or held back and inserted in one piece at the end, which reads like a batch
        // dictation at streaming speed.
        //
        // Stored beside [realtimeTranscription] rather than folded into one enum so that nobody's existing
        // on/off choice needs migrating; false keeps exactly the behaviour realtime has always had.
        val realtimeHidePreview = boolean(
            key = "dictate__realtime_hide_preview",
            default = false,
        )
        // --- Long-form segmented dictation (issue #170) ------------------------------------------
        // Transcribe long dictations segment-by-segment in the background while you keep talking, so you
        // don't wait for one big upload at the end. OFF by default; MANUAL shows the "Next" button, AUTO
        // additionally uses Silero VAD + Smart Turn v3 at speech pauses. Keyboard-only, not for realtime /
        // live-prompt / multimodal.
        val longformMode = enum(
            key = "dictate__longform_mode",
            default = DictateLongformMode.OFF,
        )
        // Maximum silence (Pipecat Smart Turn stop_secs fallback) before AUTO mode cuts even when the
        // semantic classifier says the current thought may be incomplete.
        val longformAutoSplitSeconds = int(
            key = "dictate__longform_auto_split_seconds",
            default = 3,
        )
        // Opt-in semantic auto-segmentation: when on (and the model is downloaded), AUTO mode uses the
        // on-device Smart Turn v3 classifier to cut at completed thoughts instead of only on silence.
        // Off by default; the ~8 MB model is downloaded on demand, not bundled.
        val smartTurnEnabled = boolean(
            key = "dictate__smart_turn_enabled",
            default = false,
        )
        // Speed of the typewriter animation when instantOutput is off (1 = slow … 10 = fast).
        val outputSpeed = int(
            key = "dictate__output_speed",
            default = 5,
        )
        // Show a resend button when a recording failed to transcribe/reword, to retry the same audio.
        val resendButton = boolean(
            key = "dictate__resend_button",
            default = true,
        )
        // Safety net (issue #111): keep the last successful dictation around so it can be re-inserted
        // via the "Re-insert last dictation" Smartbar action after the field is cleared (rotation,
        // context switch, host app refreshing its state). When off, nothing is cached and the action
        // stays disabled. The text is stored locally (see lastDictation) until the next dictation.
        val rememberLastDictation = boolean(
            key = "dictate__remember_last_dictation",
            default = true,
        )
        // The last successfully committed dictation text, persisted so it survives the IME process being
        // killed. Overwritten by the next successful dictation; never shown directly in the UI. Empty
        // means there is nothing to re-insert. Only populated while rememberLastDictation is on.
        val lastDictation = string(
            key = "dictate__last_dictation",
            default = "",
        )
        // Interrupted recording (keyboard closed mid-recording): the audio is finalized and moved to a
        // file in filesDir so it survives the recorder/process being destroyed; these prefs are the
        // persisted marker + metadata so the "recording interrupted — send it?" offer can be restored on
        // the next keyboard open. pending=true means an interrupted-audio file is waiting.
        val interruptedAudioPending = boolean(
            key = "dictate__interrupted_audio_pending",
            default = false,
        )
        // Recorded seconds of the interrupted audio, re-credited towards the rate/donate nudges on send.
        val interruptedAudioSeconds = long(
            key = "dictate__interrupted_audio_seconds",
            default = 0L,
        )
        // Whether the interrupted recording was a live-prompt session, so sending it repeats that mode.
        val interruptedAudioLive = boolean(
            key = "dictate__interrupted_audio_live",
            default = false,
        )
        // --- Transcription history / activity log (issue #140) -----------------------------------
        // Keep a rolling, browsable log of finished dictations (transcript + metadata) so they can be
        // re-inserted, re-transcribed or reviewed later. Supersedes the single lastDictation slot for the
        // history UI; when off, nothing is logged. Never captured in incognito/password fields.
        val historyEnabled = boolean(
            key = "dictate__history_enabled",
            default = true,
        )
        // Additionally keep the source audio (WAV) of each logged dictation so a flaky transcription can be
        // replayed and re-transcribed. Off by default (privacy + disk: ~1.9 MB per recorded minute); the
        // audio lives in the app's private storage and is pruned by the byte budget below.
        val historyAudioRetention = boolean(
            key = "dictate__history_audio_retention",
            default = false,
        )
        // Cap: how many entries to keep (oldest dropped first).
        val historyMaxEntries = int(
            key = "dictate__history_max_entries",
            default = 50,
        )
        // Cap: entries older than this many days are dropped (0 = no age limit).
        val historyMaxAgeDays = int(
            key = "dictate__history_max_age_days",
            default = 30,
        )
        // Cap: total megabytes of retained audio; once exceeded, the oldest recordings' audio is dropped
        // (their transcript text is kept).
        val historyAudioBudgetMb = int(
            key = "dictate__history_audio_budget_mb",
            default = 200,
        )
        // --- Folder export (issue #379) ----------------------------------------------------------
        // A SAF tree URI we hold a persisted read+write grant on. Every finished dictation is written
        // into it as a plain .txt the moment it exists, so whatever the user already runs on that folder
        // (a sync client, a script, an agent) can pick it up. Empty = off, which is also where a revoked
        // grant lands. Deliberately not an account integration: the app writes files, it never uploads.
        val historyExportFolderUri = string(
            key = "dictate__history_export_folder_uri",
            default = "",
        )
        // Display name of that folder, so the settings row can name it without touching SAF.
        val historyExportFolderName = string(
            key = "dictate__history_export_folder_name",
            default = "",
        )
        // Also write the retained WAV next to the transcript. Only does anything while audio retention
        // is on — without it the recording is deleted as soon as it has been transcribed.
        val historyExportAudio = boolean(
            key = "dictate__history_export_audio",
            default = false,
        )
        // Epoch millis of the last write that failed (0 = none since the last success), so the settings
        // row can say so. There is no retry queue; "export everything" is the catch-up.
        val historyExportLastFailure = long(
            key = "dictate__history_export_last_failure",
            default = 0L,
        )
        // --- Lifetime dictation statistics (issue #142) ------------------------------------------
        // Never auto-reset; only the user can reset them from the stats screen. Updated centrally after each successful dictation.
        val statsDictations = long(key = "dictate__stats_dictations", default = 0L)
        val statsWords = long(key = "dictate__stats_words", default = 0L)
        val statsChars = long(key = "dictate__stats_chars", default = 0L)
        val statsSpokenSeconds = long(key = "dictate__stats_spoken_seconds", default = 0L)
        val statsRewordings = long(key = "dictate__stats_rewordings", default = 0L)
        // Epoch millis of the first ever dictation (0 = none yet), for the "tracking since" line.
        val statsFirstUseEpochMs = long(key = "dictate__stats_first_use_epoch_ms", default = 0L)
        // Day-streak bookkeeping: last active day (epoch day) plus current/best consecutive-day runs.
        val statsLastDayEpoch = long(key = "dictate__stats_last_day_epoch", default = 0L)
        val statsStreakCurrent = int(key = "dictate__stats_streak_current", default = 0)
        val statsStreakBest = int(key = "dictate__stats_streak_best", default = 0)
        // Compact rolling per-day word counts for the 7-day chart: "epochDay:words;epochDay:words;…".
        val statsDaily = string(key = "dictate__stats_daily", default = "")
        // Words of the current calendar month, for "Diesen Monat" on the start tab. The month is kept as
        // year * 12 + month (0-based), so a new month starts the count again on its first dictation.
        val statsMonthIndex = int(key = "dictate__stats_month_index", default = 0)
        val statsMonthWords = long(key = "dictate__stats_month_words", default = 0L)
        // Comma-separated dictation language codes the user cycles through on the recording bar
        // (see DictateLanguages; "detect" = auto-detect). Default mirrors the legacy app.
        val inputLanguages = string(
            key = "dictate__input_languages",
            default = "detect,en",
        )
        // The currently active dictation language code; persists across sessions and is switched
        // from the recording bar's language chip.
        val activeInputLanguage = string(
            key = "dictate__active_input_language",
            default = "detect",
        )
        // Guard so the one-time seeding of the device/system dictation language (added on top of the
        // default detect,en) runs only once on a fresh install. See
        // DictateLegacyMigrator.seedDeviceLanguageIfNeeded.
        val inputLanguagesSeeded = boolean(
            key = "dictate__input_languages_seeded",
            default = false,
        )
        // Guard so the one-time import from the legacy Dictate SharedPreferences runs only once.
        val legacyImported = boolean(
            key = "dictate__legacy_imported",
            default = false,
        )

        // --- Rewording / GPT (roadmap section 4) -------------------------------------------------
        // Master switch for the rewording feature (prompt chips, auto-apply, live prompt). Default
        // on, mirroring the legacy app.
        val rewordingEnabled = boolean(
            key = "dictate__rewording_enabled",
            default = true,
        )
        // The spoken command word (issue #139): a dictation opening with it runs as a live prompt
        // instead of being inserted. Blank (the default) switches the whole recognition off — there is
        // no word everyone says rarely enough to be a safe default, so the user picks their own.
        val commandTriggerWord = string(
            key = "dictate__command_trigger_word",
            default = "",
        )
        // Reasoning effort sent as OpenAI-compatible `reasoning_effort` on rewording chat calls for
        // reasoning models (issue #141). OFF omits the field, so non-reasoning models are unaffected.
        val rewordingReasoningEffort = enum(
            key = "dictate__rewording_reasoning_effort",
            default = DictateReasoningEffort.OFF,
        )
        // The wire value sent as `reasoning_effort` when the setting is CUSTOM (issue #186), e.g. a value
        // a specific provider expects. Blank → the field is omitted.
        val rewordingReasoningEffortCustom = string(
            key = "dictate__rewording_reasoning_effort_custom",
            default = "",
        )
        // Chat (rewording) provider id – any chat-capable ProviderRegistry id ("openai", "groq",
        // "openrouter", … or "custom"). Independent from the transcription provider.
        val rewordingProviderId = string(
            key = "dictate__rewording_provider_id",
            default = "openai",
        )
        // --- DEPRECATED flat rewording credential prefs (migration source only) ------------------
        // Kept solely for the one-time keyring import; live calls read providerAccounts instead.
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingApiKey = string(
            key = "dictate__rewording_api_key",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingModel = string(
            key = "dictate__rewording_model",
            default = "",
        )
        @Deprecated("Migrated into providerAccounts; read the keyring instead.")
        val rewordingCustomBaseUrl = string(
            key = "dictate__rewording_custom_base_url",
            default = "",
        )
        // System prompt appended to every rewording request: 0 = none, 1 = predefined (be-precise),
        // 2 = custom. See DictatePromptDefaults.SELECTION_*.
        val systemPromptSelection = int(
            key = "dictate__system_prompt_selection",
            default = 1,
        )
        val systemPromptCustom = string(
            key = "dictate__system_prompt_custom",
            default = "",
        )
        // Style prompt biasing the transcription model (roadmap 2.4): 0 = none, 1 = predefined
        // per-language punctuation/capitalization sentence, 2 = custom.
        val stylePromptSelection = int(
            key = "dictate__style_prompt_selection",
            default = 1,
        )
        val stylePromptCustom = string(
            key = "dictate__style_prompt_custom",
            default = "",
        )
        // Custom vocabulary (roadmap 11.12): names/jargon appended to the transcription prompt so the
        // speech model spells them correctly. Comma- or newline-separated; empty = unused. Applied on
        // top of whatever style prompt (none/predefined/custom) is active.
        val customWords = string(
            key = "dictate__custom_words",
            default = "",
        )
        // Custom mappings (issue #129): deterministic find-and-replace applied to the finished transcript
        // before it is inserted — exact and token-free, unlike the prompt-hint customWords above.
        val customMappings = custom(
            key = "dictate__custom_mappings",
            default = DictateMappings.Empty,
            serializer = DictateMappings.Serializer,
        )
        // Guard for the one-time addition of the five styles of Entwurf 3 to an install that predates them.
        // See DictateLegacyMigrator.seedStylesIfNeeded.
        val stylesSeeded = boolean(
            key = "dictate__styles_seeded",
            default = false,
        )
        // The style the styles tab shows as active while none runs automatically, so switching "Nach jedem
        // Diktat anwenden" off and on again keeps the same one (-1 = none chosen yet).
        val activeStyleId = int(
            key = "dictate__active_style_id",
            default = -1,
        )
        // Run the spoken-formatting-cues → Markdown pass automatically on every transcript.
        val autoFormattingEnabled = boolean(
            key = "dictate__auto_formatting_enabled",
            default = false,
        )
    }

    // On-device translation bar (issue #424). Both are catalog codes ("de", "zh_hant", "en"); the source
    // may also be empty, which means "detect it". The target is empty until the bar is first opened, when
    // it is set to the likeliest choice (see TranslateBarController.initialTarget) and then remembered.
    val internal = Internal()
    inner class Internal {
        val homeIsBetaToolboxCollapsed = boolean(
            key = "internal__home_is_beta_toolbox_collapsed_040a01",
            default = false,
        )
        val isImeSetUp = boolean(
            key = "internal__is_ime_set_up",
            default = false,
        )
        // One-shot signal set by the onboarding's optional floating-button step: completing setup flips
        // [isImeSetUp], which rebuilds the nav graph and resets the back stack to Home; this flag lets
        // FlorisAppActivity then navigate on to the floating-button settings once that reset has settled.
        val openFloatingButtonAfterSetup = boolean(
            key = "internal__open_floating_button_after_setup",
            default = false,
        )
        // Newline-separated most-recent settings-search queries (newest first), for the search screen's
        // recent-search chips (issue #187).
        val settingsSearchHistory = string(
            key = "internal__settings_search_history",
            default = "",
        )
        val versionOnInstall = string(
            key = "internal__version_on_install",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastUse = string(
            key = "internal__version_last_use",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastChangelog = string(
            key = "internal__version_last_changelog",
            default = VersionName.DEFAULT_RAW,
        )
        val versionLastWhatsNew = string(
            key = "internal__version_last_whats_new",
            default = VersionName.DEFAULT_RAW,
        )
        val notificationPermissionState = enum(
            key = "internal__notification_permission_state",
            default = NotificationPermissionState.NOT_SET,
        )
    }

    val other = Other()
    inner class Other {
        val settingsTheme = enum(
            key = "other__settings_theme",
            default = AppTheme.AUTO,
        )
        val accentColor = custom(
            key = "other__accent_color",
            default = Color(0xFF30B7E6), // Dictate light blue
            serializer = ColorPreferenceSerializer,
        )
        val settingsLanguage = string(
            key = "other__settings_language",
            default = "auto",
        )
        val showAppIcon = boolean(
            key = "other__show_app_icon",
            default = true,
        )
        // White or black launcher icon (docs/design/diktat-design.md §5). Applied through two activity
        // aliases, see AppIcon.
        val appIconColor = enum(
            key = "other__app_icon_color",
            default = AppIconColor.WHITE,
        )
        // The name the start tab greets with ("Hallo, <Name>"); blank greets without one.
        val userName = string(
            key = "other__user_name",
            default = "",
        )
    }

    override fun migrate(entry: PreferenceMigrationEntry): PreferenceMigrationEntry {
        return when (entry.key) {
            // Migrate advanced prefs to other prefs
            // Keep migration rules until: 0.7 dev cycle
            "advanced__settings_theme" -> {
                entry.transform(key = "other__settings_theme")
            }
            "advanced__accent_color" -> {
                entry.transform(key = "other__accent_color")
            }
            "advanced__settings_language" -> {
                entry.transform(key = "other__settings_language")
            }
            "advanced__show_app_icon" -> {
                entry.transform(key = "other__show_app_icon")
            }

            // The floating button's "Show with Dictate keyboard" switch became an entry of
            // floatingButtonShowWhen (issue #439), and that entry went with the keyboard. Runs on load and
            // on a backup import alike.
            "dictate__floating_button_show_with_dictate_keyboard" -> {
                entry.transform(
                    type = PreferenceType.string(),
                    key = "dictate__floating_button_show_when",
                    rawValue = DictateFloatingButtonShowWhen.fromRemovedKeyboardOption().name,
                )
            }
            // The keyboard's hold-to-record switch and its one-time default flip, replaced by recordMode.
            "dictate__push_to_talk", "dictate__push_to_talk_default_migrated" -> entry.reset()

            "dictate__floating_button_show_when" -> {
                if (entry.rawValue == "ALSO_WITH_DICTATE_KEYBOARD") {
                    entry.transform(rawValue = DictateFloatingButtonShowWhen.fromRemovedKeyboardOption().name)
                } else {
                    entry.keepAsIs()
                }
            }

            // Default: keep entry
            else -> entry.keepAsIs()
        }
    }
}

object ColorPreferenceSerializer : PreferenceSerializer<Color> {
    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(value: String): Color {
        return Color(value.hexToULong())
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun serialize(value: Color): String = value.value.toHexString()
}
