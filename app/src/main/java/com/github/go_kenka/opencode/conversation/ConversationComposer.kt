package com.github.go_kenka.opencode.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.github.go_kenka.opencode.opencode.model.OpenCodeMode
import com.github.go_kenka.opencode.opencode.model.OpenCodeModelOption
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.opencode.model.ThinkingLevel
import com.github.go_kenka.opencode.theme.OpenCodeTheme
import com.github.go_kenka.opencode.theme.opencodeTokens

@Composable
internal fun ComposerBar(
    uiState: OpenCodeUiState,
    onModeSelected: (OpenCodeMode) -> Unit,
    onModelSelected: (OpenCodeModelOption) -> Unit,
    onThinkingSelected: (ThinkingLevel) -> Unit,
    onMessageSent: (String) -> Unit,
    onAbortSending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    val typography = tokens.typography
    val composerContainerShape = RoundedCornerShape(24.dp)
    val composerInputShape = RoundedCornerShape(20.dp)
    val composerBorderColor = tokens.palette.borderTertiary
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && uiState.selectedProject != null
    val slashCommands = listOf("/new", "/init")
    val matchedSlashCommands = slashCommands.filter { it.startsWith(text) || text == "/" }
    val showSlashMenu = text.startsWith("/") && matchedSlashCommands.isNotEmpty()

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.sm)
            .clip(composerContainerShape).border(tokens.borders.default, composerBorderColor, composerContainerShape)
            .background(tokens.palette.surface).padding(horizontal = spacing.sm, vertical = spacing.sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SelectionChip(
                    label = uiState.selectedMode.label,
                    options = uiState.availableModes,
                    optionLabel = { it.label },
                    onSelected = onModeSelected,
                    useBottomSheet = true,
                    sheetTitle = stringResource(R.string.oc_select_agent),
                )
                Spacer(Modifier.width(spacing.sm))
                SelectionChip(
                    label = uiState.selectedModel.displayLabel,
                    options = uiState.models,
                    optionLabel = { it.displayLabel },
                    onSelected = onModelSelected,
                    groupLabel = { it.providerName ?: it.providerID ?: "Other" },
                    useBottomSheet = true,
                    sheetTitle = stringResource(R.string.oc_select_model),
                    modifier = Modifier.widthIn(max = 170.dp),
                )
            }
            Spacer(Modifier.width(spacing.sm))
            ThinkingSegment(selected = uiState.selectedThinkingLevel, onSelected = onThinkingSelected)
        }

        Spacer(Modifier.height(spacing.sm))

        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(composerInputShape)
                .background(tokens.palette.surface).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                maxLines = 2,
                textStyle = TextStyle(color = colors.textPrimary, fontSize = typography.base, lineHeight = typography.baseLineHeight),
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(end = 38.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (text.isBlank()) {
                            Text(stringResource(R.string.oc_ask_anything), color = colors.textSecondary, fontSize = typography.base)
                        }
                        inner()
                    }
                },
            )
            DropdownMenu(expanded = showSlashMenu, onDismissRequest = {}, modifier = Modifier.align(Alignment.TopStart).background(tokens.palette.surface)) {
                matchedSlashCommands.forEach { command ->
                    DropdownMenuItem(
                        text = { Text(text = command, color = colors.textPrimary, fontSize = typography.base) },
                        onClick = {
                            text = ""
                            onMessageSent(command)
                        },
                    )
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).width(30.dp).height(30.dp).clip(RoundedCornerShape(50)).background(
                    when {
                        uiState.isSending -> colors.danger
                        canSend -> colors.accent
                        else -> colors.accentDim
                    },
                ).clickable(enabled = uiState.isSending || canSend) {
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
                Text(text = if (uiState.isSending) "■" else "➤", color = colors.onAccent, fontSize = typography.large, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ThinkingSegment(selected: ThinkingLevel, onSelected: (ThinkingLevel) -> Unit) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val current = if (selected == ThinkingLevel.DEFAULT) ThinkingLevel.MEDIUM else selected
    Row(modifier = Modifier.clip(RoundedCornerShape(tokens.shapes.medium)).border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium)).background(colors.panel)) {
        listOf(ThinkingLevel.LOW to "L", ThinkingLevel.MEDIUM to "M", ThinkingLevel.HIGH to "H").forEach { (value, text) ->
            Box(
                modifier = Modifier.clip(RoundedCornerShape(tokens.shapes.small)).background(if (current == value) colors.accent else Color.Transparent)
                    .clickable { onSelected(value) }.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            ) {
                Text(text = text, color = if (current == value) colors.onAccent else colors.textSecondary, fontWeight = FontWeight.Medium, fontSize = typography.small)
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
    sheetTitle: String = "",
    modifier: Modifier = Modifier,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    var expanded by remember { mutableStateOf(false) }
    val grouped = remember(options, groupLabel) {
        if (groupLabel == null) linkedMapOf("" to options) else options.groupBy(groupLabel)
    }

    Box {
        Row(
            modifier = modifier.clip(RoundedCornerShape(tokens.shapes.medium)).border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                .background(tokens.palette.surfaceInteractive).clickable { expanded = true }
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(tokens.spacing.xs))
            Icon(painter = painterResource(id = R.drawable.ic_chevron_down), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.height(14.dp))
        }

        if (!useBottomSheet) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                grouped.forEach { (group, groupItems) ->
                    if (group.isNotBlank()) {
                        DropdownMenuItem(text = { Text(group, color = colors.textSecondary, fontWeight = FontWeight.Medium) }, onClick = {}, enabled = false)
                    }
                    groupItems.forEach { option ->
                        DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false })
                    }
                }
            }
        }
    }

    if (useBottomSheet && expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, containerColor = colors.panel, contentColor = colors.textPrimary) {
            Text(text = sheetTitle.ifBlank { stringResource(R.string.oc_select) }, color = colors.textPrimary, fontSize = typography.large, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(horizontal = 12.dp)) {
                grouped.forEach { (group, groupItems) ->
                    if (group.isNotBlank()) {
                        item(key = "group-$group") {
                            Text(text = group, color = colors.textSecondary, fontSize = typography.small, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    items(groupItems) { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.large)).clickable { onSelected(option); expanded = false }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            Text(text = optionLabel(option), color = colors.textPrimary, fontSize = typography.base, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item { HorizontalDivider(color = colors.line) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposerBarPreview() {
    OpenCodeTheme {
        ComposerBar(
            uiState = OpenCodeUiState(
                selectedProject = OpenCodeProject(id = "1", name = "OpenCode", directory = "/Users/wu/AndroidStudioProjects/OpenCode"),
            ),
            onModeSelected = {},
            onModelSelected = {},
            onThinkingSelected = {},
            onMessageSent = {},
            onAbortSending = {},
        )
    }
}
