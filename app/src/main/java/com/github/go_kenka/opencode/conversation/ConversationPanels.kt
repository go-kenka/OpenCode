package com.github.go_kenka.opencode.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.opencode.model.OpenCodeTodoItem
import com.github.go_kenka.opencode.theme.opencodeTokens

@Composable
internal fun TodoStatusBar(todos: List<OpenCodeTodoItem>, onClick: () -> Unit) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val done = todos.count { it.done }
    val total = todos.size
    val latest = todos.lastOrNull()?.text ?: stringResource(R.string.oc_no_todo)
    val capsuleShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chatPanelBg)
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(capsuleShape)
                .background(colors.chatPanelBg)
                .border(tokens.borders.default, colors.line, capsuleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.xs)
                .widthIn(max = 320.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("TODO", color = colors.accent, fontSize = typography.small, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(spacing.sm))
            Text("($done/$total)", color = colors.textSecondary, fontSize = typography.small)
            Spacer(Modifier.width(spacing.sm))
            Text(text = latest, color = colors.textPrimary, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TodoDialog(todos: List<OpenCodeTodoItem>, onClose: () -> Unit) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.panel, contentColor = colors.textPrimary) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(R.string.oc_full_todo), color = colors.textPrimary, fontSize = typography.base, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = colors.line)
            if (todos.isEmpty()) {
                Text(stringResource(R.string.oc_no_todo), color = colors.textSecondary, fontSize = typography.small)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    items(todos) { todo ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(text = if (todo.done) "✓" else "•", color = if (todo.done) colors.accent else colors.textSecondary, fontSize = typography.small)
                            Spacer(Modifier.width(spacing.sm))
                            Text(text = todo.text, color = colors.textPrimary, fontSize = typography.small)
                        }
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

@Composable
internal fun PermissionRequestCard(
    title: String,
    isLoading: Boolean,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onDeny: () -> Unit,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xs)
            .clip(RoundedCornerShape(tokens.shapes.large)).background(colors.panelAlt)
            .border(tokens.borders.default, colors.warning, RoundedCornerShape(tokens.shapes.large)).padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(text = stringResource(R.string.oc_permission_request), color = colors.warning, fontSize = typography.small, fontWeight = FontWeight.Medium)
        Text(text = title, color = colors.textPrimary, fontSize = typography.small)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            PermissionActionButton(stringResource(R.string.oc_deny), !isLoading, colors.danger, onDeny)
            PermissionActionButton(stringResource(R.string.oc_allow_once), !isLoading, colors.accent, onAllowOnce)
            PermissionActionButton(stringResource(R.string.oc_allow_always), !isLoading, colors.accent, onAllowAlways)
        }
    }
}

@Composable
internal fun EmptyConversationState(
    projectName: String,
    projectPath: String,
    gitBranch: String?,
    gitStatusSummary: String?,
    modifier: Modifier = Modifier,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(painter = painterResource(id = R.drawable.ic_opencode_logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(54.dp))
            Text(text = projectName, color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium)
            Text(text = projectPath, color = colors.textSecondary, fontSize = typography.base, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⎇", color = colors.textSecondary, fontSize = typography.base)
                Spacer(Modifier.width(8.dp))
                Text(gitBranch?.ifBlank { stringResource(R.string.oc_unknown_branch) } ?: stringResource(R.string.oc_unknown_branch), color = colors.textSecondary, fontSize = typography.base)
            }
            Text(gitStatusSummary?.ifBlank { stringResource(R.string.oc_git_status_unknown) } ?: stringResource(R.string.oc_git_status_unknown), color = colors.textSecondary, fontSize = typography.base)
        }
    }
}

@Composable
internal fun HistoryLoadingState(modifier: Modifier = Modifier) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(text = stringResource(R.string.oc_loading_history), color = colors.textSecondary, fontSize = tokens.typography.small)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ServicePickerSheet(
    title: String,
    services: List<OpenCodeService>,
    selectedService: OpenCodeService?,
    onSelect: (OpenCodeService) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.panel, contentColor = colors.textPrimary) {
        Text(text = title, color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(horizontal = 12.dp)) {
            items(services.distinctBy { "${it.host}:${it.port}" }) { service ->
                val selected = selectedService?.host == service.host && selectedService.port == service.port
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).background(if (selected) colors.panelAlt else Color.Transparent)
                        .clickable { onSelect(service) }.padding(horizontal = spacing.md, vertical = spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = service.serviceName, color = colors.textPrimary, fontSize = typography.base, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                        Text(text = service.baseUrl, color = colors.textSecondary, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (selected) {
                        Text(stringResource(R.string.oc_current), color = colors.accent, fontSize = typography.small)
                    }
                }
                HorizontalDivider(color = colors.line)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
