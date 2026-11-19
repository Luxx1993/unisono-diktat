/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prefs

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.provider.DictateProxyType
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.LEGACY_ASSEMBLY_SYNC_ID
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.ProxyConfig
import java.net.Proxy
import java.util.Locale

/**
 * One-time import of the legacy Dictate transcription settings (provider, API key, model) into the
 * unified JetPref store, so upgrading users keep their configuration after the in-place update while
 * everything is edited in one place going forward (see `docs/COMPATIBILITY.md`).
 *
 * Idempotent: guarded by `prefs.dictate.legacyImported`, which is set on the first run regardless of
 * whether legacy data was present (fresh installs simply mark it done).
 *
 * Must be called only after [FlorisPreferenceStore] has finished loading.
 */
object DictateLegacyMigrator {

    @Suppress("DEPRECATION") // writes the deprecated flat prefs that migrateProviderKeyringIfNeeded folds in
    suspend fun migrateIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.legacyImported.get()) return

        val legacy = DictateLegacyPreferences(context.applicationContext)
        if (legacy.isPresent()) {
            val s = legacy.readSnapshot()

            // Legacy provider index: 0 = OpenAI, 1 = Groq, 2 = Custom.
            val providerId = when (s.transcriptionProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.transcriptionProviderId.set(providerId)

            s.effectiveTranscriptionApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.apiKey.set(it) }

            val model = when (s.transcriptionProvider) {
                1 -> s.transcriptionGroqModel
                2 -> s.transcriptionCustomModel
                else -> s.transcriptionOpenaiModel
            }
            model?.takeIf { it.isNotBlank() }?.let { prefs.dictate.transcriptionModel.set(it) }

            s.transcriptionCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.customBaseUrl.set(it) }

            // --- Rewording / GPT settings (roadmap section 4). The prompts themselves live in the
            // shared prompts.db and carry over automatically; only these settings need importing. ---
            prefs.dictate.rewordingEnabled.set(s.rewordingEnabled)
            prefs.dictate.autoFormattingEnabled.set(s.autoFormattingEnabled)

            val rewordingProviderId = when (s.rewordingProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.rewordingProviderId.set(rewordingProviderId)

            s.effectiveRewordingApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.rewordingApiKey.set(it) }

            val rewordingModel = when (s.rewordingProvider) {
                1 -> s.rewordingGroqModel
                2 -> s.rewordingCustomModel
                else -> s.rewordingOpenaiModel
            }
            rewordingModel?.takeIf { it.isNotBlank() }?.let { prefs.dictate.rewordingModel.set(it) }

            s.rewordingCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.rewordingCustomBaseUrl.set(it) }

            // --- Per-provider credential carry-over. The legacy app stored a *separate* API key (and
            // model) for each provider, but the imports above only fold the *active* provider's key into
            // the flat prefs. Seed the keyring directly from every stored provider key so a user who had
            // configured e.g. both an OpenAI and a Groq key keeps both after the update. The transcription
            // key takes precedence over the rewording one for the same provider; the per-provider keys are
            // never cross-filled with the old global `api_key`, which belonged to a single configured
            // provider. The keyring migration that runs next ([migrateProviderKeyringIfNeeded]) refines the
            // *active* accounts (models, custom-host split) on top of this seed.
            var keyring = prefs.dictate.providerAccounts.get()
            keyring = keyring.seedAccount(
                providerId = "openai",
                apiKey = s.transcriptionApiKeyOpenai ?: s.rewordingApiKeyOpenai,
                transcriptionModel = s.transcriptionOpenaiModel,
                chatModel = s.rewordingOpenaiModel,
            )
            keyring = keyring.seedAccount(
                providerId = "groq",
                apiKey = s.transcriptionApiKeyGroq ?: s.rewordingApiKeyGroq,
                transcriptionModel = s.transcriptionGroqModel,
                chatModel = s.rewordingGroqModel,
            )
            keyring = keyring.seedAccount(
                providerId = ProviderAccount.LEGACY_CUSTOM_ID,
                apiKey = s.transcriptionApiKeyCustom ?: s.rewordingApiKeyCustom,
                transcriptionModel = s.transcriptionCustomModel,
                chatModel = s.rewordingCustomModel,
                customBaseUrl = s.transcriptionCustomHost ?: s.rewordingCustomHost,
            )
            prefs.dictate.providerAccounts.set(keyring)

            prefs.dictate.systemPromptSelection.set(s.systemPromptSelection)
            s.systemPromptCustomText?.let { prefs.dictate.systemPromptCustom.set(it) }
            prefs.dictate.stylePromptSelection.set(s.stylePromptSelection)
            s.stylePromptCustomText?.let { prefs.dictate.stylePromptCustom.set(it) }

            // --- Output behavior (roadmap section 10) ---
            prefs.dictate.autoEnter.set(s.autoEnter)
            prefs.dictate.instantOutput.set(s.instantOutput)
            prefs.dictate.outputSpeed.set(s.outputSpeed)
            prefs.dictate.resendButton.set(s.resendButton)

            // --- Recording capture toggles (roadmap 11.7). These were read from the legacy snapshot
            // but previously never written, so an upgrading user silently lost them. ---
            prefs.dictate.audioFocus.set(s.audioFocus)
            prefs.dictate.useBluetoothMic.set(s.useBluetoothMic)

            // --- Dictation languages (roadmap 11.7). The legacy value is an *unordered* StringSet, so
            // rebuild a stable order ("detect" first, then the rest sorted) and map the legacy active
            // index onto it as a best effort. Previously neither the selection nor the active language
            // was migrated, resetting the user back to the default {detect,en}. ---
            val orderedLanguages = buildList {
                if (s.inputLanguages.contains("detect")) add("detect")
                addAll(s.inputLanguages.filter { it != "detect" }.sorted())
            }
            if (orderedLanguages.isNotEmpty()) {
                prefs.dictate.inputLanguages.set(orderedLanguages.joinToString(","))
                prefs.dictate.activeInputLanguage.set(
                    orderedLanguages.getOrNull(s.inputLanguagePos) ?: orderedLanguages.first(),
                )
            }

            // --- App UI language (roadmap 11.7): legacy "system" maps to FlorisBoard's "auto". ---
            prefs.other.settingsLanguage.set(if (s.appLanguage == "system") "auto" else s.appLanguage)

            // --- Accent color: the legacy app had a single user-pickable accent (ARGB int, key
            // "net.devemperor.dictate.accent_color") that tinted the keyboard prompt UI. It was read
            // into the snapshot but never applied, so upgraders lost their familiar color. Carry it
            // over to both new accent prefs so the look stays identical: theme.accentColor drives the
            // keyboard (FlorisImeTheme), other.accentColor the settings app. ---
            val legacyAccent = Color(s.accentColor)
            prefs.other.accentColor.set(legacyAccent)

            // --- Network proxy (roadmap 5.6): the legacy app stored one combined spec string
            // ("socks5|http://user:pass@host:port"); split it into the new structured fields. ---
            prefs.dictate.proxyEnabled.set(s.proxyEnabled)
            ProxyConfig.parse(s.proxyHost)?.let { proxy ->
                prefs.dictate.proxyType.set(
                    if (proxy.type == Proxy.Type.SOCKS) DictateProxyType.SOCKS5 else DictateProxyType.HTTP,
                )
                prefs.dictate.proxyHost.set(proxy.host)
                prefs.dictate.proxyPort.set(proxy.port.toString())
                proxy.username?.let { prefs.dictate.proxyUsername.set(it) }
                proxy.password?.let { prefs.dictate.proxyPassword.set(it) }
            }
        }

        prefs.dictate.legacyImported.set(true)
    }

    /**
     * On the first run of a fresh install, adds the device's system language to the dictation language
     * selection (on top of the default `{detect, en}`) so a non-English user can dictate in their own
     * language straight away without digging through settings. Only an *untouched* default selection is
     * augmented, so legacy upgraders (whose languages were already imported above) and users who have
     * customised the list are left alone. Idempotent via `prefs.dictate.inputLanguagesSeeded`.
     */
    suspend fun seedDeviceLanguageIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.inputLanguagesSeeded.get()) return
        if (prefs.dictate.inputLanguages.get() == "detect,en") {
            val device = DictateLanguages.matchDevice(Locale.getDefault())
            if (device != null && device.code != "en" && device.code != DictateLanguages.DETECT) {
                prefs.dictate.inputLanguages.set("detect,en,${device.code}")
            }
        }
        prefs.dictate.inputLanguagesSeeded.set(true)
    }

    /**
     * One-time fold of the deprecated flat credential prefs (api key, models, custom base URLs) into
     * the per-provider keyring ([ProviderAccounts]). Runs after [migrateIfNeeded], so it covers both
     * legacy-Java upgraders (whose flat prefs were just populated above) and existing fork users (who
     * already had flat prefs from an earlier build). Idempotent via `providerAccountsMigrated`.
     *
     * Each provider keeps one account holding its key plus separate transcription/chat models. If the
     * rewording side used a *different* custom host than the transcription side, it gets its own
     * `custom:<uuid>` account so the two base URLs don't collide.
     */
    @Suppress("DEPRECATION")
    suspend fun migrateProviderKeyringIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.providerAccountsMigrated.get()) return

        var keyring = prefs.dictate.providerAccounts.get()

        // --- Transcription side -> its active provider id ---
        val tProviderId = prefs.dictate.transcriptionProviderId.get()
        val tKey = prefs.dictate.apiKey.get()
        val tModel = prefs.dictate.transcriptionModel.get()
        val tBaseUrl = prefs.dictate.customBaseUrl.get()
        keyring = keyring.edit(tProviderId) { account ->
            account.copy(
                apiKey = tKey.ifBlank { account.apiKey },
                transcriptionModel = tModel.ifBlank { account.transcriptionModel },
                customBaseUrl = tBaseUrl.ifBlank { account.customBaseUrl },
            )
        }

        // --- Rewording side -> its active provider id (may equal the transcription one) ---
        var rProviderId = prefs.dictate.rewordingProviderId.get()
        val rKey = prefs.dictate.rewordingApiKey.get()
        val rModel = prefs.dictate.rewordingModel.get()
        val rBaseUrl = prefs.dictate.rewordingCustomBaseUrl.get()

        // If both sides are "custom" but point at different hosts, split the rewording one off into its
        // own custom account so each keeps its correct base URL.
        if (rProviderId == "custom" && tProviderId == "custom" &&
            rBaseUrl.isNotBlank() && tBaseUrl.isNotBlank() && rBaseUrl != tBaseUrl
        ) {
            val splitId = ProviderAccount.newCustomId()
            keyring = keyring.edit(splitId) { account ->
                account.copy(
                    apiKey = rKey.ifBlank { keyring.getOrEmpty("custom").apiKey },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl,
                )
            }
            rProviderId = splitId
            prefs.dictate.rewordingProviderId.set(splitId)
        } else {
            keyring = keyring.edit(rProviderId) { account ->
                account.copy(
                    // Blank legacy rewording key historically meant "reuse the transcription key".
                    apiKey = rKey.ifBlank { account.apiKey.ifBlank { if (rProviderId == tProviderId) tKey else account.apiKey } },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl.ifBlank { account.customBaseUrl },
                )
            }
        }

        prefs.dictate.providerAccounts.set(keyring)
        prefs.dictate.providerAccountsMigrated.set(true)
    }

    /**
     * Merges the former separate "AssemblyAI Sync" entry into AssemblyAI (October 2026): the key moves to the
     * AssemblyAI account, and if the sync entry was the dictation provider, AssemblyAI is now selected with
     * its `-sync` model. Runs on every start but only acts while an `assemblyai_sync` account or pointer exists.
     */
    suspend fun mergeAssemblySyncIfNeeded() {
        val prefs by FlorisPreferenceStore
        val active = prefs.dictate.transcriptionProviderId.get() == LEGACY_ASSEMBLY_SYNC_ID
        val keyring = prefs.dictate.providerAccounts.get()
        if (!active && keyring[LEGACY_ASSEMBLY_SYNC_ID] == null) return
        prefs.dictate.providerAccounts.set(keyring.foldAssemblySync(syncWasActive = active))
        if (active) prefs.dictate.transcriptionProviderId.set(ProviderRegistry.ASSEMBLYAI.id)
    }

    /**
     * Moves stored models that their provider switched off onto the successor (November 2026), see
     * [ProviderAccounts.replaceRetiredModels]. Runs on every start and writes only when something changed.
     */
    suspend fun replaceRetiredModelsIfNeeded() {
        val prefs by FlorisPreferenceStore
        val keyring = prefs.dictate.providerAccounts.get()
        val updated = keyring.replaceRetiredModels()
        if (updated !== keyring) prefs.dictate.providerAccounts.set(updated)
    }

    /**
     * Adds the five styles of Entwurf 3 (Aufräumen, Wörtlich, E-Mail, Chat, Aktenvermerk) once to an install
     * whose prompt database predates them; a fresh one is seeded with them when the database is created.
     * The user's own prompts stay as they are, see [PromptsDatabaseHelper.addMissingStyles]. Idempotent via
     * `prefs.dictate.stylesSeeded`.
     */
    suspend fun seedStylesIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.stylesSeeded.get()) return
        val activated = withContext(Dispatchers.IO) {
            runCatching { PromptsDatabaseHelper.getInstance(context).addMissingStyles() }
        }
        // A failed database write is tried again on the next start rather than marked as done.
        if (activated.isFailure) return
        activated.getOrNull()?.let { prefs.dictate.activeStyleId.set(it) }
        prefs.dictate.stylesSeeded.set(true)
    }

    /**
     * Moves "Trust user certificates" from the app onto each account (issue #383).
     *
     * Every account that existed while the app-wide switch was on keeps trusting them, so nothing that
     * worked stops working: someone behind a company's TLS-inspecting proxy needed it for every provider.
     * What changes is the next account, which starts without and has to be switched on for itself.
     * Idempotent: the old switch is turned off once its value has moved.
     */
    suspend fun migrateTrustUserCertsToAccountsIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (!prefs.dictate.trustUserCertificates.get()) return
        val keyring = prefs.dictate.providerAccounts.get()
        prefs.dictate.providerAccounts.set(
            keyring.copy(accounts = keyring.accounts.mapValues { (_, account) -> account.copy(trustUserCerts = true) }),
        )
        prefs.dictate.trustUserCertificates.set(false)
    }

    /**
     * Folds a single legacy provider's stored credentials into [this] keyring. No-op (returns the keyring
     * unchanged) when there is no usable key, so providers the user never configured don't create empty
     * accounts. Existing non-blank fields are never overwritten, making it safe to run before the active
     * provider's flat prefs are folded in by [migrateProviderKeyringIfNeeded].
     */
    private fun ProviderAccounts.seedAccount(
        providerId: String,
        apiKey: String?,
        transcriptionModel: String? = null,
        chatModel: String? = null,
        customBaseUrl: String? = null,
    ): ProviderAccounts {
        val key = apiKey?.takeIf { it.isNotBlank() && it != "NO_API_KEY" } ?: return this
        return edit(providerId) { account ->
            account.copy(
                apiKey = account.apiKey.ifBlank { key },
                transcriptionModel = account.transcriptionModel.ifBlank { transcriptionModel.orEmpty() },
                chatModel = account.chatModel.ifBlank { chatModel.orEmpty() },
                customBaseUrl = account.customBaseUrl.ifBlank { customBaseUrl.orEmpty() },
            )
        }
    }
}
