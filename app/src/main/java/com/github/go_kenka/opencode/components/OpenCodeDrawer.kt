/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.go_kenka.opencode.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.conversation.ConversationTestTags
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.theme.OpenCodeTheme
import com.github.go_kenka.opencode.theme.opencodeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OpenCodeDrawerContent(
    sessions: List<OpenCodeSession>,
    selectedSessionId: String?,
    onSessionClicked: (String) -> Unit,
    uiLanguage: UiLanguage,
    themeMode: ThemeMode,
    onLanguageChanged: (UiLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val drawerContentHorizontalInset = spacing.md
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        val keyword = query.trim().lowercase()
        sessions
            .sortedByDescending { it.time }
            .filter {
                if (keyword.isBlank()) return@filter true
                val title = it.title.orEmpty().lowercase()
                val directory = it.directory.orEmpty().lowercase()
                title.contains(keyword) || directory.contains(keyword)
            }
            .take(10)
    }

    Column {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        DrawerHeader()
        HorizontalDivider(color = tokens.palette.borderTertiary)

        DrawerItemHeader(stringResource(R.string.drawer_sessions))
        DrawerSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = drawerContentHorizontalInset, vertical = spacing.xs)
                .testTag(ConversationTestTags.DrawerSearch),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_no_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        SessionItem(
                            session = session,
                            selected = selectedSessionId == session.id,
                            onClick = { onSessionClicked(session.id) },
                        )
                        HorizontalDivider(color = tokens.palette.borderTertiary)
                    }
                }
            }
        }

        HorizontalDivider(color = tokens.palette.borderTertiary)
        DrawerPreferencesSection(
            uiLanguage = uiLanguage,
            themeMode = themeMode,
            onLanguageChanged = onLanguageChanged,
            onThemeModeChanged = onThemeModeChanged,
        )
    }
}

@Composable
private fun DrawerHeader() {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    Row(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm), verticalAlignment = CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.ic_opencode_logo),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = spacing.sm)) {
            Text(
                text = "OpenCode",
                fontSize = typography.large,
                color = tokens.palette.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.drawer_recent_sessions),
                fontSize = typography.small,
                color = tokens.palette.textSecondary,
            )
        }
    }
}

