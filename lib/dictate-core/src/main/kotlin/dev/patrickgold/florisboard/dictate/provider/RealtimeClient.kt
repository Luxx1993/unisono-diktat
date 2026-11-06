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

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Opens real-time transcription sessions (issue #128). Implements all eight [RealtimeApi] wire formats:
 * OpenAI and ElevenLabs/Gemini send audio as base64 in JSON; Deepgram/Soniox/AssemblyAI/Mistral/xAI stream
 * raw binary PCM. Mistral is experimental (its raw protocol is SDK-only/unverified). On any error the
 * caller falls back to batch transcription.
 *
 * The WebSocket client is long-lived (no read/call timeout, periodic ping), separate from the batch HTTP
 * client. Callers keep the batch [OpenAiCompatibleClient] for the fallback path.
 */
object RealtimeClient {

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)   // fail a dead route fast instead of stalling the session
            // Race IPv6/IPv4 with OkHttp 5 Happy Eyeballs instead of forcing either address family.
            .fastFallback(true)
            .readTimeout(0, TimeUnit.SECONDS)   // long-lived stream
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    /** The PCM sample rate a given realtime API expects (OpenAI wants 24 kHz; the rest 16 kHz). */
    fun sampleRateFor(api: RealtimeApi): Int = when (api) {
        RealtimeApi.OPENAI -> 24_000
        else -> 16_000
    }

    /**
     * Opens a session for [api] and starts connecting. [apiKey]/[model]/[language] identify the provider
     * call; [callbacks] deliver interim/final text (on background threads). The returned [RealtimeSession]
     * is fed PCM at [sampleRateFor] and finished/cancelled by the caller.
     */
    /**
     * [baseUrl] is the address this session should use instead of its vendor's fixed one. Two things ask
     * for that, and they hand over different shapes: a server of the user's own passes the HTTP base URL
     * its batch requests already use and the OpenAI-shaped session derives `/realtime` from it (#249),
     * while a provider that runs one streaming host per data-residency region passes that host's
     * WebSocket address outright (Soniox, #403) — it is a sibling of the REST host, not a path under it,
     * so there is nothing to derive. Null, and every session goes where it always went.
     *
     * [expectedLanguages] is read only by the three providers whose language field is a list (OpenAI's
     * gpt-transcribe generation, Soniox, Gemini), and only when no language is pinned: it turns
     * auto-detect into detection among the languages the user dictates in (issue #99).
     */
    fun open(
        api: RealtimeApi,
        apiKey: String,
        model: String,
        language: String?,
        callbacks: RealtimeCallbacks,
        baseUrl: String? = null,
        expectedLanguages: List<String> = emptyList(),
    ): RealtimeSession = when (api) {
        RealtimeApi.OPENAI ->
            OpenAiRealtimeSession(wsClient, apiKey, model, language, callbacks, baseUrl, expectedLanguages)
                .also { it.connect() }
        RealtimeApi.DEEPGRAM -> DeepgramRealtimeSession(wsClient, apiKey, model, language, callbacks).also { it.connect() }
        RealtimeApi.SONIOX ->
            SonioxRealtimeSession(wsClient, apiKey, model, language, callbacks, baseUrl, expectedLanguages)
                .also { it.connect() }
        RealtimeApi.ASSEMBLYAI -> AssemblyAiRealtimeSession(wsClient, apiKey, model, language, callbacks).also { it.connect() }
        RealtimeApi.ELEVENLABS -> ElevenLabsRealtimeSession(wsClient, apiKey, model, language, callbacks).also { it.connect() }
        RealtimeApi.GEMINI ->
            GeminiRealtimeSession(wsClient, apiKey, model, language, callbacks, expectedLanguages)
                .also { it.connect() }
        RealtimeApi.MISTRAL_VOXTRAL -> MistralRealtimeSession(wsClient, apiKey, model, language, callbacks).also { it.connect() }
        RealtimeApi.XAI -> XaiRealtimeSession(wsClient, apiKey, model, language, callbacks).also { it.connect() }
        RealtimeApi.GLADIA ->
            GladiaRealtimeSession(wsClient, apiKey, model, language, callbacks, expectedLanguages)
                .also { it.connect() }
    }
}

/**
 * OpenAI realtime transcription over `wss://api.openai.com/v1/realtime?intent=transcription`. Sends a
 * `session.update` transcription config on open, streams 24 kHz mono PCM16 as base64
 * `input_audio_buffer.append`, and turns `...input_audio_transcription.delta`/`.completed` events into
 * [RealtimeCallbacks.onPartial]/[onFinalSegment]. Model `gpt-realtime-whisper` streams deltas; the
 * `-transcribe` models only emit the final.
 */
