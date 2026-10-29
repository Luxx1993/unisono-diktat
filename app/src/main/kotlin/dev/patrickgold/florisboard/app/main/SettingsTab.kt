/*
 * Copyright (C) 2026 Luxx1993 (diktat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState as collectFlowAsState
import dev.patrickgold.florisboard.app.PreferenceRecovery
import org.florisboard.lib.android.showLongToast
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.apptheme.LocalUnisono
import dev.patrickgold.florisboard.app.apptheme.Unisono
import dev.patrickgold.florisboard.app.settings.dictate.isOverlayServiceEnabled
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.DictateRecordMode
import dev.patrickgold.florisboard.dictate.importer.TranscribeShareActivity
import dev.patrickgold.florisboard.dictate.provider.ProviderListing
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Einstellungen (Entwurf 3, `Einstellungen.html`): the floating button and its colour, then the dictation
 * settings and the rest in two groups. Each row opens the existing settings screen behind it; everything
 * else is under "Alle Einstellungen".
 */
@Composable
internal fun SettingsTab() {
    val prefs by FlorisPreferenceStore
    val c = LocalUnisono.current
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    Text(stringRes(R.string.main__tab_settings), style = Unisono.PageTitle, color = c.textPrimary)

    // The search, as the pill it is on the other pages.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(Unisono.Pill)
            .background(c.sunken)
            .clickable { navController.navigate(Routes.Settings.Search) }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(UnisonoIcons.Search, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
        Text(stringRes(R.string.settings__search__placeholder), style = Unisono.Body, color = c.textTertiary)
    }

    // Settings that could not be read at start were kept instead of written over (#383). This is the way
    // back to them, offered until they are restored or discarded.
    val settingsUnreadable by PreferenceRecovery.pending.collectFlowAsState()
    var showRecoveryDialog by remember { mutableStateOf(false) }
    if (settingsUnreadable) {
        UCard {
            UNavRow(
                title = stringRes(R.string.dictate__settings_unreadable_card),
                onClick = { showRecoveryDialog = true },
            )
        }
    }
    if (showRecoveryDialog) {
        UDialog(
            title = stringRes(R.string.dictate__settings_unreadable_title),
            description = stringRes(R.string.dictate__settings_unreadable_message),
            doneLabel = stringRes(R.string.action__cancel),
            onDismiss = { showRecoveryDialog = false },
        ) {
            UNavRow(
                title = stringRes(R.string.action__restore),
                onClick = {
                    showRecoveryDialog = false
                    scope.launch {
                        PreferenceRecovery.restore(context).onFailure {
                            context.showLongToast(R.string.dictate__settings_restore_failed)
                        }
                    }
                },
            )
            UDivider()
            // Delete, not Discard: several translations of Discard read the same as Cancel beside it.
            UNavRow(
                title = stringRes(R.string.action__delete),
                onClick = {
                    showRecoveryDialog = false
                    PreferenceRecovery.discard(context)
                },
            )
        }
    }

    // System state the user changes in Android's own settings, read again whenever the app comes back.
    var serviceEnabled by remember { mutableStateOf(isOverlayServiceEnabled(context)) }
    var voiceInputEnabled by remember { mutableStateOf(isVoiceInputEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isOverlayServiceEnabled(context)
                voiceInputEnabled = isVoiceInputEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val floatingEnabled by prefs.dictate.floatingButtonEnabled.collectAsState()
    UCard {
        val floatingLabel = stringRes(R.string.main__settings_floating)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Routes.Settings.DictateFloatingButton) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val design by prefs.dictate.floatingButtonDesign.collectAsState()
            BubblePreview(design, size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(floatingLabel, style = Unisono.BodyStrong.copy(fontSize = 17.sp, lineHeight = 24.sp), color = c.textPrimary)
                Text(
                    stringRes(
                        if (serviceEnabled) R.string.main__settings_floating_on else R.string.main__settings_floating_service_off,
                    ),
                    style = Unisono.Label,
                    color = c.textSecondary,
                )
            }
            USwitch(
                checked = floatingEnabled,
                onCheckedChange = { on ->
                    scope.launch { prefs.dictate.floatingButtonEnabled.set(on) }
                    // Without the accessibility service the switch alone shows nothing; its screen explains it.
                    if (on && !serviceEnabled) navController.navigate(Routes.Settings.DictateFloatingButton)
                },
                contentDescription = floatingLabel,
            )
        }
        UDivider()
        PaletteRow()
    }

    UGroupLabel(stringRes(R.string.main__settings_group_dictation))
    val languagesRaw by prefs.dictate.inputLanguages.collectAsState()
    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val transcriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
    val rewordingId by prefs.dictate.rewordingProviderId.collectAsState()
    val rewordingEnabled by prefs.dictate.rewordingEnabled.collectAsState()
    val recordMode by prefs.dictate.recordMode.collectAsState()
    val longform by prefs.dictate.longformMode.collectAsState()
    UCard {
        UNavRow(
            title = stringRes(R.string.main__settings_languages),
            subtitle = remember(languagesRaw) {
                DictateLanguages.parseSelection(languagesRaw).joinToString(", ") { it.displayName() }
            },
            icon = UnisonoIcons.Globe,
            onClick = { navController.navigate(Routes.Settings.DictateLanguages) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_providers),
            subtitle = buildString {
                append(ProviderListing.displayNameOf(transcriptionId, accounts))
                if (rewordingEnabled) append(" · ").append(ProviderListing.displayNameOf(rewordingId, accounts))
            },
            icon = UnisonoIcons.Cloud,
            onClick = { navController.navigate(Routes.Settings.DictateProviders) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_recording),
            subtitle = stringRes(
                when (recordMode) {
                    DictateRecordMode.TAP -> R.string.dictate__record_mode_tap_short
                    DictateRecordMode.HOLD -> R.string.dictate__record_mode_hold_short
                },
            ) + if (longform.isEnabled && recordMode == DictateRecordMode.TAP) {
                " · " + stringRes(R.string.dictate__longform_title)
            } else {
                ""
            },
            icon = UnisonoIcons.Wave,
            onClick = { navController.navigate(Routes.Settings.DictateRecording) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.dictate__formatting_title),
            subtitle = stringRes(R.string.dictate__formatting_menu_summary),
            icon = UnisonoIcons.Book,
            onClick = { navController.navigate(Routes.Settings.DictateFormatting) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.dictate__rewording_title),
            subtitle = stringRes(
                if (rewordingEnabled) R.string.dictate__rewording_summary_on
                else R.string.dictate__rewording_summary_off,
            ),
            icon = UnisonoIcons.Sparkle,
            onClick = { navController.navigate(Routes.Settings.DictateRewording) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.dictate__output_group),
            subtitle = stringRes(R.string.dictate__output_menu_summary),
            icon = UnisonoIcons.Keyboard,
            onClick = { navController.navigate(Routes.Settings.DictateOutput) },
        )
    }

    UGroupLabel(stringRes(R.string.main__settings_group_more))
    var showVoiceInput by remember { mutableStateOf(false) }
    UCard {
        UNavRow(
            title = stringRes(R.string.dictate__stats_title),
            subtitle = stringRes(R.string.dictate__stats_menu_summary),
            icon = UnisonoIcons.Wave,
            onClick = { navController.navigate(Routes.Settings.DictateStats) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.dictate__notes_title),
            icon = UnisonoIcons.Note,
            onClick = { navController.navigate(Routes.Settings.DictateNotes) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_history),
            icon = UnisonoIcons.Clock,
            onClick = { navController.navigate(Routes.Settings.DictateHistory) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_transcribe),
            icon = UnisonoIcons.AudioFile,
            onClick = { context.startActivity(TranscribeShareActivity.pickIntent(context)) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_voice_input),
            subtitle = stringRes(
                if (voiceInputEnabled) R.string.main__settings_voice_input_on else R.string.main__settings_voice_input_off,
            ),
            icon = UnisonoIcons.Keyboard,
            onClick = { showVoiceInput = true },
        )
    }

    UGroupLabel(stringRes(R.string.main__settings_group_app))
    var showBackup by remember { mutableStateOf(false) }
    UCard {
        UNavRow(
            title = stringRes(R.string.settings__other__title),
            icon = UnisonoIcons.Gear,
            onClick = { navController.navigate(Routes.Settings.Other) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.main__settings_backup),
            icon = UnisonoIcons.Archive,
            onClick = { showBackup = true },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__title),
            icon = UnisonoIcons.Book,
            onClick = { navController.navigate(Routes.Settings.About) },
        )
    }

    if (showVoiceInput) {
        AlertDialog(
            onDismissRequest = { showVoiceInput = false },
            title = { Text(stringRes(R.string.main__settings_voice_input)) },
            text = { Text(stringRes(R.string.main__settings_voice_input_dialog)) },
            confirmButton = {
                TextButton(onClick = {
                    showVoiceInput = false
                    context.startActivity(
                        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringRes(R.string.main__settings_voice_input_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceInput = false }) { Text(stringRes(R.string.action__cancel)) }
            },
        )
    }
    if (showBackup) {
        AlertDialog(
            onDismissRequest = { showBackup = false },
            title = { Text(stringRes(R.string.main__settings_backup)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackup = false
                    navController.navigate(Routes.Settings.Backup)
                }) { Text(stringRes(R.string.backup_and_restore__back_up__title)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackup = false
                    navController.navigate(Routes.Settings.Restore)
                }) { Text(stringRes(R.string.backup_and_restore__restore__title)) }
            },
        )
    }
}

/** Whether this app's voice input method is switched on in Android, which SwiftKey's microphone key uses. */
private fun isVoiceInputEnabled(context: Context): Boolean {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
    return runCatching { imm.enabledInputMethodList.any { it.packageName == context.packageName } }.getOrDefault(false)
}
