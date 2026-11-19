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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * Receives the result of the installation [AppUpdater] started. When Android needs the user to confirm,
 * it opens Android's own dialog; on success the app is replaced and Android restarts it on the next open.
 * Declared only in the debug manifest, next to the install permission.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    AppUpdater.onInstallResult(false, "Android hat keinen Bestätigungsdialog geliefert")
                    return
                }
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { AppUpdater.onInstallResult(false, it.message) }
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdater.onInstallResult(true, null)
            else -> AppUpdater.onInstallResult(
                false,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status $status",
            )
        }
    }
}
