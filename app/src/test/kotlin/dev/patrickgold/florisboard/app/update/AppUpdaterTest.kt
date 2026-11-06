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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdaterTest {

    @Test
    fun `version json as publish-apk writes it is read, unknown fields ignored`() {
        val release = AppUpdater.parseRelease(
            """{"versionCode":1497263,"versionName":"6.4.0+abc1234","commit":"abc1234def","sha256":"ff00",""" +
                """"size":58400000,"apk":"Dictate-Diktat-arm64.apk","builtAt":"2026-11-06T10:00:00Z"}""",
        )
        assertEquals(1497263L, release.versionCode)
        assertEquals("6.4.0+abc1234", release.versionName)
        assertEquals("Dictate-Diktat-arm64.apk", release.apk)
        assertEquals(58400000L, release.size)
    }

    @Test
    fun `only a higher version code is an update`() {
        val release = AppUpdater.Release(versionCode = 1000, versionName = "x", sha256 = "00")
        assertTrue(AppUpdater.isNewer(release, installedCode = 282))
        assertFalse(AppUpdater.isNewer(release, installedCode = 1000))
        assertFalse(AppUpdater.isNewer(release, installedCode = 2000))
    }
}
