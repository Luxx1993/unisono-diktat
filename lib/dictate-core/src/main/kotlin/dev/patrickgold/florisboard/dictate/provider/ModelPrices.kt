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

import java.util.Locale

/**
 * List prices for speech recognition in euros per hour of audio, so the model picker can show what a model
 * costs. Pay-as-you-go prices from each provider's own price page, read on 2026-10-21 (updated 2026-11-06) and converted at
 * 1 EUR = 1.1215 USD (Gladia, Scaleway and OVHcloud list in euros). Speaker labels and custom vocabulary
 * add a surcharge at some providers and are not included.
 *
 * Only what was found is listed; a model without an entry shows no price rather than a guess. [approx]
 * marks figures that are calculated from token prices or come from third-party sources.
 */
object ModelPrices {

    data class Price(val eurPerHour: Double, val approx: Boolean = false)

    private fun p(value: Double, approx: Boolean = false) = Price(value, approx)

    /** File transcription, keyed `provider id|model id` (lower case). */
    private val batch: Map<String, Price> = mapOf(
        "openrouter|openai/whisper-large-v3-turbo" to p(0.0107),
        "openrouter|openai/whisper-large-v3" to p(0.0241),
        "openrouter|nvidia/parakeet-tdt-0.6b-v3" to p(0.0535),
        "openrouter|microsoft/mai-transcribe-2" to p(0.0892),
        "openrouter|openai/gpt-transcribe" to p(0.2407),
        "openrouter|elevenlabs/scribe-v2" to p(0.1958),
        "openrouter|x-ai/grok-stt-1.0" to p(0.0892),
        "openrouter|mistralai/voxtral-mini-transcribe" to p(0.1605),
        "openrouter|mistralai/voxtral-mini-transcribe-3-0" to p(0.1872),
        "openrouter|qwen/qwen3-asr-1.7b" to p(0.0241),
        "openrouter|google/gemini-3.5-transcribe" to p(0.2675, approx = true),
        "openrouter|deepgram/nova-3" to p(0.2311, approx = true),
        "openrouter|assemblyai/universal-3-6-pro" to p(0.2675),
        "groq|whisper-large-v3-turbo" to p(0.0357),
        "groq|whisper-large-v3" to p(0.099),
        "ovhcloud|whisper-large-v3-turbo" to p(0.046),
        "ovhcloud|whisper-large-v3" to p(0.147),
        "scaleway|whisper-large-v3" to p(0.18),
        "together|openai/whisper-large-v3" to p(0.0802),
        "together|nvidia/parakeet-tdt-0.6b-v3" to p(0.0802),
        "xai|grok-voice-transcribe-2.0" to p(0.0892, approx = true),
        "soniox|stt-async-v6" to p(0.087, approx = true),
        "azure|mai-transcribe-2" to p(0.0892),
        "azure|mai-transcribe-1.5" to p(0.321),
        "assemblyai|universal-2" to p(0.1337),
        "assemblyai|universal-3-5-pro" to p(0.1872),
        "assemblyai|universal-3-5-pro${ProviderRegistry.ASSEMBLYAI_SYNC_SUFFIX}" to p(0.4012, approx = true),
        "gladia|solaria-3" to p(0.16),
        "gladia|solaria-1" to p(0.27),
        "openai|gpt-transcribe" to p(0.2407, approx = true),
        "mistral|voxtral-mini-transcribe-3-0" to p(0.1872),
        "mistral|voxtral-small-transcribe-3-0" to p(0.214),
        "elevenlabs|scribe_v2" to p(0.1962),
        "deepgram|nova-3" to p(0.23),
        "gemini|gemini-3.5-transcribe" to p(0.2675, approx = true),
        "gemini|gemini-3.6-flash" to p(0.2318, approx = true),
        "gemini|gemini-3.8-flash" to p(0.2318, approx = true),
        "gemini|gemini-3.5-flash-lite" to p(0.058, approx = true),
        "azure|mai-transcribe-1" to p(0.321),
    )

