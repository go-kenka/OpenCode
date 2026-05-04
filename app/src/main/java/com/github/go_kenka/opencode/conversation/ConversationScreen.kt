package com.github.go_kenka.opencode.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import com.github.go_kenka.opencode.theme.OpenCodeTheme

@Composable
fun ConversationScreen(
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
    onManualServerSave: (OpenCodeService, String?) -> Unit = { _, _ -> },
    onManualServerDelete: (OpenCodeService) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onServerConfigClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = conversationColors()
    var showTodoDialog by rememberSaveable { mutableStateOf(false) }
    val messageListState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        val lastIndex = uiState.messages.lastIndex
        if (lastIndex >= 0) {
            messageListState.scrollToItem(lastIndex)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = colors.bg) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                onRetryDiscovery = onRetryDiscovery,
                serverOptions = serverOptions,
                onServerSelected = onServerSelected,
                onManualServerSave = onManualServerSave,
                onManualServerDelete = onManualServerDelete,
            )
            HorizontalDivider(color = colors.line)
            TodoStatusBar(todos = uiState.todos, onClick = { showTodoDialog = true })
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(colors.chatPanelBg)) {
                if (uiState.isHistoryLoading && uiState.messages.isEmpty()) {
                    HistoryLoadingState(modifier = Modifier.fillMaxSize())
                } else if (uiState.messages.isEmpty()) {
                    EmptyConversationState(
                        projectName = uiState.selectedProject?.name ?: stringResource(R.string.oc_build_anything),
                        projectPath = uiState.selectedProject?.directory ?: "/",
                        gitBranch = uiState.selectedProject?.gitBranch,
                        gitStatusSummary = uiState.selectedProject?.gitStatusSummary,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        state = messageListState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(uiState.messages) { msg ->
                            if (msg.role == "user") {
                                UserCard(msg.content, msg.time)
                            } else {
                                AssistantCard(
                                    content = msg.content,
                                    time = msg.time,
                                    isError = msg.isError,
                                    reasoningContent = msg.reasoningContent,
                                    reasoningCompleted = msg.reasoningCompleted,
                                )
                            }
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
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        }

        if (showServicePicker && discoveredServices.size > 1) {
            ServicePickerSheet(
                title = stringResource(R.string.oc_found_multiple_servers),
                services = discoveredServices,
                selectedService = uiState.service,
                onSelect = onDiscoveredServiceSelected,
                onDismiss = onDiscoveredServicePickerDismiss,
            )
        }
        if (showTodoDialog) {
            TodoDialog(todos = uiState.todos, onClose = { showTodoDialog = false })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationScreenPreview() {
    OpenCodeTheme {
        ConversationScreen(
            uiState = OpenCodeUiState(),
            onProjectSelected = {},
            onProjectDeleteConfirmed = {},
            onAddProjectClick = {},
            onAddProjectDismiss = {},
            onAddProjectDirectoryOpen = {},
            onAddProjectDirectoryUp = {},
            onAddProjectSearchQueryChange = {},
            onAddProjectConfirm = {},
            onModeSelected = {},
            onModelSelected = {},
            onThinkingSelected = {},
            onMessageSent = {},
            onAbortSending = {},
            onPermissionAllowOnce = {},
            onPermissionAllowAlways = {},
            onPermissionDeny = {},
        )
    }
}
