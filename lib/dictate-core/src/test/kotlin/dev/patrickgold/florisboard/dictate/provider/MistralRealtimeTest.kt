/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Mistral's realtime protocol as its Python SDK speaks it (`mistralai.extra.realtime`): `session.created`,
 * then `session.update`, `input_audio.append`, and `input_audio.flush` + `input_audio.end` at the end;
 * `transcription.text.delta` and `transcription.done` come back.
 */
class MistralRealtimeTest : FunSpec({

    class Recorder : RealtimeCallbacks {
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        val closed = CountDownLatch(1)
        override fun onPartial(text: String) { synchronized(this) { partials += text } }
        override fun onFinalSegment(text: String) { synchronized(this) { finals += text } }
        override fun onError(t: Throwable) { synchronized(this) { errors += t } }
        override fun onClosed() = closed.countDown()
    }

    test("the handshake carries the model, the format goes first, audio is base64, and the end is flush then end") {
        val received = LinkedBlockingQueue<String>()
        val serverClosed = CountDownLatch(1)
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"session.created","session":{"request_id":"r1","model":"m"}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    received += text
                    if (text.contains("input_audio.append")) {
                        webSocket.send("""{"type":"transcription.text.delta","text":"Hallo"}""")
                        webSocket.send("""{"type":"transcription.text.delta","text":" Welt"}""")
                    }
                    if (text.contains("input_audio.end")) {
                        webSocket.send("""{"type":"transcription.done","model":"m","text":"Hallo Welt.","usage":{}}""")
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = serverClosed.countDown()
            }),
        )
        server.start()
        try {
            val out = Recorder()
            val session = MistralRealtimeSession(
                client = OkHttpClient(),
                apiKey = "mistral-test",
                model = "voxtral-mini-transcribe-realtime-3-0",
                language = "de",
                callbacks = out,
                endpoint = "ws://${server.hostName}:${server.port}/v1/audio/transcriptions/realtime",
            )
            session.connect()
            // Spoken before session.created arrived: held until the format has been sent.
            session.sendAudio(byteArrayOf(1, 0, 0, 1), 4)

            received.poll(5, TimeUnit.SECONDS) shouldBe
                """{"type":"session.update","session":{"audio_format":{"encoding":"pcm_s16le","sample_rate":16000}}}"""
            received.poll(5, TimeUnit.SECONDS) shouldBe
                """{"type":"input_audio.append","audio":"AQAAAQ=="}"""

            session.finish()
            received.poll(5, TimeUnit.SECONDS) shouldBe """{"type":"input_audio.flush"}"""
            received.poll(5, TimeUnit.SECONDS) shouldBe """{"type":"input_audio.end"}"""
            out.closed.await(5, TimeUnit.SECONDS) shouldBe true

            out.partials shouldContainExactly listOf("Hallo", "Hallo Welt")
            out.finals shouldContainExactly listOf("Hallo Welt.")
            out.errors.shouldBeEmpty()

            val handshake = server.takeRequest()
            handshake.getHeader("Authorization") shouldBe "Bearer mistral-test"
            handshake.path.orEmpty() shouldContain "/v1/audio/transcriptions/realtime?model=voxtral-mini-transcribe-realtime-3-0"
            serverClosed.await(5, TimeUnit.SECONDS) shouldBe true
        } finally {
            server.shutdown()
        }
    }

    test("an error event ends the session with the reason") {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"error","error":{"message":"quota exceeded","code":429}}""")
                }
            }),
        )
        server.start()
        try {
            val out = Recorder()
            val session = MistralRealtimeSession(
                client = OkHttpClient(), apiKey = "k", model = "m", language = null, callbacks = out,
                endpoint = "ws://${server.hostName}:${server.port}/x",
            )
            session.connect()
            out.closed.await(5, TimeUnit.SECONDS) shouldBe true
            out.errors.size shouldBe 1
            out.errors[0].message.orEmpty() shouldContain "quota exceeded"
        } finally {
            server.shutdown()
        }
    }
})
