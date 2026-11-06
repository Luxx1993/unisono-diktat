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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class ModelPricesTest : FunSpec({

    test("Solaria-3 shows its price, written the German way") {
        ModelPrices.format(ModelPrices.batchPrice("gladia", "solaria-3"), Locale.GERMANY) shouldBe "0,16 €/h"
    }

    test("a calculated price is marked and an unknown model shows nothing") {
        ModelPrices.format(ModelPrices.batchPrice("assemblyai", "universal-3-5-pro-sync"), Locale.GERMANY) shouldBe "ca. 0,4012 €/h"
        ModelPrices.format(ModelPrices.batchPrice("groq", "no-such-model")) shouldBe null
    }

    test("the exact figure shows with 2 to 4 decimals and no trailing zeros") {
        ModelPrices.format(ModelPrices.batchPrice("groq", "whisper-large-v3-turbo"), Locale.GERMANY) shouldBe "0,0357 €/h"
        ModelPrices.format(ModelPrices.batchPrice("openrouter", "openai/whisper-large-v3-turbo"), Locale.GERMANY) shouldBe "0,0107 €/h"
        ModelPrices.format(ModelPrices.Price(0.10), Locale.GERMANY) shouldBe "0,10 €/h"
        ModelPrices.format(ModelPrices.Price(0.015), Locale.GERMANY) shouldBe "0,015 €/h"
        ModelPrices.format(ModelPrices.Price(1.5), Locale.GERMANY) shouldBe "1,50 €/h"
    }

    test("text models show input and output per million tokens") {
        ModelPrices.formatChat(ModelPrices.chatPrice("anthropic", "claude-haiku-5-5"), Locale.GERMANY) shouldBe "0,0892 / 0,4458 €/M"
        ModelPrices.formatChat(ModelPrices.chatPrice("scaleway", "gpt-oss-120b"), Locale.GERMANY) shouldBe "0,15 / 0,60 €/M"
        ModelPrices.formatChat(ModelPrices.chatPrice("openrouter", "minimax/minimax-m3.1-flash:free"), Locale.GERMANY) shouldBe "0 / 0 €/M"
        ModelPrices.formatChat(ModelPrices.chatPrice("groq", "no-such-model")) shouldBe null
    }

    test("every curated text model of the paid providers has a price except the known gaps") {
        val gaps = setOf(
            "groq|qwen/qwen3.8-27b", "ollama", "siliconflow|deepseek-ai/deepseek-v3.2",
            "siliconflow|deepseek-ai/deepseek-v4.1-flash", "siliconflow|deepseek-ai/deepseek-v3.1-terminus",
        )
        val missing = ProviderRegistry.presets.flatMap { preset ->
            preset.curatedChatModels.filter { ModelPrices.chatPrice(preset.id, it) == null }.map { "${preset.id}|${it.lowercase()}" }
        }.filterNot { it in gaps }
        missing shouldBe emptyList()
    }

    test("a router adds its surcharge behind the price") {
        ModelPrices.chatText("openrouter", "anthropic/claude-haiku-5.5", Locale.GERMANY) shouldBe "0,0892 / 0,4458 €/M +5,5 %"
        ModelPrices.batchText("openrouter", "openai/whisper-large-v3-turbo", Locale.GERMANY) shouldBe "0,0107 €/h +5,5 %"
        ModelPrices.chatText("groq", "openai/gpt-oss-120b", Locale.GERMANY) shouldBe "0,1337 / 0,535 €/M"
        ModelPrices.chatText("openrouter", "no-such-model") shouldBe null
    }

    test("live and file prices are separate") {
        ModelPrices.realtimePrice("gladia", "solaria-1")?.eurPerHour shouldBe 0.68
        ModelPrices.batchPrice("gladia", "solaria-1")?.eurPerHour shouldBe 0.27
    }

    test("every curated transcription model of the paid providers has a price except the known gaps") {
        val gaps = setOf(
            "openrouter|google/gemini-3.5-transcribe", "deepgram|nova-2", "siliconflow|funaudiollm/sensevoicesmall",
            "siliconflow|teleai/teleasr-v3.2", "siliconflow|qwen/qwen3-asr-1.7b", "local|whisper-base", "local|whisper-tiny", "openai|gpt-transcribe-diarize",
        )
        val missing = ProviderRegistry.presets.flatMap { preset ->
            preset.curatedTranscriptionModels.filter { ModelPrices.batchPrice(preset.id, it) == null }
                .map { "${preset.id}|${it.lowercase()}" }
        }.filterNot { it in gaps }
        missing shouldBe emptyList()
    }
})
