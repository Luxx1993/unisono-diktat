/*
 * Copyright (C) 2026 Luxx1993
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState as collectFlowAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.LocalUnisono
import dev.patrickgold.florisboard.app.apptheme.Unisono
import dev.patrickgold.florisboard.app.main.UCard
import dev.patrickgold.florisboard.app.main.UDialog
import dev.patrickgold.florisboard.app.main.UDivider
import dev.patrickgold.florisboard.app.main.UNavRow
import dev.patrickgold.florisboard.app.main.USwitchRow
import dev.patrickgold.florisboard.app.main.UnisonoPage
import dev.patrickgold.florisboard.dictate.data.notes.DictateNote
import dev.patrickgold.florisboard.dictate.data.notes.DictateNotesStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * The notes folder: every dictation made as a note, as text with the date and time it was made. Each note can
 * be copied, shared or deleted; all of them can be deleted at once. There is no audio.
 */
@Composable
fun DictateNotesScreen() = UnisonoPage(title = stringRes(R.string.dictate__notes_title)) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val notes by remember { DictateNotesStore.flow(context) }.collectFlowAsState()
    val always by prefs.dictate.notesAlways.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    UCard {
        USwitchRow(
            title = stringRes(R.string.dictate__notes_always_title),
            subtitle = stringRes(R.string.dictate__notes_always_summary),
            checked = always,
            onCheckedChange = { scope.launch { prefs.dictate.notesAlways.set(it) } },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.dictate__notes_delete_all),
            subtitle = stringRes(R.string.dictate__notes_count, "count" to notes.size),
            onClick = { if (notes.isNotEmpty()) confirmDeleteAll = true },
        )
    }

    if (notes.isEmpty()) {
        Text(
            text = stringRes(R.string.dictate__notes_empty),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            style = Unisono.Body,
            color = LocalUnisono.current.textSecondary,
        )
    } else {
        UCard {
            notes.forEachIndexed { index, note ->
                if (index > 0) UDivider()
                NoteRow(
                    note = note,
                    onCopy = {
                        copyText(context, note.text)
                        Toast.makeText(context, R.string.dictate__notes_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShare = { shareText(context, note.text) },
                    onDelete = { scope.launch { DictateNotesStore.delete(context, note.id) } },
                )
            }
        }
    }

    if (confirmDeleteAll) {
        UDialog(
            title = stringRes(R.string.dictate__notes_delete_all_title),
            description = stringRes(R.string.dictate__notes_delete_all_message),
            doneLabel = stringRes(android.R.string.cancel),
            onDismiss = { confirmDeleteAll = false },
        ) {
            UNavRow(
                title = stringRes(R.string.dictate__notes_delete_all),
                onClick = {
                    scope.launch { DictateNotesStore.deleteAll(context) }
                    confirmDeleteAll = false
                },
            )
        }
    }
}

@Composable
private fun NoteRow(note: DictateNote, onCopy: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val c = LocalUnisono.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 12.dp, bottom = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = DateUtils.formatDateTime(
                    context, note.createdAt,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                ),
                style = Unisono.Mono,
                color = c.textTertiary,
            )
            SelectionContainer {
                Text(text = note.text, style = Unisono.Body, color = c.textPrimary)
            }
        }
        Column {
            IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentCopy, stringRes(R.string.dictate__notes_copy), Modifier.size(20.dp), tint = c.textSecondary)
            }
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Share, stringRes(R.string.dictate__stats_share), Modifier.size(20.dp), tint = c.textSecondary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, stringRes(R.string.dictate__notes_delete), Modifier.size(20.dp), tint = NOTE_DELETE)
            }
        }
    }
}

private val NOTE_DELETE = Color(0xFFB3443A)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("note", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