    /** Live transcription, which providers usually bill higher than files. */
    private val realtime: Map<String, Price> = mapOf(
        "soniox|stt-rt-v6" to p(0.107),
        "assemblyai|universal-streaming-english" to p(0.1337),
        "assemblyai|universal-streaming-multilingual" to p(0.1337),
        "assemblyai|universal-3-6-pro" to p(0.4012),
        "xai|grok-voice-transcribe-2.0" to p(0.1783, approx = true),
        "deepgram|nova-2" to p(0.3121),
        "deepgram|nova-3" to p(0.4102),
        "deepgram|flux-general-en" to p(0.4102),
        "deepgram|flux-general-multi" to p(0.4191),
        "elevenlabs|scribe_v2_realtime" to p(0.3477),
        "mistral|voxtral-mini-transcribe-realtime-3-0" to p(0.428),
        "openai|gpt-live-transcribe" to p(0.9095),
        "openai|gpt-realtime-whisper" to p(0.9095),
        "gemini|gemini-3.5-transcribe-live" to p(0.4815, approx = true),
        "gladia|solaria-1" to p(0.68),
    )

    /** Text models: euros per 1 million tokens, input and output. */
    data class ChatPrice(val eurIn: Double, val eurOut: Double, val approx: Boolean = false)

    private fun c(eurIn: Double, eurOut: Double, approx: Boolean = false) = ChatPrice(eurIn, eurOut, approx)

