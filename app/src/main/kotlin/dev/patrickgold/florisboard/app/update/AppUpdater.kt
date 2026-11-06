/*
 * Copyright (C) 2026 Luxx1993
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dev.patrickgold.florisboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Updates the app from the `apk` branch of the repository, the same file the README links to.
 *
 * `scripts/publish-apk.sh` puts a `version.json` next to the APK (version code, name, commit, SHA-256, size).
 * [check] compares its version code with the installed one; [install] downloads the APK, checks its SHA-256 and
 * hands it to Android's [PackageInstaller] as an update of this very app. Same package and same signature, so
 * Android keeps everything the app stores — keys, statistics, history, notes, styles — exactly as an install
 * over the old APK by hand does. Android refuses an APK with another signature, so a foreign file cannot get in.
 *
 * The first update asks once for "install unknown apps" for diktat and shows Android's confirmation. From
 * Android 12 on, later updates can run without the dialog, because the app is then its own installer of
 * record ([PackageInstaller.SessionParams.setRequireUserAction]).
 *
 * Only debug builds (the APK from GitHub) update themselves. The Play version is updated by Google Play,
 * and Play does not allow self-updates; its manifest has no install permission.
 */
object AppUpdater {
    private const val BASE_URL = "https://github.com/Luxx1993/unisono-diktat/raw/apk/"
    private const val VERSION_FILE = "version.json"

    /** True for the build that can update itself (see the class comment). */
    val isSupported: Boolean get() = BuildConfig.BUILD_TYPE == "debug"

    @Serializable
    data class Release(
        val versionCode: Long,
        val versionName: String,
        val commit: String = "",
        val sha256: String,
        val size: Long = 0L,
        val apk: String = "Dictate-Diktat-arm64.apk",
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val versionName: String) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        /** Android must allow diktat to install apps first; the download is kept for the second tap. */
        data class NeedsPermission(val release: Release) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun installedVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: BuildConfig.VERSION_NAME

    /** Whether [release] is newer than what is installed. Pure, for tests. */
    internal fun isNewer(release: Release, installedCode: Long): Boolean = release.versionCode > installedCode

    internal fun parseRelease(text: String): Release = json.decodeFromString(Release.serializer(), text)

    /** Reads `version.json` and sets [state] to [State.Available] or [State.UpToDate]. */
    suspend fun check(context: Context) {
        if (!isSupported) return
        val busy = _state.value
        if (busy is State.Checking || busy is State.Downloading || busy is State.Installing) return
        _state.value = State.Checking
        _state.value = try {
            val release = withContext(Dispatchers.IO) {
                // The query only defeats caches between here and GitHub; the file itself is the same.
                val request = Request.Builder()
                    .url(BASE_URL + VERSION_FILE + "?t=" + System.currentTimeMillis())
                    .header("Cache-Control", "no-cache")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    parseRelease(response.body?.string().orEmpty())
                }
            }
            if (isNewer(release, installedVersionCode(context))) State.Available(release)
            else State.UpToDate(installedVersionName(context))
        } catch (e: Exception) {
            State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun updateFile(context: Context): File = File(File(context.cacheDir, "update").apply { mkdirs() }, "diktat-update.apk")

    /** Downloads [release] (unless already here and intact) and starts the installation. */
    suspend fun install(context: Context, release: Release) {
        if (!isSupported) return
        val appContext = context.applicationContext
        try {
            val file = updateFile(appContext)
            if (!(file.exists() && sha256Of(file).equals(release.sha256, ignoreCase = true))) {
                _state.value = State.Downloading(release, 0f)
                download(release, file)
                val actual = sha256Of(file)
                if (!actual.equals(release.sha256, ignoreCase = true)) {
                    file.delete()
                    throw IOException("Prüfsumme stimmt nicht (Download unvollständig?)")
                }
            }
            if (!appContext.packageManager.canRequestPackageInstalls()) {
                _state.value = State.NeedsPermission(release)
                return
            }
            _state.value = State.Installing(release)
            withContext(Dispatchers.IO) { commitSession(appContext, file) }
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: e.javaClass.simpleName, release)
        }
    }

    private suspend fun download(release: Release, target: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(BASE_URL + release.apk).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            val partial = File(target.parentFile, target.name + ".part")
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                _state.value = State.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun commitSession(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Without a dialog once diktat installed itself before; Android asks otherwise.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("diktat.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, AppUpdateReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Called by [AppUpdateReceiver] when Android reports how the installation went. */
    internal fun onInstallResult(success: Boolean, message: String?) {
        val release = (_state.value as? State.Installing)?.release
        _state.value = if (success) State.Idle else State.Failed(message ?: "Installation abgebrochen", release)
    }

    /** Drops a downloaded APK left over from an earlier update. */
    fun cleanUp(context: Context) {
        runCatching { File(context.cacheDir, "update").deleteRecursively() }
    }
}
