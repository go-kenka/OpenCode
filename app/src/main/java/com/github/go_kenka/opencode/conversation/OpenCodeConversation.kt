/*
 * Copyright 2026 The Android Open Source Project
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

package com.github.go_kenka.opencode.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import android.widget.Toast
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeTodoItem
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import com.github.go_kenka.opencode.theme.opencodeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class OpenCodeColors(
    val bg: Color,
    val line: Color,
    val panel: Color,
    val panelAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentDim: Color,
    val warning: Color,
    val danger: Color,
    val onAccent: Color,
    val codePanel: Color,
)

object OpenCodeTestTags {
    const val ComposerContainer = "opencode_composer_container"
    const val ComposerToolbar = "opencode_composer_toolbar"
    const val AttachmentButton = "opencode_attachment_button"
    const val DrawerSearch = "opencode_drawer_search"
    const val DrawerSettings = "opencode_drawer_settings"
}

@Composable
private fun openCodeColors(): OpenCodeColors {
    val tokens = opencodeTokens()
    val palette = tokens.palette
    return OpenCodeColors(
        bg = palette.background,
        line = palette.borderTertiary,
        panel = palette.surface,
        panelAlt = palette.surfaceInteractive,
        textPrimary = palette.textPrimary,
        textSecondary = palette.textSecondary,
        accent = palette.accent,
        accentDim = palette.accent.copy(alpha = 0.35f),
        warning = palette.warning,
        danger = palette.error,
        onAccent = palette.onAccent,
        codePanel = palette.surface.copy(alpha = 0.72f),
    )
}

@Composable
fun OpenCodeConversationScreen(
    uiState: OpenCodeUiState,
    onProjectSelected: (String) -> Unit,
    onProjectDeleteConfirmed: (String) -> Unit,
    onAddProjectClick: () -> Unit,
    onAddProjectDismiss: () -> Unit,
    onAddProjectDirectoryOpen: (String) -> Unit,
    onAddProjectDirectoryUp: () -> Unit,
    onAddProjectSearchQueryChange: (String) -> Unit,
    onAddProjectConfirm: () -> Unit,
    onModeSelected: (OpenCodeMode) -> Unit,
    onModelSelected: (OpenCodeModelOption) -> Unit,
    onThinkingSelected: (ThinkingLevel) -> Unit,
    onMessageSent: (String) -> Unit,
    onAbortSending: () -> Unit,
    onPermissionAllowOnce: () -> Unit,
    onPermissionAllowAlways: () -> Unit,
    onPermissionDeny: () -> Unit,
    serverOptions: List<OpenCodeService> = emptyList(),
    onServerSelected: (OpenCodeService) -> Unit = {},
    showServicePicker: Boolean = false,
    discoveredServices: List<OpenCodeService> = emptyList(),
    onDiscoveredServiceSelected: (OpenCodeService) -> Unit = {},
    onDiscoveredServicePickerDismiss: () -> Unit = {},
    onRetryDiscovery: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onServerConfigClick: () -> Unit = {},
    onCreateSessionClick: () -> Unit = {},
    onFileUploadClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = openCodeColors()
    var showTodoDialog by rememberSaveable { mutableStateOf(false) }
    val messageListState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        val lastIndex = uiState.messages.lastIndex
        if (lastIndex >= 0) {
            messageListState.scrollToItem(lastIndex)
        }
    }
    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            TopCommandBar(
                uiState = uiState,
                onProjectSelected = onProjectSelected,
                onProjectDeleteConfirmed = onProjectDeleteConfirmed,
                onAddProjectClick = onAddProjectClick,
                onAddProjectDismiss = onAddProjectDismiss,
                onAddProjectDirectoryOpen = onAddProjectDirectoryOpen,
                onAddProjectDirectoryUp = onAddProjectDirectoryUp,
                onAddProjectSearchQueryChange = onAddProjectSearchQueryChange,
                onAddProjectConfirm = onAddProjectConfirm,
                onMenuClick = onMenuClick,
                onServerConfigClick = onServerConfigClick,
                onCreateSessionClick = onCreateSessionClick,
                onRetryDiscovery = onRetryDiscovery,
                serverOptions = serverOptions,
                onServerSelected = onServerSelected,
            )
            HorizontalDivider(color = colors.line)
            TodoStatusBar(
                todos = uiState.todos,
                onClick = { showTodoDialog = true },
            )
            if (uiState.isHistoryLoading && uiState.messages.isEmpty()) {
                HistoryLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else if (uiState.messages.isEmpty()) {
                EmptyConversationState(
                    projectName = uiState.selectedProject?.name ?: "构建任何东西",
                    projectPath = uiState.selectedProject?.directory ?: "/",
                    gitBranch = uiState.selectedProject?.gitBranch,
                    gitStatusSummary = uiState.selectedProject?.gitStatusSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    state = messageListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.messages) { msg ->
                        if (msg.role == "user") {
                            UserCard(msg.content, msg.time)
                        } else {
                            AssistantCard(msg.content, msg.time, isError = msg.isError)
                        }
                    }
                }
            }
            uiState.pendingPermission?.let { permission ->
                PermissionRequestCard(
                    title = permission.title,
                    isLoading = uiState.isRespondingPermission,
                    onAllowOnce = onPermissionAllowOnce,
                    onAllowAlways = onPermissionAllowAlways,
                    onDeny = onPermissionDeny,
                )
            }
            ComposerBar(
                uiState = uiState,
                onModeSelected = onModeSelected,
                onModelSelected = onModelSelected,
                onThinkingSelected = onThinkingSelected,
                onMessageSent = onMessageSent,
                onAbortSending = onAbortSending,
                onFileUploadClick = onFileUploadClick,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        }

        if (showServicePicker && discoveredServices.size > 1) {
            ServicePickerSheet(
                title = "发现多个服务器",
                services = discoveredServices,
                selectedService = uiState.service,
                onSelect = onDiscoveredServiceSelected,
                onDismiss = onDiscoveredServicePickerDismiss,
            )
        }
        if (showTodoDialog) {
            TodoDialog(
                todos = uiState.todos,
                onClose = { showTodoDialog = false },
            )
        }
    }
}

@Composable
private fun TodoStatusBar(
    todos: List<OpenCodeTodoItem>,
    onClick: () -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val done = todos.count { it.done }
    val total = todos.size
    val latest = todos.lastOrNull()?.text ?: "暂无待办"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs)
            .clip(RoundedCornerShape(tokens.shapes.medium))
            .background(colors.panelAlt)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TODO", color = colors.accent, fontSize = typography.small, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(spacing.sm))
        Text("($done/$total)", color = colors.textSecondary, fontSize = typography.small)
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = latest,
            color = colors.textPrimary,
            fontSize = typography.small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TodoDialog(
    todos: List<OpenCodeTodoItem>,
    onClose: () -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = colors.panel,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "完整 TODO",
                    color = colors.textPrimary,
                    fontSize = typography.base,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = colors.line)
            if (todos.isEmpty()) {
                Text("暂无 TODO", color = colors.textSecondary, fontSize = typography.small)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(todos) { todo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = if (todo.done) "✓" else "•",
                                color = if (todo.done) colors.accent else colors.textSecondary,
                                fontSize = typography.small,
                            )
                            Spacer(Modifier.width(spacing.sm))
                            Text(
                                text = todo.text,
                                color = colors.textPrimary,
                                fontSize = typography.small,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

@Composable
private fun PermissionRequestCard(
    title: String,
    isLoading: Boolean,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs)
            .clip(RoundedCornerShape(tokens.shapes.large))
            .background(colors.panelAlt)
            .border(tokens.borders.default, colors.warning, RoundedCornerShape(tokens.shapes.large))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = "权限请求",
            color = colors.warning,
            fontSize = typography.small,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = typography.small,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            PermissionActionButton(
                label = "拒绝",
                enabled = !isLoading,
                color = colors.danger,
                onClick = onDeny,
            )
            PermissionActionButton(
                label = "仅这次",
                enabled = !isLoading,
                color = colors.accent,
                onClick = onAllowOnce,
            )
            PermissionActionButton(
                label = "始终允许",
                enabled = !isLoading,
                color = colors.accent,
                onClick = onAllowAlways,
            )
        }
    }
}

@Composable
private fun PermissionActionButton(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shapes.medium))
            .background(color.copy(alpha = if (enabled) 0.2f else 0.1f))
            .border(tokens.borders.default, color, RoundedCornerShape(tokens.shapes.medium))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        Text(text = label, color = color, fontSize = typography.small)
    }
}

@Composable
private fun EmptyConversationState(
    projectName: String,
    projectPath: String,
    gitBranch: String?,
    gitStatusSummary: String?,
    modifier: Modifier = Modifier,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_opencode_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(54.dp),
            )
            Text(
                text = projectName,
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = projectPath,
                color = colors.textSecondary,
                fontSize = typography.base,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⎇", color = colors.textSecondary, fontSize = typography.base)
                Spacer(Modifier.width(8.dp))
                Text(
                    gitBranch?.ifBlank { "未知分支" } ?: "未知分支",
                    color = colors.textSecondary,
                    fontSize = typography.base,
                )
            }
            Text(
                text = gitStatusSummary?.ifBlank { "Git 状态未知" } ?: "Git 状态未知",
                color = colors.textSecondary,
                fontSize = typography.base,
            )
        }
    }
}

@Composable
private fun HistoryLoadingState(modifier: Modifier = Modifier) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AssistantTypingBubble()
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            text = "正在加载历史消息...",
            color = colors.textSecondary,
            fontSize = tokens.typography.small,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopCommandBar(
    uiState: OpenCodeUiState,
    onProjectSelected: (String) -> Unit,
    onProjectDeleteConfirmed: (String) -> Unit,
    onAddProjectClick: () -> Unit,
    onAddProjectDismiss: () -> Unit,
    onAddProjectDirectoryOpen: (String) -> Unit,
    onAddProjectDirectoryUp: () -> Unit,
    onAddProjectSearchQueryChange: (String) -> Unit,
    onAddProjectConfirm: () -> Unit,
    onMenuClick: () -> Unit,
    onServerConfigClick: () -> Unit,
    onCreateSessionClick: () -> Unit,
    onRetryDiscovery: () -> Unit,
    serverOptions: List<OpenCodeService>,
    onServerSelected: (OpenCodeService) -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val actionShape = RoundedCornerShape(tokens.shapes.medium)
    val actionBorderColor = tokens.palette.borderTertiary
    val actionBackgroundColor = tokens.palette.surface
    var showProjectSheet by remember { mutableStateOf(false) }
    var deleteConfirmProjectDirectory by remember { mutableStateOf<String?>(null) }
    var showServerSheet by remember { mutableStateOf(false) }
    var showGitSheet by remember { mutableStateOf(false) }
    val selectedProject = uiState.selectedProject
    val projectName = selectedProject?.name ?: "选择项目"
    val isConnected = uiState.service != null && !uiState.isConnecting && uiState.errorMessage == null
    val connectedColor = Color(0xFF1DB954)
    val disconnectedColor = Color(0xFFFF3B30)
    val statusDotColor = if (isConnected) connectedColor else disconnectedColor
    val knownServers = remember(serverOptions, uiState.service) {
        (serverOptions + listOfNotNull(uiState.service)).distinctBy { "${it.host}:${it.port}" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(actionShape)
                .border(tokens.borders.default, actionBorderColor, actionShape)
                .background(actionBackgroundColor)
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", color = colors.textPrimary, fontSize = typography.large)
        }

        Spacer(Modifier.width(spacing.sm))

        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(actionShape)
                    .border(tokens.borders.default, actionBorderColor, actionShape)
                    .background(actionBackgroundColor)
                    .clickable { showProjectSheet = true }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", color = colors.textSecondary, fontSize = typography.large)
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = projectName,
                    color = colors.textPrimary,
                    fontSize = typography.base,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(spacing.sm))
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(spacing.sm))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(actionShape)
                .border(tokens.borders.default, actionBorderColor, actionShape)
                .background(actionBackgroundColor)
                .clickable {
                    onServerConfigClick()
                    showServerSheet = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_server),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                    .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(statusDotColor),
                )
            }
        }

        Spacer(Modifier.width(spacing.sm))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(actionShape)
                .border(tokens.borders.default, actionBorderColor, actionShape)
                .background(actionBackgroundColor)
                .clickable { showGitSheet = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_git_branch),
                contentDescription = "Git 状态",
                tint = colors.textPrimary,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(spacing.sm))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(actionShape)
                .border(tokens.borders.default, actionBorderColor, actionShape)
                .background(actionBackgroundColor)
                .clickable(onClick = onCreateSessionClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (showProjectSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProjectSheet = false },
            containerColor = colors.panel,
            contentColor = colors.textPrimary,
        ) {
            Text(
                text = "选择项目",
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(horizontal = 12.dp),
            ) {
                items(uiState.projects) { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.shapes.large))
                            .combinedClickable(
                                onClick = {
                                    onProjectSelected(project.directory)
                                    showProjectSheet = false
                                },
                                onLongClick = {
                                    deleteConfirmProjectDirectory = project.directory
                                },
                            )
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                color = colors.textPrimary,
                                fontSize = typography.base,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = project.directory,
                                color = colors.textSecondary,
                                fontSize = typography.small,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (project.isLocalOnly) {
                                Text(
                                    text = "本地项目",
                                    color = colors.accent,
                                    fontSize = typography.small,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.line)
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.shapes.large))
                            .clickable {
                                showProjectSheet = false
                                onAddProjectClick()
                            }
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    ) {
                        Text(
                            text = "+ 添加项目",
                            color = colors.accent,
                            fontSize = typography.base,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    deleteConfirmProjectDirectory?.let { directory ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProjectDirectory = null },
            title = { Text("删除项目") },
            text = { Text("确认删除该项目吗？\n$directory") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onProjectDeleteConfirmed(directory)
                        deleteConfirmProjectDirectory = null
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProjectDirectory = null }) { Text("取消") }
            },
        )
    }

    if (showGitSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGitSheet = false },
            containerColor = colors.panel,
            contentColor = colors.textPrimary,
        ) {
            Text(
                text = "Git 状态",
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "项目: ${selectedProject?.name ?: "未选择"}",
                    color = colors.textPrimary,
                    fontSize = typography.base,
                )
                Text(
                    text = "分支: ${selectedProject?.gitBranch?.ifBlank { "未知分支" } ?: "未知分支"}",
                    color = colors.textPrimary,
                    fontSize = typography.base,
                )
                Text(
                    text = "状态: ${selectedProject?.gitStatusSummary?.ifBlank { "Git 状态未知" } ?: "Git 状态未知"}",
                    color = colors.textPrimary,
                    fontSize = typography.base,
                )
                Text(
                    text = "目录: ${selectedProject?.directory ?: "-"}",
                    color = colors.textSecondary,
                    fontSize = typography.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (uiState.isProjectPickerVisible) {
        ModalBottomSheet(
            onDismissRequest = onAddProjectDismiss,
            containerColor = colors.panel,
            contentColor = colors.textPrimary,
        ) {
            Text(
                text = "添加本地项目",
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            Text(
                text = "当前目录: ${uiState.projectPickerCurrentDirectory ?: "-"}",
                color = colors.textSecondary,
                fontSize = typography.small,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
            BasicTextField(
                value = uiState.projectPickerQuery,
                onValueChange = onAddProjectSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = typography.base,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .clip(RoundedCornerShape(tokens.shapes.medium))
                    .background(colors.panelAlt)
                    .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                decorationBox = { inner ->
                    if (uiState.projectPickerQuery.isBlank()) {
                        Text(
                            text = "搜索目录（query）",
                            color = colors.textSecondary,
                            fontSize = typography.small,
                        )
                    }
                    inner()
                },
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.shapes.medium))
                        .background(colors.panelAlt)
                        .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                        .clickable(onClick = onAddProjectDirectoryUp)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    Text("上级目录", color = colors.textPrimary, fontSize = typography.small)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.shapes.medium))
                        .background(colors.panelAlt)
                        .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                        .clickable(onClick = onAddProjectConfirm)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    Text("添加当前目录（本地）", color = colors.accent, fontSize = typography.small)
                }
            }
            uiState.projectPickerErrorMessage?.let { message ->
                Text(
                    text = message,
                    color = colors.danger,
                    fontSize = typography.small,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
            if (uiState.isProjectPickerLoading) {
                Text(
                    text = "目录加载中...",
                    color = colors.textSecondary,
                    fontSize = typography.small,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 12.dp),
                ) {
                    items(uiState.projectPickerDirectories) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.shapes.large))
                                .clickable { onAddProjectDirectoryOpen(entry.directory) }
                                .padding(horizontal = spacing.md, vertical = spacing.md),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    color = colors.textPrimary,
                                    fontSize = typography.base,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = entry.directory,
                                    color = colors.textSecondary,
                                    fontSize = typography.small,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = colors.line)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            containerColor = colors.panel,
            contentColor = colors.textPrimary,
        ) {
            Text(
                text = "服务器状态",
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            val currentService = uiState.service
            Text(
                text = "当前: ${currentService?.baseUrl ?: "未连接"}",
                color = colors.textPrimary,
                fontSize = typography.base,
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
            val connectedColor = Color(0xFF1DB954)
            val disconnectedColor = Color(0xFFFF3B30)
            val statusColor = if (isConnected) connectedColor else disconnectedColor
            Row(
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "连接正常" else "无法连接",
                    color = statusColor,
                    fontSize = typography.small,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.xs)
                    .clip(RoundedCornerShape(tokens.shapes.medium))
                    .background(colors.panelAlt)
                    .border(tokens.borders.default, tokens.palette.borderTertiary, RoundedCornerShape(tokens.shapes.medium))
                    .clickable {
                        onRetryDiscovery()
                        showServerSheet = false
                    }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                Text("重新发现服务", color = colors.textPrimary, fontSize = typography.small)
            }

            HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "可用服务器",
                color = colors.textSecondary,
                fontSize = typography.small,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(horizontal = 12.dp),
            ) {
                items(knownServers) { service ->
                    val isCurrent = uiState.service?.host == service.host && uiState.service.port == service.port
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.shapes.large))
                            .background(if (isCurrent) colors.panelAlt else Color.Transparent)
                            .clickable {
                                onServerSelected(service)
                                showServerSheet = false
                            }
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = service.serviceName,
                                color = colors.textPrimary,
                                fontSize = typography.base,
                                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = service.baseUrl,
                                color = colors.textSecondary,
                                fontSize = typography.small,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Text("当前", color = colors.accent, fontSize = typography.small, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServicePickerSheet(
    title: String,
    services: List<OpenCodeService>,
    selectedService: OpenCodeService?,
    onSelect: (OpenCodeService) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        contentColor = colors.textPrimary,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = typography.large,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(horizontal = 12.dp),
        ) {
            items(services.distinctBy { "${it.host}:${it.port}" }) { service ->
                val selected = selectedService?.host == service.host && selectedService.port == service.port
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.shapes.large))
                        .background(if (selected) colors.panelAlt else Color.Transparent)
                        .clickable { onSelect(service) }
                        .padding(horizontal = spacing.md, vertical = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = service.serviceName,
                            color = colors.textPrimary,
                            fontSize = typography.base,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        )
                        Text(
                            text = service.baseUrl,
                            color = colors.textSecondary,
                            fontSize = typography.small,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Text("当前", color = colors.accent, fontSize = typography.small)
                    }
                }
                HorizontalDivider(color = colors.line)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReferenceAssistantCard() {
    AssistantCard(
        content = "I've initialized the project structure. Here is the primary entry point for the API router.\n\n```ts\nexport const router = new Router();\n```",
        time = System.currentTimeMillis(),
        isError = false,
    )
}

@Composable
private fun AssistantCard(content: String, time: Long, isError: Boolean) {
    val colors = openCodeColors()
    val typography = opencodeTokens().typography
    val isTyping = content.isBlank() && !isError
    val useEnhancedCard = !isTyping && (isError || isEnhancedAssistantContent(content))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ChatAvatar(
            label = "AI",
            background = if (isError) colors.danger.copy(alpha = 0.15f) else colors.accent.copy(alpha = 0.14f),
            contentColor = if (isError) colors.danger else colors.accent,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.82f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "OPENCODE AI",
                color = colors.textSecondary,
                fontSize = typography.small,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatMessageTime(time),
                color = colors.textSecondary,
                fontSize = typography.small,
            )
            if (useEnhancedCard) {
                AssistantEnhancedCard(
                    content = content,
                    isError = isError,
                )
            } else if (isTyping) {
                AssistantTypingBubble()
            } else {
                AssistantMarkdownBubble(
                    content = content,
                    isError = isError,
                )
            }
        }
    }
}

@Composable
private fun AssistantTypingBubble() {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "typing_phase",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shapes.large))
            .background(colors.panel)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large))
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha = typingDotAlpha(phase, index)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.textSecondary.copy(alpha = alpha)),
            )
        }
    }
}

private fun typingDotAlpha(phase: Float, index: Int): Float {
    val shifted = (phase + index * 0.2f) % 1f
    val pulse = if (shifted < 0.5f) shifted * 2f else (1f - shifted) * 2f
    return 0.25f + pulse * 0.75f
}

@Composable
private fun ReferenceUserCard() {
    UserCard(
        content = "Yes, proceed with the authentication middleware using JWT. Use the existing secret from environment variables.",
        time = System.currentTimeMillis(),
    )
}

@Composable
private fun UserCard(content: String, time: Long) {
    val colors = openCodeColors()
    val isDarkTheme = isSystemInDarkTheme()
    val bubbleBackground = if (isDarkTheme) colors.panelAlt else colors.accent
    val bubbleTextColor = if (isDarkTheme) colors.textPrimary else colors.onAccent
    val copyRaw = rememberRawCopyAction()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatMessageTime(time),
                color = colors.textSecondary,
                fontSize = opencodeTokens().typography.small,
            )
            ChatBubble(
                text = content,
                textColor = bubbleTextColor,
                background = bubbleBackground,
                borderColor = bubbleBackground,
                modifier = Modifier.copyRawOnLongPress(content, copyRaw),
            )
        }
        Spacer(Modifier.width(8.dp))
        ChatAvatar(
            label = "我",
            background = colors.panelAlt,
            contentColor = colors.textSecondary,
        )
    }
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return "--:--"
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

@Composable
private fun ChatAvatar(
    label: String,
    background: Color,
    contentColor: Color,
) {
    val tokens = opencodeTokens()
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(tokens.borders.default, contentColor.copy(alpha = 0.22f), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = tokens.typography.small,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ChatBubble(
    text: String,
    textColor: Color,
    background: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = opencodeTokens()
    Text(
        text = text,
        color = textColor,
        fontSize = tokens.typography.base,
        lineHeight = tokens.typography.largeLineHeight,
        modifier = modifier
            .clip(RoundedCornerShape(tokens.shapes.large))
            .background(background)
            .border(tokens.borders.default, borderColor, RoundedCornerShape(tokens.shapes.large))
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    )
}

@Composable
private fun AssistantEnhancedCard(
    content: String,
    isError: Boolean,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val accentColor = if (isError) colors.danger else colors.accent
    val copyRaw = rememberRawCopyAction()
    Column(
        modifier = Modifier
            .copyRawOnLongPress(content, copyRaw)
            .clip(RoundedCornerShape(tokens.shapes.large))
            .background(colors.panel)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panelAlt)
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
        Text(
            text = if (isError) "运行错误" else "助手响应",
            color = colors.textSecondary,
            fontSize = typography.small,
            fontWeight = FontWeight.Medium,
        )
    }
        AssistantMarkdownContent(
            content = content,
            textColor = if (isError) colors.danger else colors.textPrimary,
            modifier = Modifier.padding(
                start = tokens.spacing.md,
                end = tokens.spacing.md,
                top = tokens.spacing.xs,
                bottom = tokens.spacing.sm,
            ),
        )
    }
}

@Composable
private fun AssistantMarkdownBubble(
    content: String,
    isError: Boolean,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val copyRaw = rememberRawCopyAction()
    Box(
        modifier = Modifier
            .copyRawOnLongPress(content, copyRaw)
            .clip(RoundedCornerShape(tokens.shapes.large))
            .background(colors.panel)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large))
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    ) {
        AssistantMarkdownContent(
            content = content,
            textColor = if (isError) colors.danger else colors.textPrimary,
        )
    }
}

@Composable
private fun rememberRawCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { raw ->
            clipboard.setText(AnnotatedString(raw))
            Toast.makeText(context, "已复制原文", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun Modifier.copyRawOnLongPress(
    raw: String,
    onCopy: (String) -> Unit,
): Modifier = pointerInput(raw) {
    detectTapGestures(onLongPress = { onCopy(raw) })
}

private data class MarkdownBlock(
    val isCode: Boolean,
    val language: String? = null,
    val content: String,
)

@Composable
private fun AssistantMarkdownContent(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseMarkdownBlocks(content).forEach { block ->
            if (block.isCode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.shapes.medium))
                        .background(colors.codePanel)
                        .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
                ) {
                    block.language?.takeIf { it.isNotBlank() }?.let { lang ->
                        Text(
                            text = lang,
                            color = colors.textSecondary,
                            fontSize = typography.small,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = block.content.trimEnd(),
                        color = textColor,
                        fontSize = typography.small,
                        lineHeight = typography.baseLineHeight,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                block.content.lines().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    if (line.isBlank()) {
                        Spacer(Modifier.height(2.dp))
                    } else {
                        val trimmed = line.trimStart()
                        when {
                            trimmed.startsWith("### ") -> {
                                Text(
                                    text = trimmed.removePrefix("### "),
                                    color = textColor,
                                    fontSize = (typography.large.value - 1).sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            trimmed.startsWith("## ") -> {
                                Text(
                                    text = trimmed.removePrefix("## "),
                                    color = textColor,
                                    fontSize = typography.large,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            trimmed.startsWith("# ") -> {
                                Text(
                                    text = trimmed.removePrefix("# "),
                                    color = textColor,
                                    fontSize = (typography.large.value + 1).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                val listLine = "• ${trimmed.drop(2)}"
                                val styled = messageFormatter(text = listLine, primary = false)
                                ClickableText(
                                    text = styled,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = typography.base,
                                        lineHeight = typography.largeLineHeight,
                                    ),
                                    onClick = { offset ->
                                        styled.getStringAnnotations(start = offset, end = offset)
                                            .firstOrNull()
                                            ?.takeIf { it.tag == SymbolAnnotationType.LINK.name }
                                            ?.let { uriHandler.openUri(it.item) }
                                    },
                                )
                            }
                            else -> {
                                val styled = messageFormatter(text = line, primary = false)
                                ClickableText(
                                    text = styled,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = typography.base,
                                        lineHeight = typography.largeLineHeight,
                                    ),
                                    onClick = { offset ->
                                        styled.getStringAnnotations(start = offset, end = offset)
                                            .firstOrNull()
                                            ?.takeIf { it.tag == SymbolAnnotationType.LINK.name }
                                            ?.let { uriHandler.openUri(it.item) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    if (!content.contains("```")) return listOf(MarkdownBlock(isCode = false, content = content))
    val parts = content.split("```")
    val blocks = mutableListOf<MarkdownBlock>()
    parts.forEachIndexed { index, part ->
        if (part.isEmpty()) return@forEachIndexed
        if (index % 2 == 0) {
            blocks += MarkdownBlock(isCode = false, content = part)
        } else {
            val firstLine = part.lineSequence().firstOrNull()?.trim().orEmpty()
            val looksLikeLanguage = firstLine.matches(Regex("^[A-Za-z0-9_+\\-#.]{1,24}$"))
            val code = if (looksLikeLanguage) part.substringAfter('\n', "") else part
            blocks += MarkdownBlock(
                isCode = true,
                language = if (looksLikeLanguage) firstLine else null,
                content = code,
            )
        }
    }
    return blocks.ifEmpty { listOf(MarkdownBlock(isCode = false, content = content)) }
}

private fun isEnhancedAssistantContent(content: String): Boolean {
    return content.contains("```") || content.contains("    ") || content.count { it == '\n' } >= 2
}

@Composable
private fun IndexingCard() {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(tokens.borders.default, colors.line)
            .background(colors.panel)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚙", color = colors.warning, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("INDEXING STATUS", color = colors.textSecondary, fontSize = tokens.typography.small)
            Text("84 files scanned", color = colors.textPrimary, fontSize = tokens.typography.large)
        }
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(8.dp)
                .background(colors.panelAlt, RoundedCornerShape(999.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(8.dp)
                    .background(colors.warning, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun ComposerBar(
    uiState: OpenCodeUiState,
    onModeSelected: (OpenCodeMode) -> Unit,
    onModelSelected: (OpenCodeModelOption) -> Unit,
    onThinkingSelected: (ThinkingLevel) -> Unit,
    onMessageSent: (String) -> Unit,
    onAbortSending: () -> Unit,
    onFileUploadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val composerContainerShape = RoundedCornerShape(tokens.shapes.large)
    val composerToolbarShape = RoundedCornerShape(tokens.shapes.medium)
    val composerBorderColor = tokens.palette.borderTertiary
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && uiState.selectedProject != null
    val slashCommands = listOf("/init")
    val matchedSlashCommands = slashCommands.filter { it.startsWith(text) || text == "/" }
    val showSlashMenu = text.startsWith("/") && matchedSlashCommands.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.sm)
            .testTag(OpenCodeTestTags.ComposerContainer)
            .clip(composerContainerShape)
            .border(tokens.borders.default, composerBorderColor, composerContainerShape)
            .background(tokens.palette.surface)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(composerToolbarShape)
                .border(tokens.borders.default, composerBorderColor, composerToolbarShape)
                .background(tokens.palette.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 2,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = typography.base, lineHeight = typography.baseLineHeight),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(end = 38.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (text.isBlank()) {
                            Text("随便问点什么...", color = colors.textSecondary, fontSize = typography.base)
                        }
                        inner()
                    }
                },
            )
            DropdownMenu(
                expanded = showSlashMenu,
                onDismissRequest = {},
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(tokens.palette.surface),
            ) {
                matchedSlashCommands.forEach { command ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = command,
                                    color = colors.textPrimary,
                                    fontSize = typography.base,
                                )
                            },
                            onClick = {
                                text = ""
                                onMessageSent(command)
                            },
                        )
                    }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(composerToolbarShape)
                    .background(
                        when {
                            uiState.isSending -> colors.danger
                            canSend -> colors.accent
                            else -> colors.accentDim
                        },
                    )
                    .clickable(enabled = uiState.isSending || canSend) {
                        if (uiState.isSending) {
                            onAbortSending()
                        } else {
                            val msg = text
                            text = ""
                            onMessageSent(msg)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (uiState.isSending) "■" else "➤",
                    color = colors.onAccent,
                    fontSize = typography.large,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(spacing.sm))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionChip(
                    label = uiState.selectedMode.label,
                    options = uiState.availableModes,
                    optionLabel = { it.label },
                    onSelected = onModeSelected,
                    useBottomSheet = true,
                    sheetTitle = "选择Agent",
                )
                Spacer(Modifier.width(spacing.sm))
                SelectionChip(
                    label = uiState.selectedModel.displayLabel,
                    options = uiState.models,
                    optionLabel = { it.displayLabel },
                    onSelected = onModelSelected,
                    groupLabel = { it.providerName ?: it.providerID ?: "Other" },
                    useBottomSheet = true,
                    sheetTitle = "选择模型",
                    modifier = Modifier.widthIn(max = 170.dp),
                )
            }
            Spacer(Modifier.width(spacing.sm))
            ThinkingSegment(
                selected = uiState.selectedThinkingLevel,
                onSelected = onThinkingSelected,
            )
        }
    }
}

@Composable
private fun ThinkingSegment(
    selected: ThinkingLevel,
    onSelected: (ThinkingLevel) -> Unit,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val current = if (selected == ThinkingLevel.DEFAULT) ThinkingLevel.MEDIUM else selected
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shapes.medium))
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
            .background(colors.panel),
    ) {
        listOf(
            ThinkingLevel.LOW to "L",
            ThinkingLevel.MEDIUM to "M",
            ThinkingLevel.HIGH to "H",
        ).forEach { (value, text) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.shapes.small))
                    .background(if (current == value) colors.accent else Color.Transparent)
                    .clickable { onSelected(value) }
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            ) {
                Text(
                    text = text,
                    color = if (current == value) colors.onAccent else colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = typography.small,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> SelectionChip(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    groupLabel: ((T) -> String)? = null,
    useBottomSheet: Boolean = false,
    sheetTitle: String = "选择",
    modifier: Modifier = Modifier,
) {
    val colors = openCodeColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    var expanded by remember { mutableStateOf(false) }
    val grouped = remember(options, groupLabel) {
        if (groupLabel == null) linkedMapOf("" to options) else options.groupBy(groupLabel)
    }

    Box {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(tokens.shapes.medium))
                .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                .background(tokens.palette.surfaceInteractive)
                .clickable { expanded = true }
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }

        if (!useBottomSheet) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                grouped.forEach { (group, groupItems) ->
                    if (group.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(group, color = colors.textSecondary, fontWeight = FontWeight.Medium) },
                            onClick = { },
                            enabled = false,
                        )
                    }
                    groupItems.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = {
                                onSelected(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }

    if (useBottomSheet && expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = colors.panel,
            contentColor = colors.textPrimary,
        ) {
            Text(
                text = sheetTitle,
                color = colors.textPrimary,
                fontSize = typography.large,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(horizontal = 12.dp),
            ) {
                grouped.forEach { (group, groupItems) ->
                    if (group.isNotBlank()) {
                        item(key = "group-$group") {
                            Text(
                                text = group,
                                color = colors.textSecondary,
                                fontSize = typography.small,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(groupItems) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.shapes.large))
                                .clickable {
                                    onSelected(option)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = optionLabel(option),
                                color = colors.textPrimary,
                                fontSize = typography.base,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    item {
                        HorizontalDivider(color = colors.line)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