@Composable
private fun DrawerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = tokens.palette.textPrimary,
            fontSize = typography.base,
            lineHeight = typography.baseLineHeight,
        ),
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(tokens.palette.surface)
            .border(tokens.borders.default, tokens.palette.borderTertiary, shape)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        decorationBox = { inner ->
            Row(verticalAlignment = CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = tokens.palette.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(spacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(R.string.drawer_search_sessions),
                            color = tokens.palette.textSecondary,
                            fontSize = typography.base,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DrawerPreferencesSection(
    uiLanguage: UiLanguage,
    themeMode: ThemeMode,
    onLanguageChanged: (UiLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val languageOptions = listOf(
        PreferenceOption(UiLanguage.SYSTEM, stringResource(R.string.language_system), leadingEmoji = "🌐"),
        PreferenceOption(UiLanguage.ZH, stringResource(R.string.language_zh), leadingEmoji = "🇨🇳"),
        PreferenceOption(UiLanguage.EN, stringResource(R.string.language_en), leadingEmoji = "🇺🇸"),
    )
    val themeOptions = listOf(
        PreferenceOption(ThemeMode.SYSTEM, stringResource(R.string.theme_system), leadingIconRes = R.drawable.ic_settings),
        PreferenceOption(ThemeMode.LIGHT, stringResource(R.string.theme_light), leadingIconRes = R.drawable.ic_light_mode),
        PreferenceOption(ThemeMode.DARK, stringResource(R.string.theme_dark), leadingIconRes = R.drawable.ic_dark_mode),
    )
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xs),
    ) {
        DrawerPreferenceRow(
            iconRes = R.drawable.ic_language,
            label = stringResource(R.string.drawer_language),
            value = languageOptions.firstOrNull { it.value == uiLanguage }?.label.orEmpty(),
            onClick = { showLanguageSheet = true },
        )
        HorizontalDivider(color = tokens.palette.borderTertiary)
        DrawerPreferenceRow(
            iconRes = R.drawable.ic_theme,
            label = stringResource(R.string.drawer_theme),
            value = themeOptions.firstOrNull { it.value == themeMode }?.label.orEmpty(),
            onClick = { showThemeSheet = true },
        )
    }

    if (showLanguageSheet) {
        PreferenceSelectionSheet(
            title = stringResource(R.string.drawer_language),
            selectedValue = uiLanguage,
            options = languageOptions,
            onDismiss = { showLanguageSheet = false },
            onSelected = {
                onLanguageChanged(it)
                showLanguageSheet = false
            },
        )
    }
    if (showThemeSheet) {
        PreferenceSelectionSheet(
            title = stringResource(R.string.drawer_theme),
            selectedValue = themeMode,
            options = themeOptions,
            onDismiss = { showThemeSheet = false },
            onSelected = {
                onThemeModeChanged(it)
                showThemeSheet = false
            },
        )
    }
}

@Composable
private fun DrawerPreferenceRow(
    iconRes: Int,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val shape = RoundedCornerShape(tokens.shapes.medium)
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .fillMaxWidth(),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tokens.palette.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.palette.textSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.palette.textPrimary,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = tokens.palette.textSecondary,
            modifier = Modifier
                .size(16.dp)
                .rotate(-90f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceSelectionSheet(
    title: String,
    selectedValue: T,
    options: List<PreferenceOption<T>>,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    val tokens = opencodeTokens()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.palette.surface,
        contentColor = tokens.palette.textPrimary,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.palette.textPrimary,
            modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(options) { option ->
                val selected = option.value == selectedValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(option.value) }
                        .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
                    verticalAlignment = CenterVertically,
                ) {
                    if (option.leadingIconRes != null) {
                        Icon(
                            painter = painterResource(id = option.leadingIconRes),
                            contentDescription = null,
                            tint = tokens.palette.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                    } else if (option.leadingEmoji != null) {
                        Text(
                            text = option.leadingEmoji,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.sm))
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) tokens.palette.accent else tokens.palette.textPrimary,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text(
                            text = "✓",
                            color = tokens.palette.accent,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                HorizontalDivider(color = tokens.palette.borderTertiary)
            }
        }
        Spacer(modifier = Modifier.height(tokens.spacing.lg))
    }
}

private data class PreferenceOption<T>(
    val value: T,
    val label: String,
    val leadingIconRes: Int? = null,
    val leadingEmoji: String? = null,
)

enum class UiLanguage {
    SYSTEM,
    ZH,
    EN,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
private fun DrawerItemHeader(text: String) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    Box(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = spacing.md),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.palette.textSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SessionItem(
    session: OpenCodeSession,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val title = session.title?.takeIf { it.isNotBlank() }
        ?: session.directory?.substringAfterLast('/')
        ?: session.id.take(8)
    val sessionTime = formatSessionTime(session.time)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) tokens.palette.surfaceInteractive else tokens.palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_opencode),
            contentDescription = null,
            tint = if (selected) tokens.palette.accent else tokens.palette.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.padding(start = spacing.sm).weight(1f)) {
            Text(
                text = title,
                fontSize = typography.base,
                color = if (selected) tokens.palette.accent else tokens.palette.textPrimary,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${session.directory.orEmpty()} · $sessionTime",
                fontSize = typography.small,
                color = tokens.palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return "--:--"
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

@Composable
@Preview
fun DrawerPreview() {
    OpenCodeTheme {
        Surface {
            OpenCodeDrawerContent(
                sessions = listOf(
                    OpenCodeSession("s1", "p1", "/Users/demo/app", "修复登录问题", System.currentTimeMillis()),
                    OpenCodeSession("s2", "p2", "/Users/demo/web", "首页重构", System.currentTimeMillis() - 60000L),
                ),
                selectedSessionId = "s1",
                onSessionClicked = {},
                uiLanguage = UiLanguage.SYSTEM,
                themeMode = ThemeMode.SYSTEM,
                onLanguageChanged = {},
                onThemeModeChanged = {},
            )
        }
    }
}
