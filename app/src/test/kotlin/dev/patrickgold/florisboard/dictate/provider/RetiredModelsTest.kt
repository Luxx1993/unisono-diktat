/*
 * Copyright (C) 2026 Luxx1993
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RetiredModelsTest {

    @Test
    fun `a keyring without retired models stays the same instance`() {
        val keyring = ProviderAccounts().put(
            ProviderAccount(providerId = "mistral", transcriptionModel = "voxtral-mini-transcribe-3-0", chatModel = "mistral-small-2603"),
        )
        assertSame(keyring, keyring.replaceRetiredModels())
    }

    @Test
    fun `retired models move to their successor, per field`() {
        val keyring = ProviderAccounts()
            .put(ProviderAccount(providerId = "mistral", transcriptionModel = "voxtral-mini-latest",
                realtimeModel = "voxtral-mini-transcribe-realtime-2602", chatModel = "mistral-small-2603"))
            .put(ProviderAccount(providerId = "soniox", transcriptionModel = "stt-async-v5", realtimeModel = "stt-rt-v5"))
            .put(ProviderAccount(providerId = "gemini", chatModel = "gemini-3.1-flash-lite"))
            .put(ProviderAccount(providerId = "openai", transcriptionModel = "whisper-1", chatModel = "gpt-6-luna"))
            .put(ProviderAccount(providerId = "ovhcloud", chatModel = "Mistral-Small-3.2-24B-Instruct-2506"))
            .replaceRetiredModels()
        val mistral = keyring.getOrEmpty("mistral")
        assertEquals("voxtral-mini-transcribe-3-0", mistral.transcriptionModel)
        assertEquals("voxtral-mini-transcribe-realtime-3-0", mistral.realtimeModel)
        assertEquals("mistral-small-2603", mistral.chatModel)
        assertEquals("stt-async-v6", keyring.getOrEmpty("soniox").transcriptionModel)
        assertEquals("stt-rt-v6", keyring.getOrEmpty("soniox").realtimeModel)
        assertEquals("gemini-3.5-flash-lite", keyring.getOrEmpty("gemini").chatModel)
        assertEquals("gpt-oss-20b", keyring.getOrEmpty("ovhcloud").chatModel)
        assertEquals("gpt-transcribe", keyring.getOrEmpty("openai").transcriptionModel)
        assertEquals("gpt-6-luna", keyring.getOrEmpty("openai").chatModel)
    }

    @Test
    fun `the AssemblyAI sync variant keeps its suffix`() {
        val keyring = ProviderAccounts()
            .put(ProviderAccount(providerId = "assemblyai", transcriptionModel = "universal-3-pro${ProviderRegistry.ASSEMBLYAI_SYNC_SUFFIX}"))
            .replaceRetiredModels()
        assertEquals("universal-3-5-pro${ProviderRegistry.ASSEMBLYAI_SYNC_SUFFIX}", keyring.getOrEmpty("assemblyai").transcriptionModel)
    }

    @Test
    fun `the same model id at another provider is left alone`() {
        val keyring = ProviderAccounts().put(ProviderAccount(providerId = "openrouter", transcriptionModel = "voxtral-mini-latest"))
        assertSame(keyring, keyring.replaceRetiredModels())
    }
}
