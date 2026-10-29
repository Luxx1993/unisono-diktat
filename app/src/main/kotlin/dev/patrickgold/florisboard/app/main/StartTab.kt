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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.launch
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.apptheme.LocalUnisono
import dev.patrickgold.florisboard.app.apptheme.Unisono
import dev.patrickgold.florisboard.app.apptheme.providerDot
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryEntry
import dev.patrickgold.florisboard.dictate.data.history.DictateHistoryStore
import dev.patrickgold.florisboard.dictate.data.notes.DictateNotesStore
import dev.patrickgold.florisboard.dictate.data.stats.DictateStats
import dev.patrickgold.florisboard.dictate.provider.ProviderListing
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.stringRes
import java.text.NumberFormat

/** How many dictations the start tab lists; the rest is one tap away in the history. */
private const val RECENT_COUNT = 3

/**
 * Start (Entwurf 3, `Main.html`): greeting, three numbers, the two active providers, the last dictations
 * with a copy button each.
 */
@Composable
internal fun StartTab() {
    val prefs by FlorisPreferenceStore
    val c = LocalUnisono.current
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    // The greeting is where the name is set: tap it. Without a name a quiet hint says so.
    val name by prefs.other.userName.collectAsState()
    var editingName by remember { mutableStateOf(false) }
    UPageHeader(
        title = if (name.isBlank()) {
            stringRes(R.string.main__greeting)
        } else {
            stringRes(R.string.main__greeting_named, "name" to name.trim())
        },
        subtitle = stringRes(R.string.main__start_subtitle),
        onTitleClick = { editingName = true },
    )
    if (name.isBlank()) {
        Text(
            stringRes(R.string.main__greeting_set_name),
            modifier = Modifier
                .offset(y = (-16).dp)
                .clip(Unisono.RadiusMd)
                .clickable { editingName = true }
                .padding(vertical = 4.dp),
            style = Unisono.Caption,
            color = c.textTertiary,
        )
    }
    if (editingName) {
        NameDialog(
            initial = name,
            onDismiss = { editingName = false },
            onSave = { value ->
                editingName = false
                scope.launch { prefs.other.userName.set(value.trim()) }
            },
        )
    }

    val words by prefs.dictate.statsWords.collectAsState()
    val monthIndex by prefs.dictate.statsMonthIndex.collectAsState()
    val monthWords by prefs.dictate.statsMonthWords.collectAsState()
    val lastDay by prefs.dictate.statsLastDayEpoch.collectAsState()
    val streak by prefs.dictate.statsStreakCurrent.collectAsState()
    val numbers = NumberFormat.getIntegerInstance()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(Routes.Settings.DictateStats) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Stat(numbers.format(words), stringRes(R.string.main__stat_words_total), Modifier.weight(1f))
        Stat(
            numbers.format(DictateStats.wordsThisMonth(monthIndex, monthWords)),
            stringRes(R.string.main__stat_words_month),
            Modifier.weight(1f),
        )
        Stat(
            numbers.format(DictateStats.currentStreak(lastDay, streak)),
            stringRes(R.string.main__stat_streak),
            Modifier.weight(1f),
        )
    }

    val accounts by prefs.dictate.providerAccounts.collectAsState()
    val transcriptionId by prefs.dictate.transcriptionProviderId.collectAsState()
    val rewordingId by prefs.dictate.rewordingProviderId.collectAsState()
    val rewordingEnabled by prefs.dictate.rewordingEnabled.collectAsState()
    UCard {
        ProviderRow(
            icon = UnisonoIcons.Mic,
            label = stringRes(R.string.main__provider_transcription),
            value = ProviderListing.displayNameOf(transcriptionId, accounts),
            dot = c.providerDot(transcriptionId),
            onClick = { navController.navigate(Routes.Settings.DictateProviders) },
        )
        UDivider()
        ProviderRow(
            icon = UnisonoIcons.Sparkle,
            label = stringRes(R.string.main__provider_cleanup),
            value = if (rewordingEnabled) {
                ProviderListing.displayNameOf(rewordingId, accounts)
            } else {
                stringRes(R.string.main__provider_off)
            },
            dot = if (rewordingEnabled) c.providerDot(rewordingId) else c.textTertiary,
            onClick = { navController.navigate(Routes.Settings.DictateProviders) },
        )
    }

    val noteCount by remember { DictateNotesStore.flow(context) }.collectAsState()
    UCard {
        UNavRow(
            title = stringRes(R.string.dictate__notes_title),
            subtitle = if (noteCount.isEmpty()) null else stringRes(R.string.dictate__notes_count, "count" to noteCount.size),
            icon = UnisonoIcons.Note,
            onClick = { navController.navigate(Routes.Settings.DictateNotes) },
        )
    }

    val historyFlow = remember { DictateHistoryStore.flow(context) }
    val history by historyFlow.collectAsState(initial = emptyList())
    val recent = remember(history) {
        history.filter { !it.failed && it.text.isNotBlank() }.sortedByDescending { it.createdAt }.take(RECENT_COUNT)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringRes(R.string.main__recent_title),
                modifier = Modifier.weight(1f),
                style = Unisono.SectionTitle.copy(lineHeight = 28.sp),
                color = c.textPrimary,
            )
            Text(
                stringRes(R.string.main__recent_all),
                modifier = Modifier
                    .clip(Unisono.RadiusMd)
                    .clickable { navController.navigate(Routes.Settings.DictateHistory) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                style = Unisono.Caption,
                color = c.textPrimary,
            )
        }
        if (recent.isEmpty()) {
            Text(
                stringRes(R.string.main__recent_empty),
                modifier = Modifier.padding(vertical = 12.dp),
                style = Unisono.Body,
                color = c.textSecondary,
            )
        }
        recent.forEachIndexed { i, entry ->
            RecentRow(entry, last = i == recent.lastIndex)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = LocalUnisono.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = Unisono.StatNumber.copy(letterSpacing = Unisono.PageTitle.letterSpacing), color = c.textPrimary, maxLines = 1)
        Text(label, style = Unisono.Label, color = c.textSecondary)
    }
}