private class OpenAiRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
    private val baseUrl: String? = null,
    expectedLanguages: List<String> = emptyList(),
) : RealtimeSession {

    private val languageHints = languageHintsOf(language, expectedLanguages)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    private val partial = StringBuilder()
    private val audioGate = RealtimeAudioGate()
    @Volatile private var committing = false
    @Volatile private var done = false

    private companion object {
        const val URL = "wss://api.openai.com/v1/realtime?intent=transcription"

        /**
         * The websocket address of a self-hosted server, derived from the base URL its batch requests
         * already use (#249) — `http(s)://host/v1/` becomes `ws(s)://host/v1/realtime`. Cleartext is
         * deliberately allowed through: these live on a LAN, where https is the exception.
         */
        fun realtimeUrlFrom(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            val ws = when {
                trimmed.startsWith("https://", ignoreCase = true) -> "wss://" + trimmed.removeRange(0, 8)
                trimmed.startsWith("http://", ignoreCase = true) -> "ws://" + trimmed.removeRange(0, 7)
                trimmed.startsWith("ws://", ignoreCase = true) ||
                    trimmed.startsWith("wss://", ignoreCase = true) -> trimmed
                else -> "ws://$trimmed"
            }
            // Already pointed at the endpoint itself (or carrying its own query): take it as given.
            return if (ws.contains("/realtime")) ws else "$ws/realtime?intent=transcription"
        }
    }

    fun connect() {
        // GA interface (the OpenAI-Beta: realtime=v1 header would force the retired beta shape →
        // "beta_api_shape_disabled"). Session type ("transcription") distinguishes the session in GA.
        val request = Request.Builder()
            .url(baseUrl?.takeIf { it.isNotBlank() }?.let { realtimeUrlFrom(it) } ?: URL)
            .apply {
                // A server of one's own usually has no key at all, and sending an empty bearer makes some
                // of them reject the handshake outright.
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
            }
            .build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(sessionUpdate())
            // OkHttp accepts send() calls while a socket is still connecting. Without this gate, mic
            // frames queued during the handshake precede this session.update and therefore arrive before
            // OpenAI knows the intended PCM format/model. Put the config first, then replay the beginning.
            audioGate.markReady(
                send = { pcm16, len -> sendAudioFrame(webSocket, pcm16, len) },
                finish = { sendCommit(webSocket) },
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.content) {
                "conversation.item.input_audio_transcription.delta" -> {
                    val delta = obj["delta"]?.jsonPrimitive?.content ?: return
                    partial.append(delta)
                    callbacks.onPartial(partial.toString())
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = obj["transcript"]?.jsonPrimitive?.content ?: partial.toString()
                    partial.setLength(0)
                    callbacks.onFinalSegment(transcript)
                    // After we asked to commit, the completed event is our cue that the final is in.
                    if (committing) finishClosed(webSocket)
                }
                "error" -> emitError(RuntimeException("OpenAI realtime error: ${obj["error"] ?: obj}"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Concise error only (no transcript/body content): the engine falls back to batch on error.
            android.util.Log.w("DictateRT", "realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    private fun sessionUpdate(): String = buildJsonObject {
        put("type", "session.update")
        put("session", buildJsonObject {
            put("type", "transcription")
            put("audio", buildJsonObject {
                put("input", buildJsonObject {
                    put("format", buildJsonObject {
                        put("type", "audio/pcm")
                        put("rate", 24_000)
                    })
                    put("transcription", buildJsonObject {
                        put("model", model)
                        // The gpt-transcribe generation takes `languages` as an array (it can hint
                        // several for code-switching audio); the older models take a single
                        // `language` string. Sending the wrong one is not an error, it is simply
                        // ignored — the user's language choice would quietly stop applying (#248).
                        if (usesLanguagesField(model)) {
                            if (languageHints.isNotEmpty()) {
                                put("languages", buildJsonArray { languageHints.forEach { add(JsonPrimitive(it)) } })
                            }
                        } else if (!language.isNullOrBlank() && language != "detect") {
                            put("language", language)
                        }
                    })
                    // No server-side turn detection: Dictate decides when a dictation ends and commits the
                    // buffer itself, so letting the server also cut turns would segment the same audio a
                    // second time on its own schedule (from #243).
                    put("turn_detection", JsonNull)
                })
            })
        })
    }.toString()

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length ->
            ws?.let { sendAudioFrame(it, audio, length) }
        }
    }

    private fun sendAudioFrame(socket: WebSocket, pcm16: ByteArray, len: Int) {
        val b64 = Base64.encodeToString(pcm16, 0, len, Base64.NO_WRAP)
        val msg = buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", b64)
        }.toString()
        runCatching { socket.send(msg) }
    }

    private fun sendCommit(socket: WebSocket) {
        runCatching { socket.send("""{"type":"input_audio_buffer.commit"}""") }
    }

    override fun finish() {
        committing = true
        // Flush the buffered audio; the server responds with the final `completed`, then we close.
        audioGate.finish {
            ws?.let { sendCommit(it) }
        }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Soniox streaming transcription over `wss://stt-rt.soniox.com/transcribe-websocket`. The API key goes
 * in the `Authorization: Bearer` header of the handshake — a key inside the config message is deprecated
 * and refused from 2027-01-15 — and the config is the first JSON message; 16 kHz mono PCM16 is streamed as raw binary
 * frames. Each `tokens[]` message carries `is_final` tokens (permanent) plus a replaceable tail; the full
 * text (permanent + tail) is emitted as the partial, and the permanent text as the final on flush. An
 * empty frame flushes; the server replies `finished:true` and closes.
 *
 * [wsUrl] is the streaming host of the account's data-residency region (#403), taken as given: Soniox
 * publishes it per region alongside the REST one, and a key is only a credential inside its own region.
 */
private class SonioxRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
    private val wsUrl: String? = null,
    expectedLanguages: List<String> = emptyList(),
) : RealtimeSession {

    private val languageHints = languageHintsOf(language, expectedLanguages)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    private val permanent = StringBuilder()
    private val audioGate = RealtimeAudioGate()
    @Volatile private var done = false

    private companion object { const val URL = "wss://stt-rt.soniox.com/transcribe-websocket" }

    fun connect() {
        val url = wsUrl?.takeIf { it.isNotBlank() } ?: URL
        val request = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(config())
            // Soniox requires the config text to be the first frame. Replay every microphone frame
            // captured while the socket connected immediately after it, instead of losing the start.
            audioGate.markReady(
                send = { pcm16, len -> runCatching { webSocket.send(pcm16.toByteString(0, len)) } },
                finish = { runCatching { webSocket.send("") } },
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            obj["tokens"]?.jsonArray?.let { tokens ->
                val tail = StringBuilder()
                for (tk in tokens) {
                    val o = tk.jsonObject
                    val txt = o["text"]?.jsonPrimitive?.content ?: continue
                    if (o["is_final"]?.jsonPrimitive?.booleanOrNull == true) permanent.append(txt) else tail.append(txt)
                }
                callbacks.onPartial(permanent.toString() + tail)
            }
            if (obj["finished"]?.jsonPrimitive?.booleanOrNull == true) finalizeAndClose(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "soniox realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finalizeAndClose(webSocket)
    }

    private fun config(): String = buildJsonObject {
        put("model", model)
        put("audio_format", "pcm_s16le")
        put("sample_rate", 16_000)
        put("num_channels", 1)
        // Soniox hints a list, so auto-detect can still name the languages the user dictates in (#99).
        if (languageHints.isNotEmpty()) {
            put("language_hints", buildJsonArray { languageHints.forEach { add(it) } })
        }
    }.toString()

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length ->
            runCatching { ws?.send(audio.toByteString(0, length)) }
        }
    }

    override fun finish() {
        audioGate.finish {
            runCatching { ws?.send("") }   // empty frame → flush + finished
        }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finalizeAndClose(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        if (permanent.isNotEmpty()) callbacks.onFinalSegment(permanent.toString())
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * AssemblyAI Universal Streaming over `wss://streaming.assemblyai.com/v3/ws` (config in query params, raw
 * key in the `Authorization` header — no "Bearer"). Streams 16 kHz mono PCM16 as raw binary frames. `Turn`
 * messages carry the current turn text in `transcript`; `end_of_turn` finalizes it. `format_turns` is off
 * (our rewording stage formats), so each turn produces one clean final. `finish()` sends `Terminate`.
 */
private class AssemblyAiRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    @Volatile private var done = false

    private companion object {
        const val URL = "wss://streaming.assemblyai.com/v3/ws?sample_rate=16000&encoding=pcm_s16le"

        /** The `speech_model` values the streaming docs list (2026-10-19); anything else (an older stored id) is left to the default. */
        val MODELS = setOf("universal-3-6-pro", "universal-3-5-pro", "universal-streaming-english", "universal-streaming-multilingual")
    }

    fun connect() {
        // Universal Streaming is selected by the /v3/ws endpoint itself; the raw key goes in Authorization.
        val url = if (model in MODELS) "$URL&speech_model=$model" else URL
        val request = Request.Builder().url(url).header("Authorization", apiKey).build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.content) {
                "Turn" -> {
                    val transcript = obj["transcript"]?.jsonPrimitive?.content.orEmpty()
                    if (transcript.isBlank()) return
                    if (obj["end_of_turn"]?.jsonPrimitive?.booleanOrNull == true) {
                        callbacks.onFinalSegment(transcript)
                    } else {
                        callbacks.onPartial(transcript)
                    }
                }
                "Termination" -> finishClosed(webSocket)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "assemblyai realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        runCatching { ws?.send(pcm16.toByteString(0, len)) }
    }

    override fun finish() {
        val socket = ws ?: return finishClosed(null)
        runCatching { socket.send("""{"type":"Terminate"}""") }
    }

    override fun cancel() {
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * ElevenLabs Scribe realtime over `wss://api.elevenlabs.io/v1/speech-to-text/realtime` (config in query
 * params, `xi-api-key` header). 16 kHz mono PCM16 is sent base64 in `input_audio_chunk` messages;
 * `partial_transcript`/`committed_transcript` messages carry the text in `text`. `finish()` sends a final
 * chunk with `commit:true` so the server flushes the last committed transcript.
 */
private class ElevenLabsRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    private val audioGate = RealtimeAudioGate()
    @Volatile private var done = false

    fun connect() {
        val lang = if (!language.isNullOrBlank() && language != "detect") "&language_code=$language" else ""
        val url = "wss://api.elevenlabs.io/v1/speech-to-text/realtime" +
            "?model_id=$model&audio_format=pcm_16000&commit_strategy=vad$lang"
        val request = Request.Builder().url(url).header("xi-api-key", apiKey).build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val body = obj["text"]?.jsonPrimitive?.content.orEmpty()
            when (obj["message_type"]?.jsonPrimitive?.content) {
                // The official lifecycle starts media after this acknowledgement. Preserve everything the
                // mic captured while connecting, then replay it once ElevenLabs confirms the session.
                "session_started" -> audioGate.markReady(
                    send = { pcm16, len -> sendAudioFrame(webSocket, pcm16, len) },
                    finish = { sendCommit(webSocket) },
                )
                "partial_transcript" -> if (body.isNotBlank()) callbacks.onPartial(body)
                "committed_transcript", "committed_transcript_with_timestamps" ->
                    if (body.isNotBlank()) callbacks.onFinalSegment(body)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "elevenlabs realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length ->
            ws?.let { sendAudioFrame(it, audio, length) }
        }
    }

    private fun sendAudioFrame(socket: WebSocket, pcm16: ByteArray, len: Int) {
        val msg = buildJsonObject {
            put("message_type", "input_audio_chunk")
            put("audio_base_64", Base64.encodeToString(pcm16, 0, len, Base64.NO_WRAP))
            put("commit", false)
            put("sample_rate", 16_000)
        }.toString()
        runCatching { socket.send(msg) }
    }

    private fun sendCommit(socket: WebSocket) {
        val msg = buildJsonObject {
            put("message_type", "input_audio_chunk")
            put("audio_base_64", "")
            put("commit", true)
        }.toString()
        runCatching { socket.send(msg) }
    }

    override fun finish() {
        audioGate.finish {
            ws?.let { sendCommit(it) }
        }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Google Gemini Live over the BidiGenerateContent WebSocket (`?key=` auth), driving the dedicated
 * streaming model `gemini-3.5-transcribe-live` (issue #292). A `setup` message enables input-audio
 * transcription (TEXT response modality); 16 kHz mono PCM16 is sent base64 as `realtimeInput`.
 * `finish()` sends `audioStreamEnd`; the last chunks flush and `turnComplete`/`generationComplete`
 * closes the session.
 *
 * The transcription model answers in two fields where the conversational live models had one:
 * `interimInputTranscription` is a speculative hypothesis that keeps changing while someone speaks,
 * `inputTranscription` is what stands once they pause. That maps exactly onto the
 * [RealtimeCallbacks] contract — interim replaces the partial, final appends a segment — so text
 * settles as it is spoken instead of all at once when the session ends.
 */
private class GeminiRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
    expectedLanguages: List<String> = emptyList(),
) : RealtimeSession {

    private val languageHints = languageHintsOf(language, expectedLanguages)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    /** The latest speculative text, cleared by every final. What is left at close was heard but never settled. */
    @Volatile private var pendingInterim = ""
    /** Audio has been sent that no settled transcription covers yet — words are still owed to us. */
    @Volatile private var audioSinceFinal = false
    private val audioGate = RealtimeAudioGate()
    @Volatile private var finishing = false
    @Volatile private var done = false

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(setup())
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)
        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = handle(webSocket, bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "gemini realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finalizeAndClose(webSocket)
    }

    private fun handle(webSocket: WebSocket, text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        if (obj.containsKey("setupComplete")) {
            // Gemini also forbids audio before setupComplete. Preserve and replay the mic startup rather
            // than dropping it while waiting for the server acknowledgement.
            audioGate.markReady(
                send = { pcm16, len -> sendAudioFrame(webSocket, pcm16, len) },
                finish = { sendAudioEnd(webSocket) },
            )
        }
        val server = obj["serverContent"]?.jsonObject
        // Speculative text: show it, never keep it. The next interim replaces this one outright.
        server?.get("interimInputTranscription")?.jsonObject?.get("text")?.jsonPrimitive?.content?.let { hypothesis ->
            if (hypothesis.isNotEmpty()) {
                pendingInterim = hypothesis
                callbacks.onPartial(hypothesis)
            }
        }
        server?.get("inputTranscription")?.jsonObject?.get("text")?.jsonPrimitive?.content?.let { chunk ->
            if (chunk.isNotEmpty()) emitFinal(chunk)
        }
        val ended = server?.get("turnComplete")?.jsonPrimitive?.booleanOrNull == true ||
            server?.get("generationComplete")?.jsonPrimitive?.booleanOrNull == true
        // Gemini ends a *turn* this way, not only the dictation: a pause in the middle closes one and
        // opens the next (measured 2026-09-14, a 36 s dictation settled its first turn 4.4 s before the
        // microphone closed). So an end marker can be the one for a turn before the stop, arriving just as
        // the stop is sent — and closing on it would drop everything spoken since. Audio that no
        // transcription has covered yet is the exact test: while some is outstanding this marker is not
        // ours, and the one that answers our own end of stream is still to come.
        if (ended && finishing && !audioSinceFinal) finalizeAndClose(webSocket)
    }

    /**
     * Hands one finalized chunk on. The protocol never stated whether `inputTranscription` carries just the
     * settled segment or everything settled so far, so this used to forward only the growth over what was
     * already seen. Measured against the endpoint on 2026-09-14 (#372): a dictation with a two-second
     * pause in it produces one `inputTranscription` per turn, each carrying **only that turn's** words.
     * The guard therefore never fired on real growth — the single thing it could match was a turn that
     * repeated the whole transcript so far, and that one it deleted. Saying the same short sentence twice
     * is exactly that shape, so the second one silently disappeared.
     */
    private fun emitFinal(chunk: String) {
        pendingInterim = ""
        audioSinceFinal = false
        callbacks.onFinalSegment(chunk)
    }

    private fun setup(): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", "models/$model")
            putJsonObject("generationConfig") {
                put("responseModalities", buildJsonArray { add("TEXT") })
            }
            putJsonObject("inputAudioTranscription") {
                // An empty list is Google's own way of asking for automatic detection, so the user's
                // chosen language reaches Gemini for the first time — it used to be accepted and dropped.
                // With none pinned the list carries the languages they dictate in, which is what Google
                // means by a list in the first place (#99).
                put("languageCodes", buildJsonArray { languageHints.forEach { add(it) } })
                // SMART drops "um"/"uh", folds spoken self-corrections into the sentence and punctuates.
                // VERBATIM would hand all of that to the rewording step instead — for a keyboard, clean
                // text on arrival is worth more than a faithful record of the stumbles.
                put("mode", "SMART")
            }
        }
    }.toString()

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length ->
            ws?.let { sendAudioFrame(it, audio, length) }
        }
    }

    private fun sendAudioFrame(socket: WebSocket, pcm16: ByteArray, len: Int) {
        audioSinceFinal = true
        val msg = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("data", Base64.encodeToString(pcm16, 0, len, Base64.NO_WRAP))
                    put("mimeType", "audio/pcm;rate=16000")
                }
            }
        }.toString()
        runCatching { socket.send(msg) }
    }

    private fun sendAudioEnd(socket: WebSocket) {
        runCatching { socket.send("""{"realtimeInput":{"audioStreamEnd":true}}""") }
    }

    override fun finish() {
        finishing = true
        audioGate.finish {
            ws?.let { sendAudioEnd(it) }
        }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finalizeAndClose(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        // Settled text was already handed over segment by segment, so nothing is repeated here. What can
        // still be outstanding is a hypothesis the server never got to confirm — the last words of a
        // dictation that ended on the closing socket. Keep them: heard-but-unconfirmed beats dropped.
        if (pendingInterim.isNotEmpty()) {
            callbacks.onFinalSegment(pendingInterim)
            pendingInterim = ""
        }
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Mistral Voxtral realtime over `wss://api.mistral.ai/v1/audio/transcriptions/realtime?model=…`, with the key
 * as `Authorization: Bearer`. The messages are the ones Mistral's own Python SDK (`mistralai.extra.realtime`)
 * sends and reads, so nothing here is guessed:
 *  - server → `session.created`; the client answers with `session.update` carrying the audio format
 *    (`pcm_s16le`, 16 kHz) and only then sends audio, which is why the first frames wait in [RealtimeAudioGate];
 *  - audio goes up as `{"type":"input_audio.append","audio":"<base64>"}`;
 *  - the server streams `transcription.text.delta` (`text`, appended token by token) and ends with
 *    `transcription.done` (`text` is the whole transcript), or `error`;
 *  - the client ends the dictation with `input_audio.flush` and then `input_audio.end`.
 * The model query parameter is required — without it the handshake is refused, which is what the 403 in
 * issue #128 was. The language is not sent: the realtime model detects it. Diarization does not work in
 * realtime (Mistral's docs), and none is asked for.
 */
internal class MistralRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    @Suppress("unused") private val language: String?,
    private val callbacks: RealtimeCallbacks,
    /** The socket address without its query — Mistral's own, except in tests. */
    private val endpoint: String = URL,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    private val audioGate = RealtimeAudioGate()
    private val transcript = StringBuilder()
    @Volatile private var done = false

    private companion object { const val URL = "wss://api.mistral.ai/v1/audio/transcriptions/realtime" }

    internal fun url(): String = "$endpoint?model=" + URLEncoder.encode(model, "UTF-8")

    fun connect() {
        val request = Request.Builder().url(url()).header("Authorization", "Bearer $apiKey").build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.content) {
                "session.created" -> {
                    runCatching { webSocket.send(sessionUpdate()) }
                    audioGate.markReady(
                        send = { pcm16, len -> sendAudioFrame(webSocket, pcm16, len) },
                        finish = { sendEnd(webSocket) },
                    )
                }
                "transcription.text.delta" -> {
                    val chunk = obj["text"]?.jsonPrimitive?.content ?: return
                    transcript.append(chunk)
                    callbacks.onPartial(transcript.toString())
                }
                "transcription.done" -> {
                    obj["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let {
                        transcript.setLength(0)
                        transcript.append(it)
                    }
                    finalizeAndClose(webSocket)
                }
                "error" -> {
                    val detail = obj["error"]?.let { e ->
                        runCatching { e.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() ?: e.toString()
                    } ?: "unknown error"
                    emitError(java.io.IOException("Mistral realtime: $detail"))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "mistral realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        // A close from the server without a transcription.done before it still ends the session.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = finalizeAndClose(webSocket)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finalizeAndClose(webSocket)
    }

    internal fun sessionUpdate(): String = buildJsonObject {
        put("type", "session.update")
        put("session", buildJsonObject {
            put("audio_format", buildJsonObject {
                put("encoding", "pcm_s16le")
                put("sample_rate", 16_000)
            })
        })
    }.toString()

    private fun sendAudioFrame(socket: WebSocket, pcm16: ByteArray, len: Int) {
        val msg = buildJsonObject {
            put("type", "input_audio.append")
            put("audio", java.util.Base64.getEncoder().encodeToString(pcm16.copyOfRange(0, len)))
        }.toString()
        runCatching { socket.send(msg) }
    }

    private fun sendEnd(socket: WebSocket) {
        runCatching { socket.send("""{"type":"input_audio.flush"}""") }
        runCatching { socket.send("""{"type":"input_audio.end"}""") }
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length -> ws?.let { sendAudioFrame(it, audio, length) } }
    }

    override fun finish() {
        val socket = ws ?: return finalizeAndClose(null)
        audioGate.finish { sendEnd(socket) }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finalizeAndClose(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        if (transcript.isNotEmpty()) callbacks.onFinalSegment(transcript.toString())
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Gladia live transcription (Solaria 1, the only live model). Two steps: `POST /v2/live?region=eu-west` with the
 * key (`x-gladia-key`) and the audio format answers with a WebSocket address that already carries a session
 * token; the socket then takes the audio as binary frames (16 kHz mono PCM16, as announced in the first call)
 * and ends with `{"type":"stop_recording"}`, after which the server processes what is left and closes with 1000.
 * `transcript` messages carry `data.is_final` and `data.utterance.text`; with `receive_partial_transcripts` the
 * not yet final ones arrive too, and a newer event of the same utterance replaces the one before it.
 *
 * The EU region is Gladia's default; it is named in the request so it stays that way. A language is pinned when
 * the user pinned one, and the languages the user dictates in are offered otherwise ([expectedLanguages]).
 * Custom vocabulary is not sent here: a realtime session has no channel for it yet.
 *
 * Audio spoken while the first call is still on its way waits in [RealtimeAudioGate]; a failed first call is an
 * error like any other, and the engine falls back to batch transcription.
 */
internal class GladiaRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    @Suppress("unused") private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
    private val expectedLanguages: List<String> = emptyList(),
    /** The address of the session call — Gladia's own, except in tests. */
    private val initUrl: String = INIT_URL,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var ws: WebSocket? = null
    private val audioGate = RealtimeAudioGate()
    /** The latest provisional text, kept so a close before its final does not lose it. */
    @Volatile private var pending = ""
    @Volatile private var done = false

    private companion object { const val INIT_URL = "https://api.gladia.io/v2/live?region=eu-west" }

    /** The body of the session call: PCM16 mono 16 kHz, partial transcripts, and the language(s). */
    internal fun initBody(): String = buildJsonObject {
        put("encoding", "wav/pcm")
        put("bit_depth", 16)
        put("sample_rate", 16_000)
        put("channels", 1)
        put("model", "solaria-1")
        putJsonObject("language_config") {
            val pinned = language?.takeIf { it.isNotBlank() && it != "detect" }?.substringBefore('-')
            put("languages", buildJsonArray {
                if (pinned != null) add(pinned)
                else expectedLanguages.map { it.substringBefore('-') }.distinct().forEach { add(it) }
            })
            put("code_switching", false)
        }
        putJsonObject("messages_config") {
            put("receive_partial_transcripts", true)
            put("receive_final_transcripts", true)
        }
    }.toString()

    fun connect() {
        val request = Request.Builder()
            .url(initUrl)
            .header("x-gladia-key", apiKey)
            .post(initBody().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = emitError(e)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    val url = runCatching { json.parseToJsonElement(text).jsonObject["url"]?.jsonPrimitive?.content }.getOrNull()
                    if (!it.isSuccessful || url.isNullOrBlank()) {
                        emitError(IOException("Gladia live: HTTP ${it.code} ${text.take(200)}"))
                        return
                    }
                    if (done) return   // cancelled while the call was on its way
                    ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
                }
            }
        })
    }

    private val listener = object : WebSocketListener() {
        // No acknowledgement is documented: the audio may flow as soon as the socket is open.
        override fun onOpen(webSocket: WebSocket, response: Response) {
            audioGate.markReady(
                send = { pcm16, len -> runCatching { webSocket.send(pcm16.toByteString(0, len)) } },
                finish = { sendStop(webSocket) },
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.content) {
                "transcript" -> {
                    val data = obj["data"]?.jsonObject ?: return
                    val utterance = data["utterance"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                    if (utterance.isBlank()) return
                    if (data["is_final"]?.jsonPrimitive?.booleanOrNull == true) {
                        pending = ""
                        callbacks.onFinalSegment(utterance)
                    } else {
                        pending = utterance
                        callbacks.onPartial(utterance)
                    }
                }
                "error" -> emitError(IOException("Gladia live: $text".take(300)))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "gladia realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        // After stop_recording Gladia closes the socket itself: answering completes the close handshake.
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    private fun sendStop(socket: WebSocket) {
        runCatching { socket.send("""{"type":"stop_recording"}""") }
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length -> ws?.send(audio.toByteString(0, length)) }
    }

    override fun finish() {
        audioGate.finish { ws?.let { sendStop(it) } }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        pending.takeIf { it.isNotEmpty() }?.let { callbacks.onFinalSegment(it) }
        pending = ""
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Deepgram streaming transcription. Streams raw 16 kHz mono PCM16 as binary WebSocket frames, and speaks
 * whichever of Deepgram's two wire versions the chosen model belongs to — the framing around them (`Token`
 * auth, binary audio, `CloseStream` to flush) is the same:
 *
 *  - **Nova/Whisper** on `wss://api.deepgram.com/v1/listen`, reporting `Results`: each interim revises the
 *    current segment ([RealtimeCallbacks.onPartial]), `is_final` settles it ([onFinalSegment]).
 *  - **Flux** on `/v2/listen`, reporting `TurnInfo` (issue #291). Flux is Deepgram's conversational model:
 *    it decides itself when a turn has ended instead of waiting out a silence timer, and every event
 *    carries the whole turn so far — so each one is a partial until `EndOfTurn` settles it.
 *
 * Text that is still unsettled when the socket closes is emitted as a final segment, so the tail of a
 * dictation survives a close that arrives before the last `is_final`/`EndOfTurn`.
 */
private class DeepgramRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    /** Latest partial, cleared when it is settled — emitted at close if nothing ever settled it. */
    @Volatile private var pending = ""
    @Volatile private var done = false

    /** The dictation language the user pinned, or null when they left auto-detection on. */
    private val pinnedLanguage = language?.takeIf { it.isNotBlank() && it != "detect" }

    fun connect() {
        val request = Request.Builder()
            .url(if (model.startsWith("flux")) fluxUrl() else listenUrl())
            .header("Authorization", "Token $apiKey")
            .build()
        ws = client.newWebSocket(request, listener)
    }

    private fun listenUrl(): String {
        val lang = pinnedLanguage?.let { "&language=$it" }.orEmpty()
        return "wss://api.deepgram.com/v1/listen?model=$model" +
            "&encoding=linear16&sample_rate=16000&channels=1&interim_results=true&punctuate=true$lang"
    }

    /**
     * Flux takes neither `interim_results` nor `punctuate` — the turn is its unit, and its transcript comes
     * formatted. Its language parameter is a *hint* that biases detection, which only the multilingual
     * model does at all; the single-language models reject the parameter, and no variant knows regions.
     */
    private fun fluxUrl(): String {
        val hint = pinnedLanguage
            ?.takeIf { model.endsWith("multi") }
            ?.let { "&language_hint=${it.substringBefore('-')}" }
            .orEmpty()
        return "wss://api.deepgram.com/v2/listen?model=$model&encoding=linear16&sample_rate=16000$hint"
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            when (obj["type"]?.jsonPrimitive?.content) {
                "Results" -> emit(
                    transcript = obj["channel"]?.jsonObject
                        ?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("transcript")?.jsonPrimitive?.content.orEmpty(),
                    settled = obj["is_final"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                "TurnInfo" -> emit(
                    transcript = obj["transcript"]?.jsonPrimitive?.content.orEmpty(),
                    settled = obj["event"]?.jsonPrimitive?.content == "EndOfTurn",
                )
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "deepgram realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        runCatching { ws?.send(pcm16.toByteString(0, len)) }
    }

    override fun finish() {
        val socket = ws ?: return finishClosed(null)
        // Ask Deepgram to flush the last segment; it then emits it and closes.
        runCatching { socket.send("""{"type":"CloseStream"}""") }
    }

    override fun cancel() {
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    /** Hands one message's transcript to the right callback and tracks what is still unsettled. */
    private fun emit(transcript: String, settled: Boolean) {
        if (transcript.isBlank()) return
        if (settled) {
            pending = ""
            callbacks.onFinalSegment(transcript)
        } else {
            pending = transcript
            callbacks.onPartial(transcript)
        }
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        pending.takeIf { it.isNotEmpty() }?.let { callbacks.onFinalSegment(it) }
        pending = ""
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * xAI Grok Voice Transcribe over `wss://api.x.ai/v1/stt` (issue #435). Everything is configured in the
 * query and the Bearer header — there is no setup message — and 16 kHz mono PCM16 goes out as raw binary
 * frames. The server announces `transcript.created` once its recogniser is up and asks for no audio before
 * that, so the microphone's first frames wait in a [RealtimeAudioGate] until it does.
 *
 * What comes back looks like Deepgram's results and does not mean the same thing; that difference is all
 * of [XaiTranscriptAssembler]. Stop sends `finalize` — xAI's documented push-to-talk release, which
 * settles the utterance in progress at once instead of after the endpointing silence — and then
 * `audio.done`, which the server answers with `transcript.done` before it closes.
 *
 * `language` is sent only where xAI formats numbers for it ([OpenAiCompatibleClient.xaiLanguage]); it
 * never limits which language is recognised. Endpointing stays at xAI's 400 ms: an utterance final is a
 * point where text settles, and the app decides for itself when the dictation ends.
 */
internal class XaiRealtimeSession(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val model: String,
    private val language: String?,
    private val callbacks: RealtimeCallbacks,
    /** The socket address without its query — xAI's own, except in tests. */
    private val endpoint: String = URL,
) : RealtimeSession {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var ws: WebSocket? = null
    private val audioGate = RealtimeAudioGate()
    private val transcript = XaiTranscriptAssembler(callbacks)
    @Volatile private var done = false

    private companion object {
        const val URL = "wss://api.x.ai/v1/stt"
    }

    /** The socket URL with the session's configuration in its query. */
    internal fun url(): String = buildString {
        append(endpoint).append("?sample_rate=16000&encoding=pcm&interim_results=true")
        if (model.isNotBlank()) append("&model=").append(URLEncoder.encode(model, "UTF-8"))
        OpenAiCompatibleClient.xaiLanguage(language)?.let { append("&language=").append(it) }
    }

    fun connect() {
        val request = Request.Builder().url(url()).header("Authorization", "Bearer $apiKey").build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            fun string(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull
            fun flag(name: String) = (obj[name] as? JsonPrimitive)?.booleanOrNull == true
            when (string("type")) {
                "transcript.created" -> audioGate.markReady(
                    send = { pcm16, len -> runCatching { webSocket.send(pcm16.toByteString(0, len)) } },
                    finish = { sendEnd(webSocket) },
                )
                "transcript.partial" -> transcript.onPartial(
                    text = string("text").orEmpty(),
                    isFinal = flag("is_final"),
                    speechFinal = flag("speech_final"),
                )
                "transcript.done" -> {
                    transcript.onDone(string("text").orEmpty())
                    finishClosed(webSocket)
                }
                // Almost every error closes the session on xAI's side anyway; the one that does not — a
                // client message it could not parse — cannot come from the two fixed ones sent here.
                "error" -> emitError(RuntimeException("xAI realtime error: ${string("message") ?: text}"))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            android.util.Log.w("DictateRT", "xai realtime WS failed (http=${response?.code}): ${t.message}")
            emitError(t)
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finishClosed(webSocket)
    }

    override fun sendAudio(pcm16: ByteArray, len: Int) {
        audioGate.sendAudio(pcm16, len) { audio, length ->
            runCatching { ws?.send(audio.toByteString(0, length)) }
        }
    }

    private fun sendEnd(socket: WebSocket) {
        runCatching {
            socket.send("""{"type":"finalize"}""")
            socket.send("""{"type":"audio.done"}""")
        }
    }

    override fun finish() {
        audioGate.finish {
            ws?.let { sendEnd(it) }
        }
    }

    override fun cancel() {
        audioGate.close()
        done = true
        runCatching { ws?.close(1000, null) }
        callbacks.onClosed()
    }

    private fun emitError(t: Throwable) {
        if (done) return
        done = true
        audioGate.close()
        runCatching { ws?.cancel() }
        callbacks.onError(t)
        callbacks.onClosed()
    }

    private fun finishClosed(webSocket: WebSocket?) {
        if (done) return
        done = true
        audioGate.close()
        transcript.flush()
        runCatching { (webSocket ?: ws)?.close(1000, null) }
        callbacks.onClosed()
    }
}

/**
 * Turns xAI's streaming events into [RealtimeCallbacks] (issue #435) — the part of its protocol the
 * documentation states loosely and two other projects had to measure.
 *
 * Every event is a `transcript.partial` with two flags, which between them mean three things:
 *  - **interim** (`is_final` false): a guess at the audio *since the last lock*, not at the utterance.
 *  - **chunk final** (`is_final` only): about three seconds of speech, locked.
 *  - **utterance final** (both): the speaker paused, and the event restates the **whole utterance** —
 *    every chunk final inside it again, re-punctuated.
 *
 * So a chunk final is never handed on as a final segment. Doing that and then appending the utterance
 * final is how pipecat came to print every sentence twice (pipecat-ai/pipecat#5671: 76 % of its benchmark
 * transcripts doubled). Chunks are held, shown in the preview with the interim that follows them, and
 * replaced by the utterance final. Captured against the live endpoint by pipecat and by
 * Project-N-E-K-O/N.E.K.O#3073, and the two agree:
 *
 *     interim       "my order"
 *     chunk final   "my order number"
 *     interim       "is four"
 *     chunk final   "is four two one."
 *     utterance     "my order number is four two one."
 *
 * N.E.K.O also found that noise produces empty events of the two other kinds — 34 in 25 seconds — and
 * never an utterance final; they change nothing here. An utterance final that is itself empty falls back
 * to its own chunks, so a blank restatement cannot swallow text the server had already locked.
 *
 * `transcript.done` is the one event nobody has pinned down: the reference calls its text the "final
 * transcript", and its own example carries an empty one. So it is believed only where no reading of it
 * could repeat anything — while nothing has been settled yet. Otherwise what is still open when the
 * session ends is settled from what is here: its locked chunks, then the last guess, because a word heard
 * and not confirmed is worth more than one dropped.
 *
 * Only ever driven from one socket's reader thread, so it keeps no locks.
 */
internal class XaiTranscriptAssembler(private val callbacks: RealtimeCallbacks) {

    /** The chunk finals of the utterance in progress — locked text its utterance final will restate. */
    private val locked = mutableListOf<String>()

    /** The guess at the audio after the last lock. */
    private var interim = ""

    /** What the preview last showed, so a run of identical empty events does not repaint it each time. */
    private var shown = ""

    /** Whether any final segment has gone out, which is all [onDone] needs to know. */
    private var settledAny = false

    fun onPartial(text: String, isFinal: Boolean, speechFinal: Boolean) {
        val piece = text.trim()
        when {
            isFinal && speechFinal -> {
                val chunks = locked.toList()
                locked.clear()
                interim = ""
                shown = ""
                if (piece.isNotEmpty()) settle(piece) else chunks.forEach(::settle)
            }
            isFinal -> {
                if (piece.isNotEmpty()) locked += piece
                interim = ""
                preview()
            }
            else -> {
                interim = piece
                preview()
            }
        }
    }

    fun onDone(text: String) {
        val piece = text.trim()
        if (!settledAny && piece.isNotEmpty()) {
            locked.clear()
            interim = ""
            settle(piece)
        }
        flush()
    }

    /** Settles whatever the server never did — locked chunks first, then the last guess. */
    fun flush() {
        val open = locked + interim
        locked.clear()
        interim = ""
        open.filter { it.isNotEmpty() }.forEach(::settle)
    }

    private fun preview() {
        val text = (locked + interim).filter { it.isNotEmpty() }.joinToString(" ")
        if (text == shown) return
        shown = text
        callbacks.onPartial(text)
    }

    private fun settle(piece: String) {
        settledAny = true
        callbacks.onFinalSegment(piece)
    }
}

/**
 * True for OpenAI's gpt-transcribe generation, which renamed the singular `language` field to a
 * `languages` array. Matched on the id prefix so later snapshots and variants are covered, while
 * gpt-realtime-whisper and the gpt-4o-*-transcribe models keep the old field (issue #248).
 *
 * Measured on the batch endpoint (2026-08-28): gpt-transcribe there answers an invalid code under
 * `language`, `languages` and `languages[]` alike, so it reads all three and this branch is a
 * courtesy rather than the thing that makes a language choice apply. What the array does earn is the
 * *list*: every entry is validated, so several languages genuinely arrive (issue #99), which no
 * singular field can express. Unverified for the realtime socket, where the field name may well
 * matter — hence the branch stays.
 *
 * **The prefix is deliberately not vendor-tolerant.** OpenRouter's id for the same model reads
 * `openai/gpt-transcribe` and therefore does NOT match, which is correct: OpenRouter's transcription
 * endpoint documents the singular `language` and nothing else, and an unread multipart field is
 * dropped in silence rather than refused — so "generalising" this to strip a `vendor/` prefix would
 * turn every language choice on OpenRouter into a no-op with no error to show for it. A test in
 * OpenAiCompatibleClientNetworkTest holds that line (issue #321).
 */
internal fun usesLanguagesField(model: String): Boolean {
    val id = model.lowercase()
    return id.startsWith("gpt-transcribe") || id.startsWith("gpt-live-transcribe")
}

/**
 * The codes for a provider field that takes a *list* of languages: the pinned language when the user
 * chose one — a choice stays a choice, however many the model could juggle — and otherwise the
 * languages they actually dictate in, which is what turns "detect automatically" into "detect among
 * my languages" (issue #99). Empty means detect freely, as before.
 */
internal fun languageHintsOf(language: String?, expectedLanguages: List<String>): List<String> {
    val pinned = language?.takeIf { it.isNotBlank() && it != "detect" }
    return (if (pinned != null) listOf(pinned) else expectedLanguages)
        .filter { it.isNotBlank() && it != "detect" }
}

/**
 * Whether a multipart transcription goes to Mistral's own `audio/transcriptions`, which documents a
 * narrower field set than OpenAI's (no `prompt`, no `response_format`, two-letter `language`). Judged
 * by the host, so a Voxtral model reached through OpenRouter or a custom server keeps the OpenAI form.
 */
internal fun isMistralVoxtral(baseUrl: String, model: String): Boolean =
    baseUrl.contains("api.mistral.ai", ignoreCase = true) && model.lowercase().startsWith("voxtral")
