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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.conversation.OpenCodeTestTags
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.theme.JetchatTheme
import com.github.go_kenka.opencode.theme.opencodeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JetchatDrawerContent(
    sessions: List<OpenCodeSession>,
    selectedSessionId: String?,
    onSessionClicked: (String) -> Unit,
    onSettingsClicked: () -> Unit,
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

        DrawerItemHeader("Chats")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("搜索会话") },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = tokens.palette.textSecondary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = drawerContentHorizontalInset, vertical = spacing.sm)
                .testTag(OpenCodeTestTags.DrawerSearch),
            shape = RoundedCornerShape(tokens.shapes.large),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = tokens.palette.surface,
                focusedContainerColor = tokens.palette.surface,
                unfocusedBorderColor = tokens.palette.borderTertiary,
                focusedBorderColor = tokens.palette.borderSecondary,
            ),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = "暂无会话",
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

        HorizontalDivider(
            color = tokens.palette.borderTertiary,
            modifier = Modifier.padding(horizontal = drawerContentHorizontalInset),
        )
        SettingsItem(
            onClick = onSettingsClicked,
            modifier = Modifier.padding(horizontal = drawerContentHorizontalInset, vertical = spacing.sm),
        )
    }
}

@Composable
private fun DrawerHeader() {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    Row(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md), verticalAlignment = CenterVertically) {
        Icon(
            painter = painterResource(id = R.drawable.ic_opencode_logo),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.padding(start = spacing.sm)) {
            Text(
                text = "OpenCode",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.palette.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "最近会话",
                fontSize = typography.small,
                color = tokens.palette.textSecondary,
            )
        }
    }
}

@Composable
private fun DrawerItemHeader(text: String) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    Box(
        modifier = Modifier
            .height(40.dp)
            .padding(horizontal = spacing.lg),
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
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_jetchat),
            contentDescription = null,
            tint = if (selected) tokens.palette.accent else tokens.palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.padding(start = spacing.sm)) {
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
private fun SettingsItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val shape = RoundedCornerShape(tokens.shapes.large)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.palette.surface)
            .border(tokens.borders.default, tokens.palette.borderTertiary, shape)
            .clickable(onClick = onClick)
            .testTag(OpenCodeTestTags.DrawerSettings),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_settings),
            contentDescription = null,
            tint = tokens.palette.textSecondary,
            modifier = Modifier.padding(start = spacing.lg),
        )
        Text(
            text = "设置",
            fontSize = typography.base,
            color = tokens.palette.textPrimary,
            modifier = Modifier.padding(start = spacing.md),
        )
    }
}

@Composable
@Preview
fun DrawerPreview() {
    JetchatTheme {
        Surface {
            JetchatDrawerContent(
                sessions = listOf(
                    OpenCodeSession("s1", "p1", "/Users/demo/app", "修复登录问题", System.currentTimeMillis()),
                    OpenCodeSession("s2", "p2", "/Users/demo/web", "首页重构", System.currentTimeMillis() - 60000L),
                ),
                selectedSessionId = "s1",
                onSessionClicked = {},
                onSettingsClicked = {},
            )
        }
    }
}
