package com.github.go_kenka.opencode.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.theme.OpenCodeTheme
import com.github.go_kenka.opencode.theme.opencodeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TopCommandBar(
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
    onRetryDiscovery: () -> Unit,
    serverOptions: List<OpenCodeService>,
    onServerSelected: (OpenCodeService) -> Unit,
    onManualServerSave: (OpenCodeService, String?) -> Unit,
    onManualServerDelete: (OpenCodeService) -> Unit,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val actionShape = RoundedCornerShape(tokens.shapes.medium)
    val menuButtonShape = RoundedCornerShape(50)
    val projectSearchShape = RoundedCornerShape(50)
    val serverButtonShape = RoundedCornerShape(50)
    val actionBorderColor = tokens.palette.borderTertiary
    val actionBackgroundColor = tokens.palette.surface
    var showProjectSheet by remember { mutableStateOf(false) }
    var deleteConfirmProjectDirectory by remember { mutableStateOf<String?>(null) }
    var showServerSheet by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<OpenCodeService?>(null) }
    var deleteServer by remember { mutableStateOf<OpenCodeService?>(null) }
    val selectedProject = uiState.selectedProject
    val projectName = selectedProject?.name ?: stringResource(R.string.oc_select_project)
    val isConnected = uiState.service != null && !uiState.isConnecting && uiState.errorMessage == null
    val connectedColor = Color(0xFF1DB954)
    val disconnectedColor = Color(0xFFFF3B30)
    val statusDotColor = if (isConnected) connectedColor else disconnectedColor
    val knownServers = remember(serverOptions, uiState.service) { (serverOptions + listOfNotNull(uiState.service)).distinctBy { "${it.host}:${it.port}" } }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(menuButtonShape).border(tokens.borders.default, actionBorderColor, menuButtonShape)
                .background(actionBackgroundColor).clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", color = colors.textPrimary, fontSize = typography.large)
        }

        Spacer(Modifier.size(spacing.sm))

        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(projectSearchShape).border(tokens.borders.default, actionBorderColor, projectSearchShape)
                    .background(actionBackgroundColor).clickable { showProjectSheet = true }.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕", color = colors.textSecondary, fontSize = typography.large)
                Spacer(Modifier.size(spacing.sm))
                Text(text = projectName, color = colors.textPrimary, fontSize = typography.base, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(spacing.sm))
                Icon(painter = painterResource(id = R.drawable.ic_chevron_down), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.size(spacing.sm))
        Box(
            modifier = Modifier.size(40.dp).clip(serverButtonShape).border(tokens.borders.default, actionBorderColor, serverButtonShape)
                .background(actionBackgroundColor).clickable { onServerConfigClick(); showServerSheet = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.ic_settings), contentDescription = stringResource(R.string.oc_server_settings), tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.align(Alignment.TopEnd).size(6.dp).clip(RoundedCornerShape(50)).background(statusDotColor))
            }
        }
    }

    if (showProjectSheet) {
        ModalBottomSheet(onDismissRequest = { showProjectSheet = false }, containerColor = colors.panel, contentColor = colors.textPrimary) {
            Text(text = stringResource(R.string.oc_select_project), color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(horizontal = 12.dp)) {
                items(uiState.projects) { project ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).combinedClickable(
                            onClick = { onProjectSelected(project.directory); showProjectSheet = false },
                            onLongClick = { deleteConfirmProjectDirectory = project.directory },
                        ).padding(horizontal = spacing.md, vertical = spacing.md),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, color = colors.textPrimary, fontSize = typography.base, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(project.directory, color = colors.textSecondary, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (project.isLocalOnly) Text(stringResource(R.string.oc_local_project), color = colors.accent, fontSize = typography.small)
                        }
                    }
                    HorizontalDivider(color = colors.line)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).clickable { showProjectSheet = false; onAddProjectClick() }.padding(horizontal = spacing.md, vertical = spacing.md)) {
                        Text(stringResource(R.string.oc_add_project), color = colors.accent, fontSize = typography.base, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    deleteConfirmProjectDirectory?.let { directory ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProjectDirectory = null },
            title = { Text(stringResource(R.string.oc_delete_project)) },
            text = { Text(stringResource(R.string.oc_confirm_delete_project, directory)) },
            confirmButton = { TextButton(onClick = { onProjectDeleteConfirmed(directory); deleteConfirmProjectDirectory = null }) { Text(stringResource(R.string.oc_confirm)) } },
            dismissButton = { TextButton(onClick = { deleteConfirmProjectDirectory = null }) { Text(stringResource(R.string.oc_cancel)) } },
        )
    }

    if (uiState.isProjectPickerVisible) {
        ModalBottomSheet(onDismissRequest = onAddProjectDismiss, containerColor = colors.panel, contentColor = colors.textPrimary) {
            Text(stringResource(R.string.oc_add_local_project), color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md))
            Text(stringResource(R.string.oc_current_directory, uiState.projectPickerCurrentDirectory ?: "-"), color = colors.textSecondary, fontSize = typography.small, modifier = Modifier.padding(horizontal = spacing.lg))
            BasicTextField(
                value = uiState.projectPickerQuery,
                onValueChange = onAddProjectSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = typography.base),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm).clip(RoundedCornerShape(tokens.shapes.medium))
                    .background(colors.panelAlt).border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium)).padding(horizontal = spacing.md, vertical = spacing.sm),
                decorationBox = { inner ->
                    if (uiState.projectPickerQuery.isBlank()) Text(stringResource(R.string.oc_search_directory), color = colors.textSecondary, fontSize = typography.small)
                    inner()
                },
            )
            Row(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(modifier = Modifier.clip(actionShape).background(colors.panelAlt).border(tokens.borders.default, colors.line, actionShape).clickable(onClick = onAddProjectDirectoryUp).padding(horizontal = spacing.md, vertical = spacing.sm)) {
                    Text(stringResource(R.string.oc_parent_directory), color = colors.textPrimary, fontSize = typography.small)
                }
                Row(modifier = Modifier.clip(actionShape).background(colors.panelAlt).border(tokens.borders.default, colors.line, actionShape).clickable(onClick = onAddProjectConfirm).padding(horizontal = spacing.md, vertical = spacing.sm)) {
                    Text(stringResource(R.string.oc_add_current_directory), color = colors.accent, fontSize = typography.small)
                }
            }
            uiState.projectPickerErrorMessage?.let { message ->
                Text(text = message, color = colors.danger, fontSize = typography.small, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs))
            }
            if (uiState.isProjectPickerLoading) {
                Text(stringResource(R.string.oc_loading_directories), color = colors.textSecondary, fontSize = typography.small, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(horizontal = 12.dp)) {
                    items(uiState.projectPickerDirectories) { entry ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).clickable { onAddProjectDirectoryOpen(entry.directory) }.padding(horizontal = spacing.md, vertical = spacing.md)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, color = colors.textPrimary, fontSize = typography.base, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.directory, color = colors.textSecondary, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        ModalBottomSheet(onDismissRequest = { showServerSheet = false }, containerColor = colors.panel, contentColor = colors.textPrimary) {
            Text(stringResource(R.string.oc_server_status), color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md))
            val currentService = uiState.service
            Text(stringResource(R.string.oc_current_server, currentService?.baseUrl ?: stringResource(R.string.oc_not_connected)), color = colors.textPrimary, fontSize = typography.base, modifier = Modifier.padding(horizontal = spacing.lg))
            uiState.healthVersion?.let { version ->
                Text(stringResource(R.string.oc_version, version), color = colors.textSecondary, fontSize = typography.small, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs))
            }
            val statusColor = if (isConnected) connectedColor else disconnectedColor
            Row(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(statusColor))
                Spacer(Modifier.size(6.dp))
                Text(text = if (isConnected) stringResource(R.string.oc_connected) else stringResource(R.string.oc_disconnected), color = statusColor, fontSize = typography.small, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs).clip(actionShape).background(colors.panelAlt).border(tokens.borders.default, tokens.palette.borderTertiary, actionShape).clickable { onRetryDiscovery() }.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                Text(stringResource(R.string.oc_rediscover_service), color = colors.textPrimary, fontSize = typography.small)
            }
            Text(text = uiState.discoveryStatus, color = colors.textSecondary, fontSize = typography.small, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs))

            HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 10.dp))
            Text(stringResource(R.string.oc_available_servers), color = colors.textSecondary, fontSize = typography.small, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs))
            Row(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs).clip(actionShape).background(colors.panelAlt).border(tokens.borders.default, tokens.palette.borderTertiary, actionShape).clickable { editingService = OpenCodeService(serviceName = "", host = "", port = 4096) }.padding(horizontal = spacing.md, vertical = spacing.sm)) {
                Text(stringResource(R.string.oc_add_server), color = colors.accent, fontSize = typography.small)
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(horizontal = 12.dp)) {
                items(knownServers) { service ->
                    val isCurrent = uiState.service?.host == service.host && uiState.service.port == service.port
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).background(if (isCurrent) colors.panelAlt else Color.Transparent)
                            .clickable { onServerSelected(service); showServerSheet = false }.padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service.serviceName, color = colors.textPrimary, fontSize = typography.base, fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(service.baseUrl, color = colors.textSecondary, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!service.username.isNullOrBlank()) Text(stringResource(R.string.oc_user_label, service.username.orEmpty()), color = colors.textSecondary, fontSize = typography.small)
                        }
                        Text(stringResource(R.string.oc_edit), color = colors.accent, fontSize = typography.small, modifier = Modifier.padding(end = spacing.sm).clickable { editingService = service })
                        Text(stringResource(R.string.oc_delete), color = colors.danger, fontSize = typography.small, modifier = Modifier.clickable { deleteServer = service })
                        if (isCurrent) Text(stringResource(R.string.oc_current), color = colors.accent, fontSize = typography.small, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = colors.line)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    deleteServer?.let { service ->
        AlertDialog(
            onDismissRequest = { deleteServer = null },
            title = { Text(stringResource(R.string.oc_delete_server)) },
            text = { Text(stringResource(R.string.oc_confirm_delete_server, service.baseUrl)) },
            confirmButton = { TextButton(onClick = { onManualServerDelete(service); deleteServer = null }) { Text(stringResource(R.string.oc_confirm)) } },
            dismissButton = { TextButton(onClick = { deleteServer = null }) { Text(stringResource(R.string.oc_cancel)) } },
        )
    }

    editingService?.let { service ->
        ServerEditorDialog(
            initial = service,
            onDismiss = { editingService = null },
            onSave = { updated, originalKey -> onManualServerSave(updated, originalKey); editingService = null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TopCommandBarPreview() {
    OpenCodeTheme {
        TopCommandBar(
            uiState = OpenCodeUiState(),
            onProjectSelected = {},
            onProjectDeleteConfirmed = {},
            onAddProjectClick = {},
            onAddProjectDismiss = {},
            onAddProjectDirectoryOpen = {},
            onAddProjectDirectoryUp = {},
            onAddProjectSearchQueryChange = {},
            onAddProjectConfirm = {},
            onMenuClick = {},
            onServerConfigClick = {},
            onRetryDiscovery = {},
            serverOptions = emptyList(),
            onServerSelected = {},
            onManualServerSave = { _, _ -> },
            onManualServerDelete = {},
        )
    }
}
