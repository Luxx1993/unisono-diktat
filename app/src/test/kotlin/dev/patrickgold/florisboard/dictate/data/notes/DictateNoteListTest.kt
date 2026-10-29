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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictateNoteListTest {

    @Test
    fun `a new note goes to the top and its text is trimmed`() {
        val first = DictateNoteList.withNew(emptyList(), "  Erste Idee  ", now = 1000)
        val both = DictateNoteList.withNew(first, "Zweite Idee", now = 2000)
        assertEquals(listOf("Zweite Idee", "Erste Idee"), both.map { it.text })
        assertEquals(1000, both.last().createdAt)
    }

    @Test
    fun `blank text is no note`() {
        assertTrue(DictateNoteList.withNew(emptyList(), "   ", now = 1).isEmpty())
    }

    @Test
    fun `two notes in the same millisecond get different ids`() {
        val list = DictateNoteList.withNew(DictateNoteList.withNew(emptyList(), "a", 500), "b", 500)
        assertEquals(2, list.map { it.id }.toSet().size)
    }

    @Test
    fun `a note can be removed by id`() {
        val list = DictateNoteList.withNew(DictateNoteList.withNew(emptyList(), "a", 1), "b", 2)
        assertEquals(listOf("a"), DictateNoteList.without(list, list.first().id).map { it.text })
    }

    @Test
    fun `the list survives the file and a broken file is an empty folder`() {
        val list = DictateNoteList.withNew(DictateNoteList.withNew(emptyList(), "ä ö ü \"Zitat\"\nZeile 2", 1), "b", 2)
        assertEquals(list, DictateNoteList.decode(DictateNoteList.encode(list)))
        assertTrue(DictateNoteList.decode("{kaputt").isEmpty())
    }
}