    /**
     * Text models (cleanup, rewording) in euros per 1 million tokens, `provider id|model id` in lower case. Standard
     * pay-as-you-go prices of 2026-10-21 for short prompts, no batch, cache or priority tariffs; dollar prices at
     * 1 EUR = 1.1215 USD, Scaleway and OVHcloud list in euros. Long-prompt tiers, promotional prices and unconfirmed
     * model ids are in docs/PREISE.md. [ChatPrice.approx] marks unconfirmed ids and third-party figures.
     */
    private val chat: Map<String, ChatPrice> = mapOf(
        "anthropic|claude-fable-5" to c(8.9166, 44.5831, approx = true),
        "anthropic|claude-fable-5-1" to c(8.9166, 44.5831),
        "anthropic|claude-haiku-4-5" to c(0.8917, 4.4583, approx = true),
        "anthropic|claude-haiku-5-5" to c(0.0892, 0.4458),
        "anthropic|claude-opus-4-5" to c(4.4583, 22.2916, approx = true),
        "anthropic|claude-opus-4-6" to c(4.4583, 22.2916, approx = true),
        "anthropic|claude-opus-4-7" to c(4.4583, 22.2916, approx = true),
        "anthropic|claude-opus-4-8" to c(4.4583, 22.2916, approx = true),
        "anthropic|claude-opus-5" to c(4.4583, 22.2916, approx = true),
        "anthropic|claude-opus-5-5" to c(3.5667, 17.8333),
        "anthropic|claude-sonnet-4-5" to c(2.675, 13.3749, approx = true),
        "anthropic|claude-sonnet-4-6" to c(2.675, 13.3749, approx = true),
        "anthropic|claude-sonnet-5" to c(1.7833, 8.9166, approx = true),
        "anthropic|claude-sonnet-5-5" to c(1.7833, 8.9166),
        "cerebras|gpt-oss-120b" to c(0.3121, 0.6687),
        "cerebras|qwen-3.8-27b" to c(0.8827, 1.3286),
        "deepinfra|anthropic/claude-haiku-4-5" to c(0.8917, 4.4583),
        "deepinfra|anthropic/claude-sonnet-5" to c(2.675, 13.3749),
        "deepinfra|deepseek-ai/deepseek-r1-0528" to c(0.4458, 1.9171),
        "deepinfra|deepseek-ai/deepseek-v3.2" to c(0.2318, 0.3388),
        "deepinfra|deepseek-ai/deepseek-v4-flash" to c(0.0802, 0.1605),
        "deepinfra|deepseek-ai/deepseek-v4-flash-0731" to c(0.0535, 0.1605),
        "deepinfra|deepseek-ai/deepseek-v4-pro" to c(1.1592, 2.3183),
        "deepinfra|google/gemini-2.5-flash" to c(0.2675, 2.2292),
        "deepinfra|google/gemma-4-31b-it" to c(0.1783, 0.3567),
        "deepinfra|meta-llama/llama-3.3-70b-instruct-turbo" to c(0.0892, 0.2853),
        "deepinfra|meta-llama/llama-4-scout-17b-16e-instruct" to c(0.0892, 0.2675),
        "deepinfra|meta-llama/meta-llama-3.1-8b-instruct-turbo" to c(0.0178, 0.0357),
        "deepinfra|microsoft/phi-4" to c(0.0624, 0.1248),
        "deepinfra|mistralai/mistral-nemo-instruct-2407" to c(0.0259, 0.0267),
        "deepinfra|mistralai/mistral-small-3.2-24b-instruct-2506" to c(0.0669, 0.1783),
        "deepinfra|moonshotai/kimi-k2.6" to c(0.6687, 3.1208),
        "deepinfra|moonshotai/kimi-k3" to c(2.5412, 12.7062),
        "deepinfra|qwen/qwen3-235b-a22b-instruct-2507" to c(0.0802, 0.4904),
        "deepinfra|qwen/qwen3-32b" to c(0.0713, 0.2497),
        "deepinfra|qwen/qwen3.5-397b-a17b" to c(0.4012, 2.675),
        "deepinfra|qwen/qwen3.6-27b" to c(0.2853, 2.8533),
        "deepinfra|qwen/qwen3.5-9b" to c(0.0892, 0.1337),
        "deepinfra|openai/gpt-oss-120b" to c(0.033, 0.1516),
        "deepseek|deepseek-flash" to c(0.2675, 1.07),
        "deepseek|deepseek-v4-pro" to c(1.177, 3.531),
        "gemini|gemini-2.5-flash" to c(0.2675, 2.2292),
        "gemini|gemini-2.5-flash-lite" to c(0.0892, 0.3567),
        "gemini|gemini-2.5-pro" to c(1.1146, 8.9166),
        "gemini|gemini-3-flash-preview" to c(0.4458, 2.675),
        "gemini|gemini-3.1-pro-preview" to c(1.7833, 10.7),
        "gemini|gemini-3.5-flash-lite" to c(0.2675, 2.2292),
        "gemini|gemini-3.6-flash" to c(0.6687, 3.3437),
        "gemini|gemini-3.8-flash" to c(0.6687, 3.3437),
        "gemini|gemini-4-argon" to c(1.7833, 8.9166),
        "groq|openai/gpt-oss-120b" to c(0.1337, 0.535),
        "groq|openai/gpt-oss-20b" to c(0.0669, 0.2675),
        "groq|openai/gpt-oss-safeguard-20b" to c(0.0669, 0.2675),
        "groq|qwen/qwen3.8-27b" to c(0.7133, 3.5667),
        "mistral|codestral" to c(0.2675, 0.8025, approx = true),
        "mistral|ministral-14b-2512" to c(0.1783, 0.1783),
        "mistral|ministral-3b-2512" to c(0.0892, 0.0892),
        "mistral|ministral-8b-2512" to c(0.1337, 0.1337),
        "mistral|mistral-large-4" to c(0.6242, 1.8725),
        "mistral|mistral-small-latest" to c(0.1337, 0.535),
        "mistral|mistral-small-2603" to c(0.1337, 0.535),
        "openai|chat-latest" to c(4.4583, 26.7499),
        "openai|gpt-3.5-turbo" to c(0.4458, 1.3375),
        "openai|gpt-4-turbo-2024-04-09" to c(8.9166, 26.7499),
        "openai|gpt-4.1" to c(1.7833, 7.1333),
        "openai|gpt-4.1-mini" to c(0.3567, 1.4267),
        "openai|gpt-4.1-nano" to c(0.0892, 0.3567),
        "openai|gpt-4o" to c(2.2292, 8.9166),
        "openai|gpt-4o-mini" to c(0.1337, 0.535),
        "openai|gpt-5" to c(1.1146, 8.9166),
        "openai|gpt-5-mini" to c(0.2229, 1.7833),
        "openai|gpt-5-nano" to c(0.0446, 0.3567),
        "openai|gpt-5-pro" to c(13.3749, 106.9996),
        "openai|gpt-5.1" to c(1.1146, 8.9166),
        "openai|gpt-5.2" to c(1.5604, 12.4833),
        "openai|gpt-5.2-pro" to c(18.7249, 149.7994),
        "openai|gpt-5.4" to c(2.2292, 13.3749),
        "openai|gpt-5.4-mini" to c(0.6687, 4.0125),
        "openai|gpt-5.4-nano" to c(0.1783, 1.1146),
        "openai|gpt-5.4-pro" to c(26.7499, 160.4993),
        "openai|gpt-5.5" to c(4.4583, 26.7499),
        "openai|gpt-5.5-pro" to c(26.7499, 160.4993),
        "openai|gpt-5.6-luna" to c(0.1783, 1.07),
        "openai|gpt-5.6-sol" to c(3.5667, 17.8333),
        "openai|gpt-5.6-terra" to c(1.7833, 10.7),
        "openai|gpt-6-astra" to c(8.9166, 44.5831),
        "openai|gpt-6-luna" to c(0.0892, 0.4458),
        "openai|gpt-6-sol" to c(1.7833, 8.9166),
        "openai|gpt-6.1-sol" to c(1.7833, 8.9166),
        "openai|gpt-6.1-terra" to c(0.8917, 4.4583),
        "openai|o1" to c(13.3749, 53.4998),
        "openai|o1-pro" to c(133.7494, 534.9978),
        "openai|o3" to c(1.7833, 7.1333),
        "openai|o3-mini" to c(0.9808, 3.9233),
        "openai|o3-pro" to c(17.8333, 71.333),
        "openai|o4-mini" to c(0.9808, 3.9233),
        "openrouter|anthropic/claude-haiku-5.5" to c(0.0892, 0.4458),
        "openrouter|anthropic/claude-opus-5.5" to c(3.5667, 17.8333),
        "openrouter|anthropic/claude-sonnet-5.5" to c(1.7833, 8.9166),
        "openrouter|deepseek/deepseek-v4-flash" to c(0.0811, 0.1623),
        "openrouter|deepseek/deepseek-v4-flash-0731" to c(0.3923, 1.177),
        "openrouter|deepseek/deepseek-v4.1-flash" to c(0.0499, 0.107),
        "openrouter|deepseek/deepseek-v4.1-flash-1017" to c(0.2675, 1.07),
        "openrouter|meta/muse-spark-1.3-contributor" to c(0.0892, 0.1783),
        "openrouter|minimax/minimax-m3.1-flash:free" to c(0.0, 0.0),
        "openrouter|nvidia/nemotron-3-ultra-550b-a55b:free" to c(0.0, 0.0),
        "openrouter|openai/gpt-6-luna" to c(0.0892, 0.4458),
        "openrouter|openai/gpt-oss-120b" to c(0.0178, 0.066),
        "openrouter|stepfun/step-5-preview" to c(0.8917, 2.4075),
        "openrouter|tencent/hy3" to c(0.1177, 0.4708),
        "openrouter|tencent/hy4-preview" to c(0.7436, 2.23),
        "openrouter|xiaomi/mimo-v2.6-flash" to c(0.1248, 0.2497),
        "openrouter|z-ai/glm-5.3" to c(0.0704, 1.2483),
        "openrouter|z-ai/glm-5.3-flash" to c(0.0419, 0.107),
        "ovhcloud|gpt-oss-120b" to c(0.08, 0.4),
        "ovhcloud|gpt-oss-20b" to c(0.04, 0.15),
        "ovhcloud|meta-llama-3_3-70b-instruct" to c(0.67, 0.67),
        "ovhcloud|mistral-nemo-instruct-2407" to c(0.13, 0.13),
        "ovhcloud|qwen2.5-vl-72b-instruct" to c(0.91, 0.91),
        "ovhcloud|qwen3.5-397b-a17b" to c(0.6, 3.6),
        "ovhcloud|qwen3.5-9b" to c(0.1, 0.15),
        "ovhcloud|qwen3.6-27b" to c(0.4, 2.7),
        "ovhcloud|qwen3.8-27b" to c(0.4, 2.7),
        "scaleway|deepseek-v4-flash-0731" to c(0.4, 0.8),
        "scaleway|deepseek-v4.1-flash" to c(0.6, 1.8),
        "scaleway|gemma-4-26b-a4b-it" to c(0.25, 0.5),
        "scaleway|glm-5.2" to c(1.8, 5.5),
        "scaleway|glm-5.3" to c(1.8, 5.5),
        "scaleway|gpt-oss-120b" to c(0.15, 0.6),
        "scaleway|llama-3.3-70b-instruct" to c(0.9, 0.9),
        "scaleway|mistral-medium-3.5-128b" to c(1.5, 7.5),
        "scaleway|mistral-small-3.2-24b-instruct-2506" to c(0.15, 0.35),
        "scaleway|qwen3-235b-a22b-instruct-2507" to c(0.75, 2.25),
        "scaleway|qwen3.5-397b-a17b" to c(0.6, 3.6),
        "scaleway|qwen3.6-35b-a3b" to c(0.25, 1.5),
        "scaleway|qwen3.8-27b" to c(0.6, 3.3),
        "together|openai/gpt-oss-120b" to c(0.1337, 0.535),
        "xai|grok-4.20-0309-non-reasoning" to c(1.1146, 2.2292),
        "xai|grok-4.20-0309-reasoning" to c(1.1146, 2.2292),
        "xai|grok-4.20-multi-agent-0309" to c(1.1146, 2.2292),
        "xai|grok-4.3" to c(1.1146, 2.2292),
        "xai|grok-4.5" to c(1.7833, 5.35),
        "xai|grok-4.6" to c(1.7833, 5.35),
        "xai|grok-4.7" to c(1.7833, 5.35),
        "xai|grok-build-0.1" to c(0.8917, 1.7833),
    )

