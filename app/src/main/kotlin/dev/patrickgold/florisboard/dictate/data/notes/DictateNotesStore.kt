/*
 * Copyright (C) 2026 Luxx1993
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.notes

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A voice note: what was said, as text, and when. There is no audio. The id is the creation time in
 * milliseconds (bumped by one if two notes land in the same millisecond), which also keeps the list in order.
 */
@Serializable
data class DictateNote(val id: Long, val text: String, val createdAt: Long)

/** The list operations of the notes folder, free of files and Android so they can be tested directly. */
object DictateNoteList {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(val notes: List<DictateNote> = emptyList())

    /** Newest first. Blank text is never a note. */
    fun withNew(list: List<DictateNote>, text: String, now: Long): List<DictateNote> {
        val clean = text.trim()
        if (clean.isEmpty()) return list
        val id = maxOf(now, (list.maxOfOrNull { it.id } ?: Long.MIN_VALUE) + 1)
        return listOf(DictateNote(id = id, text = clean, createdAt = now)) + list
    }

    fun without(list: List<DictateNote>, id: Long): List<DictateNote> = list.filterNot { it.id == id }

    fun encode(list: List<DictateNote>): String = json.encodeToString(Stored.serializer(), Stored(list))

    /** A file that cannot be read is an empty folder, not a crash. */
    fun decode(text: String): List<DictateNote> =
        runCatching { json.decodeFromString(Stored.serializer(), text).notes }.getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
}

/**
 * The notes folder: every dictation made as a note (the floating button's menu, "Notiz diktieren"), kept as text in
 * one small file in the app's private storage. It is the backup for the clipboard, which Android may clear.
 */
object DictateNotesStore {

    private const val FILE_NAME = "dictate_notes.json"

    private val state = MutableStateFlow<List<DictateNote>>(emptyList())
    private val mutex = Mutex()
    @Volatile private var loaded = false

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /** The notes, newest first; loaded from disk on first use. */
    fun flow(context: Context): StateFlow<List<DictateNote>> {
        ensureLoaded(context)
        return state
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val f = file(context)
            state.value = if (f.exists()) DictateNoteList.decode(f.readText()) else emptyList()
            loaded = true
        }
    }

    /** Saves [text] as the newest note; returns it, or null when there was nothing to save. */
    suspend fun add(context: Context, text: String): DictateNote? {
        var created: DictateNote? = null
        change(context) { list ->
            val next = DictateNoteList.withNew(list, text, System.currentTimeMillis())
            created = next.firstOrNull()?.takeIf { n -> list.none { it.id == n.id } }
            next
        }
        return created
    }

    suspend fun delete(context: Context, id: Long) {
        change(context) { DictateNoteList.without(it, id) }
    }

    suspend fun deleteAll(context: Context) {
        change(context) { emptyList() }
    }

    private suspend fun change(context: Context, block: (List<DictateNote>) -> List<DictateNote>) {
        ensureLoaded(context)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = block(state.value)
                if (next == state.value) return@withLock
                val f = file(context)
                val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(DictateNoteList.encode(next))
                if (!tmp.renameTo(f)) {
                    f.writeText(tmp.readText())
                    tmp.delete()
                }
                state.value = next
            }
        }
    }
}
