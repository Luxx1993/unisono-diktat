/*
 * Copyright (C) 2026 Luxx1993
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

/**
 * [DictationSink] of a dictation made as a note: there is no field to write into and none to read, so every
 * write is a success that does nothing. The finished text goes to the notes folder and the clipboard instead
 * ([DictateController] does that once the text is complete, in one place).
 */
object NoteSink : DictationSink {
    override fun commitText(text: String, verify: Boolean): Boolean = true
    override fun selectedText(): String = ""
    override fun fullText(): String = ""
    override fun selectAll() = Unit
    override fun textBeforeCursor(length: Int): String? = null
    override fun performEnter(): Boolean = true
    override fun deleteLastText(text: String): Boolean = false
    override fun setDictationPreview(newText: String, prevText: String) = Unit
    override fun commitDictationFinal(finalText: String, prevText: String): Boolean = true
    override fun clearDictationPreview(prevText: String) = Unit
}