    fun chatPrice(providerId: String, model: String): ChatPrice? = chat[key(providerId, model)]

    /**
     * What a router adds on top of the provider's price: OpenRouter passes prices through and takes 5.5 % when the
     * balance is topped up. Null for providers that bill their listed price.
     */
    fun surcharge(providerId: String): String? = SURCHARGES[providerId]

    private val SURCHARGES = mapOf("openrouter" to "+5,5 %")

    /** The file price of a model as the picker shows it, with the router surcharge behind it; null without a price. */
    fun batchText(providerId: String, model: String, locale: Locale = Locale.getDefault()): String? =
        withSurcharge(providerId, format(batchPrice(providerId, model), locale))

    /** The text model price as the picker shows it, with the router surcharge behind it; null without a price. */
    fun chatText(providerId: String, model: String, locale: Locale = Locale.getDefault()): String? =
        withSurcharge(providerId, formatChat(chatPrice(providerId, model), locale))

    private fun withSurcharge(providerId: String, text: String?): String? =
        if (text == null) null else surcharge(providerId)?.let { "$text $it" } ?: text

    fun batchPrice(providerId: String, model: String): Price? = batch[key(providerId, model)]

    fun realtimePrice(providerId: String, model: String): Price? = realtime[key(providerId, model)]

    /**
     * `0,16 €/h`, `0,015 €/h` or `0,0096 €/h`: the exact figure with 2 to 4 decimals, trailing zeros dropped.
     * `ca. 0,40 €/h` for a calculated figure; null when nothing is known.
     */
    fun format(price: Price?, locale: Locale = Locale.getDefault()): String? {
        price ?: return null
        return (if (price.approx) "ca. " else "") + number(price.eurPerHour, locale) + " €/h"
    }

    /** `0,09 / 0,36 €/M`: input and output, euros per 1 million tokens; `ca. ` in front for an unconfirmed figure. */
    fun formatChat(price: ChatPrice?, locale: Locale = Locale.getDefault()): String? {
        price ?: return null
        return (if (price.approx) "ca. " else "") + number(price.eurIn, locale) + " / " + number(price.eurOut, locale) + " €/M"
    }

    /** 2 to 4 decimals, trailing zeros dropped (`0,10`, `0,015`, `0,0096`); a free model reads `0`. */
    private fun number(value: Double, locale: Locale): String {
        if (value == 0.0) return "0"
        val text = String.format(locale, "%.4f", value)
        val separator = text.indexOfFirst { it == ',' || it == '.' }
        val trimmed = text.trimEnd('0')
        return if (trimmed.length - separator - 1 < 2) text.take(separator + 3) else trimmed
    }

    private fun key(providerId: String, model: String) = "$providerId|${model.trim().lowercase()}"
}
