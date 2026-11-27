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
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** AssemblyAI rewords through its LLM Gateway: own host per region, raw key, a real answer budget. */
class AssemblyLlmGatewayTest : FunSpec({
    test("the gateway sits next to the API of the same region") {
        ProviderRegistry.assemblyLlmGatewayBaseUrl("https://api.assemblyai.com/") shouldBe
            "https://llm-gateway.assemblyai.com/v1/"
        ProviderRegistry.assemblyLlmGatewayBaseUrl("https://api.eu.assemblyai.com") shouldBe
            "https://llm-gateway.eu.assemblyai.com/v1/"
    }

    test("AssemblyAI rewords with Gemini 3.5 Flash-Lite at low effort, Claude Haiku as fallback") {
        val preset = ProviderRegistry.ASSEMBLYAI
        preset.capabilities.chat shouldBe true
        preset.defaultChatModel shouldBe "gemini-3.5-flash-lite"
        preset.reasoningEffortWhenOff("gemini-3.5-flash-lite") shouldBe "low"
        preset.chatFallbackModel shouldBe "claude-haiku-5-5"
    }

    test("a chat call goes to v1/chat/completions with the raw key and a long answer budget") {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"request_id":"r1","choices":[{"message":{"role":"assistant","content":"Hallo Welt."},""" +
                        """"finish_reason":"stop"}],"usage":{"input_tokens":12,"output_tokens":3,"total_tokens":15}}""",
                ),
            )
            val client = OpenAiCompatibleClient(
                ProviderConfig(
                    baseUrl = server.url("/").toString(),
                    apiKey = "aai-key",
                    transcriptionApi = TranscriptionApi.ASSEMBLYAI_ASYNC,
                    chatFallbackModel = "claude-haiku-5-5",
                ),
            )
            val result = client.complete(ChatRequest.ofUser("gemini-3.5-flash-lite", "hallo äh welt", reasoningEffort = "low"))
            val recorded = server.takeRequest()

            result.text shouldBe "Hallo Welt."
            recorded.path shouldBe "/v1/chat/completions"
            recorded.getHeader("Authorization") shouldBe "aai-key"
            val body = recorded.body.readUtf8()
            body shouldContain "\"model\":\"gemini-3.5-flash-lite\""
            body shouldContain "\"max_tokens\":16000"
            body shouldContain "\"reasoning_effort\":\"low\""
            body shouldContain "\"fallbacks\":[{\"model\":\"claude-haiku-5-5\"}]"
        }
    }

    test("no fallback when the chosen model is the fallback itself") {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))
            val client = OpenAiCompatibleClient(
                ProviderConfig(
                    baseUrl = server.url("/").toString(),
                    apiKey = "aai-key",
                    transcriptionApi = TranscriptionApi.ASSEMBLYAI_ASYNC,
                    chatFallbackModel = "claude-haiku-5-5",
                ),
            )
            client.complete(ChatRequest.ofUser("claude-haiku-5-5", "x"))
            server.takeRequest().body.readUtf8().contains("fallbacks") shouldBe false
        }
    }
})
