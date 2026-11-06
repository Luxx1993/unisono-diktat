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

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.apptheme.LocalUnisono
import dev.patrickgold.florisboard.app.main.UNavRow
import dev.patrickgold.florisboard.app.main.UnisonoIcons
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** Checks once per app start whether a newer APK is on GitHub, so the row can say so without a tap. */
private var checkedThisProcess = false

/**
 * The "Update app" row: one tap checks, the next downloads and installs (after a short confirmation).
 * Shown only in the build that can update itself, see [AppUpdater.isSupported].
 */
@Composable
fun AppUpdateRow(modifier: Modifier = Modifier) {
    if (!AppUpdater.isSupported) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdater.state.collectAsState()
    var confirm by remember { mutableStateOf<AppUpdater.Release?>(null) }
    val c = LocalUnisono.current

    LaunchedEffect(Unit) {
        if (!checkedThisProcess) {
            checkedThisProcess = true
            AppUpdater.check(context)
        }
    }

    fun sizeOf(release: AppUpdater.Release) =
        if (release.size > 0) Formatter.formatShortFileSize(context, release.size) else "?"

    val installed = AppUpdater.installedVersionName(context)
    val subtitle = when (val s = state) {
        AppUpdater.State.Idle -> stringRes(R.string.app_update__idle, "version" to installed)
        AppUpdater.State.Checking -> stringRes(R.string.app_update__checking)
        is AppUpdater.State.UpToDate -> stringRes(R.string.app_update__up_to_date, "version" to s.versionName)
        is AppUpdater.State.Available ->
            stringRes(R.string.app_update__available, "version" to s.release.versionName, "size" to sizeOf(s.release))
        is AppUpdater.State.Downloading ->
            stringRes(R.string.app_update__downloading, "percent" to (s.progress * 100).toInt())
        is AppUpdater.State.NeedsPermission -> stringRes(R.string.app_update__needs_permission)
        is AppUpdater.State.Installing -> stringRes(R.string.app_update__installing)
        is AppUpdater.State.Failed -> stringRes(R.string.app_update__failed, "error" to s.message)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        UNavRow(
            title = stringRes(R.string.app_update__title),
            subtitle = subtitle,
            icon = UnisonoIcons.Download,
            onClick = {
                when (val s = state) {
                    is AppUpdater.State.Available -> confirm = s.release
                    is AppUpdater.State.NeedsPermission -> {
                        if (context.packageManager.canRequestPackageInstalls()) {
                            scope.launch { AppUpdater.install(context, s.release) }
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    ("package:" + context.packageName).toUri(),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    is AppUpdater.State.Failed ->
                        if (s.release != null) confirm = s.release else scope.launch { AppUpdater.check(context) }
                    is AppUpdater.State.Checking, is AppUpdater.State.Downloading, is AppUpdater.State.Installing -> Unit
                    else -> scope.launch { AppUpdater.check(context) }
                }
            },
        )
        (state as? AppUpdater.State.Downloading)?.let { s ->
            LinearProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                color = c.textPrimary,
                trackColor = c.textSecondary.copy(alpha = 0.2f),
            )
        }
    }

    confirm?.let { release ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringRes(R.string.app_update__confirm_title)) },
            text = {
                Text(
                    stringRes(
                        R.string.app_update__confirm_text,
                        "version" to release.versionName,
                        "size" to sizeOf(release),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { AppUpdater.install(context, release) }
                }) { Text(stringRes(R.string.app_update__confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringRes(R.string.app_update__confirm_cancel)) }
            },
        )
    }
}