@Composable
private fun ProviderRow(icon: ImageVector, label: String, value: String, dot: Color, onClick: () -> Unit) {
    val c = LocalUnisono.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Unisono.Label, color = c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UDot(dot)
                Text(
                    value,
                    style = Unisono.BodyStrong.copy(lineHeight = 24.sp),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        UChevron()
    }
}

@Composable
private fun RecentRow(entry: DictateHistoryEntry, last: Boolean) {
    val c = LocalUnisono.current
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    entry.text,
                    style = Unisono.Body,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(timeAgo(context, entry.createdAt), style = Unisono.Mono, color = c.textTertiary)
            }
            val copyLabel = stringRes(R.string.main__copy)
            val copiedLabel = stringRes(R.string.main__copied)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Unisono.RadiusMd)
                    .clickable {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        runCatching { clipboard?.setPrimaryClip(ClipData.newPlainText("diktat", entry.text)) }
                        // Android 13 and later confirm a copy themselves.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(UnisonoIcons.Copy, contentDescription = copyLabel, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderSubtle))
        }
    }
}

/** "vor 11 min", "vor 3 h", and from yesterday on the date. */
private fun timeAgo(context: Context, createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - createdAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> context.getString(R.string.main__ago_now)
        minutes < 60 -> context.getString(R.string.main__ago_minutes).replace("{n}", minutes.toString())
        minutes < 24 * 60 -> context.getString(R.string.main__ago_hours).replace("{n}", (minutes / 60).toString())
        else -> DateFormat.getMediumDateFormat(context).format(createdAt)
    }
}

/** The name the greeting uses; empty greets without one. */
@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = LocalUnisono.current
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.pref__other__user_name__label)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Unisono.RadiusMd)
                    .background(c.sunken)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(stringRes(R.string.main__greeting_name_hint), style = Unisono.Body, color = c.textTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = Unisono.Body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.textPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave(value) }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringRes(R.string.main__styles_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
    )
}
