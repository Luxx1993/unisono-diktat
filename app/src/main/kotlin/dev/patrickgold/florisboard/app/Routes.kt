/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import dev.patrickgold.florisboard.app.main.MainScreen
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchScreen
import dev.patrickgold.florisboard.app.settings.about.AboutScreen
import dev.patrickgold.florisboard.app.settings.about.DataAttributionsScreen
import dev.patrickgold.florisboard.app.settings.about.ProjectLicenseScreen
import dev.patrickgold.florisboard.app.settings.about.ThirdPartyLicensesScreen
import dev.patrickgold.florisboard.app.settings.advanced.BackupScreen
import dev.patrickgold.florisboard.app.settings.advanced.OtherScreen
import dev.patrickgold.florisboard.app.settings.advanced.RestoreScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateLanguagesScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateMappingsScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateNotesScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateAddProviderScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateProvidersScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateProxyScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictatePromptLibraryScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictatePromptsScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateFloatingButtonAppsScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateFloatingButtonScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateFormattingScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateOutputScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateRecordingScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateRewordingScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateHistoryScreen
import dev.patrickgold.florisboard.app.settings.dictate.DictateStatsScreen
import dev.patrickgold.florisboard.app.setup.SetupScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Deeplink(val path: String)

inline fun <reified T : Any> NavGraphBuilder.composableWithDeepLink(
    kClass: KClass<T>,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink) {
        "faulty class: $kClass with annotations ${kClass.annotations}"
    }
    composable<T>(
        deepLinks = listOf(navDeepLink<T>(basePath = "ui://florisboard/${deeplink.path}")),
        content = content,
    )
}

object Routes {
    object Setup {
        @Serializable
        object Screen
    }

    /** The four tabs of Entwurf 3, the app's start screen. */
    @Serializable
    @Deeplink("main")
    object Main

    object Settings {
        @Serializable
        @Deeplink("settings/search")
        object Search

        @Serializable
        @Deeplink("settings/dictate/languages")
        object DictateLanguages

        @Serializable
        @Deeplink("settings/dictate/providers")
        object DictateProviders

        @Serializable
        @Deeplink("settings/dictate/providers/add")
        object DictateProvidersAdd

        @Serializable
        @Deeplink("settings/dictate/mappings")
        object DictateMappings

        @Serializable
        @Deeplink("settings/dictate/proxy")
        object DictateProxy

        @Serializable
        @Deeplink("settings/dictate/rewording")
        object DictateRewording

        @Serializable
        @Deeplink("settings/dictate/stats")
        object DictateStats

        @Serializable
        @Deeplink("settings/dictate/history")
        object DictateHistory

        @Serializable
        @Deeplink("settings/dictate/notes")
        object DictateNotes

        @Serializable
        @Deeplink("settings/dictate/floating-button")
        object DictateFloatingButton

        @Serializable
        @Deeplink("settings/dictate/floating-button/apps")
        object DictateFloatingButtonApps

        @Serializable
        @Deeplink("settings/dictate/formatting")
        object DictateFormatting

        @Serializable
        @Deeplink("settings/dictate/recording")
        object DictateRecording

        @Serializable
        @Deeplink("settings/dictate/output")
        object DictateOutput

        @Serializable
        @Deeplink("settings/dictate/prompts")
        data class DictatePrompts(val editPromptId: Int = -1)

        @Serializable
        @Deeplink("settings/dictate/prompts/library")
        object DictatePromptLibrary

        @Serializable
        @Deeplink("settings/other")
        object Other

        @Serializable
        @Deeplink("settings/other/backup")
        object Backup

        @Serializable
        @Deeplink("settings/other/restore")
        object Restore

        @Serializable
        @Deeplink("settings/about")
        object About

        @Serializable
        @Deeplink("settings/about/project-license")
        object ProjectLicense

        @Serializable
        @Deeplink("settings/about/third-party-licenses")
        object ThirdPartyLicenses

        @Serializable
        @Deeplink("settings/about/data-attributions")
        object DataAttributions
    }
    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        startDestination: KClass<*>,
    ) {
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIn { IntOffset(it.width, 0) } + fadeIn()
            },
            exitTransition = {
                slideOut { IntOffset(-it.width, 0) } + fadeOut()
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85F,
                    transformOrigin = TransformOrigin(pivotFractionX = 0.8f, pivotFractionY = 0.5f)
                ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
        ) {
            composable<Setup.Screen> { SetupScreen() }

            composableWithDeepLink(Main::class) { MainScreen() }
            composableWithDeepLink(Settings.Search::class) { SettingsSearchScreen() }

            composableWithDeepLink(Settings.DictateLanguages::class) { DictateLanguagesScreen() }
            composableWithDeepLink(Settings.DictateProviders::class) { DictateProvidersScreen() }
            composableWithDeepLink(Settings.DictateProvidersAdd::class) { DictateAddProviderScreen() }
            composableWithDeepLink(Settings.DictateMappings::class) { DictateMappingsScreen() }
            composableWithDeepLink(Settings.DictateProxy::class) { DictateProxyScreen() }
            composableWithDeepLink(Settings.DictateRewording::class) { DictateRewordingScreen() }
            composableWithDeepLink(Settings.DictateFormatting::class) { DictateFormattingScreen() }
            composableWithDeepLink(Settings.DictateRecording::class) { DictateRecordingScreen() }
            composableWithDeepLink(Settings.DictateOutput::class) { DictateOutputScreen() }
            composableWithDeepLink(Settings.DictateStats::class) { DictateStatsScreen() }
            composableWithDeepLink(Settings.DictateHistory::class) { DictateHistoryScreen() }
            composableWithDeepLink(Settings.DictateNotes::class) { DictateNotesScreen() }
            composableWithDeepLink(Settings.DictateFloatingButton::class) { DictateFloatingButtonScreen() }
            composableWithDeepLink(Settings.DictateFloatingButtonApps::class) { DictateFloatingButtonAppsScreen() }
            composableWithDeepLink(Settings.DictatePrompts::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.DictatePrompts>()
                DictatePromptsScreen(editPromptId = payload.editPromptId)
            }
            composableWithDeepLink(Settings.DictatePromptLibrary::class) { DictatePromptLibraryScreen() }

            composableWithDeepLink(Settings.Other::class) { OtherScreen() }
            composableWithDeepLink(Settings.Backup::class) { BackupScreen() }
            composableWithDeepLink(Settings.Restore::class) { RestoreScreen() }

            composableWithDeepLink(Settings.About::class) { AboutScreen() }
            composableWithDeepLink(Settings.ProjectLicense::class) { ProjectLicenseScreen() }
            composableWithDeepLink(Settings.ThirdPartyLicenses::class) { ThirdPartyLicensesScreen() }
            composableWithDeepLink(Settings.DataAttributions::class) { DataAttributionsScreen() }
        }
    }
}
