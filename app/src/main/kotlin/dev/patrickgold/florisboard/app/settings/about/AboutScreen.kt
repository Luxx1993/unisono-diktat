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

package dev.patrickgold.florisboard.app.settings.about

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.apptheme.LocalUnisono
import dev.patrickgold.florisboard.app.apptheme.Unisono
import dev.patrickgold.florisboard.app.main.UCard
import dev.patrickgold.florisboard.app.main.UDivider
import dev.patrickgold.florisboard.app.main.UNavRow
import dev.patrickgold.florisboard.app.main.UnisonoPage
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.update.AppUpdateRow
import dev.patrickgold.florisboard.app.update.AppUpdater
import dev.patrickgold.florisboard.lib.util.launchUrl
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.stringRes

/** About page in the look of Entwurf 3: app name and credit on top, links and licences in two cards. */
@Composable
fun AboutScreen() = UnisonoPage(title = stringRes(R.string.about__title)) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val c = LocalUnisono.current
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The globe has no background of its own, so it takes the text colour rather than the launcher's white.
        Icon(
            modifier = Modifier.requiredSize(64.dp),
            imageVector = ImageVector.vectorResource(R.drawable.ic_diktat_globe_notification),
            contentDescription = stringRes(R.string.app_name_full),
            tint = c.textPrimary,
        )
        Text(stringRes(R.string.app_name_full), style = Unisono.SectionTitle, color = c.textPrimary)
        Text(
            stringRes(R.string.about__made_by),
            style = Unisono.Label,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
    }

    UCard {
        UNavRow(
            title = stringRes(R.string.about__version__title),
            subtitle = appVersion,
            modifier = Modifier.settingsSearchAnchor("about__version__title"),
            onClick = {
                try {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText(appVersion, appVersion))
                    Toast.makeText(context, R.string.about__version_copied__title, Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(
                        context,
                        context.stringRes(R.string.about__version_copied__error, "error_message" to e.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        if (AppUpdater.isSupported) {
            UDivider()
            AppUpdateRow()
        }
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__changelog__title),
            subtitle = stringRes(R.string.about__changelog__summary),
            modifier = Modifier.settingsSearchAnchor("about__changelog__title"),
            onClick = { context.launchUrl(R.string.florisboard__changelog_url, "version" to BuildConfig.VERSION_NAME) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__repository__title),
            subtitle = stringRes(R.string.about__repository__summary),
            modifier = Modifier.settingsSearchAnchor("about__repository__title"),
            onClick = { context.launchUrl(R.string.florisboard__repo_url) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__based_on_floris__title),
            subtitle = stringRes(R.string.about__based_on_floris__summary),
            modifier = Modifier.settingsSearchAnchor("about__based_on_floris__title"),
            onClick = { context.launchUrl(R.string.florisboard__upstream_repo_url) },
        )
    }

    UCard {
        UNavRow(
            title = stringRes(R.string.about__project_license__title),
            subtitle = stringRes(R.string.about__project_license__summary, "license_name" to "Apache 2.0"),
            modifier = Modifier.settingsSearchAnchor("about__project_license__title"),
            onClick = { navController.navigate(Routes.Settings.ProjectLicense) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__third_party_licenses__title),
            subtitle = stringRes(R.string.about__third_party_licenses__summary),
            onClick = { navController.navigate(Routes.Settings.ThirdPartyLicenses) },
        )
        UDivider()
        UNavRow(
            title = stringRes(R.string.about__data_attributions__title),
            subtitle = stringRes(R.string.about__data_attributions__summary),
            modifier = Modifier.settingsSearchAnchor("about__data_attributions__title"),
            onClick = { navController.navigate(Routes.Settings.DataAttributions) },
        )
    }
}
