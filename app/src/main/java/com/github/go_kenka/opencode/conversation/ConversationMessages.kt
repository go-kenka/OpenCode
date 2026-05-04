package com.github.go_kenka.opencode.conversation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.theme.OpenCodeTheme
import com.github.go_kenka.opencode.theme.opencodeTokens

@Composable
internal fun AssistantCard(content: String, time: Long, isError: Boolean, reasoningContent: String, reasoningCompleted: Boolean) {
    val colors = conversationColors()
    val typography = opencodeTokens().typography
    val isTyping = content.isBlank() && !isError
    val useEnhancedCard = !isTyping && (isError || isEnhancedAssistantContent(content))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChatAvatar("AI", if (isError) colors.danger.copy(alpha = 0.15f) else colors.accent.copy(alpha = 0.14f), if (isError) colors.danger else colors.accent)
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "OPENCODE AI", color = colors.textSecondary, fontSize = typography.small, fontWeight = FontWeight.Medium)
            Text(text = formatMessageTime(time), color = colors.textSecondary, fontSize = typography.small)
            if (useEnhancedCard) {
                ReasoningCard(reasoningContent, reasoningCompleted)
                if (reasoningContent.isNotBlank()) Spacer(Modifier.height(6.dp))
                AssistantEnhancedCard(content, isError)
            } else if (isTyping) {
                AssistantTypingBubble()
            } else {
                ReasoningCard(reasoningContent, reasoningCompleted)
                if (reasoningContent.isNotBlank()) Spacer(Modifier.height(6.dp))
                AssistantMarkdownBubble(content, isError)
            }
        }
    }
}

@Composable
internal fun UserCard(content: String, time: Long) {
    val colors = conversationColors()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val bubbleBackground = if (isDarkTheme) colors.panelAlt else colors.accent
    val bubbleTextColor = if (isDarkTheme) colors.textPrimary else colors.onAccent
    val copyRaw = rememberRawCopyAction()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.fillMaxWidth(0.82f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = formatMessageTime(time), color = colors.textSecondary, fontSize = opencodeTokens().typography.small)
            ChatBubble(content, bubbleTextColor, bubbleBackground, bubbleBackground, modifier = Modifier.copyRawOnLongPress(content, copyRaw))
        }
        Spacer(Modifier.size(8.dp))
        ChatAvatar(stringResource(R.string.author_me), colors.panelAlt, colors.textSecondary)
    }
}

@Composable
private fun ChatBubble(text: String, textColor: Color, background: Color, borderColor: Color, modifier: Modifier = Modifier) {
    val tokens = opencodeTokens()
    Text(
        text = text,
        color = textColor,
        fontSize = tokens.typography.base,
        lineHeight = tokens.typography.largeLineHeight,
        modifier = modifier.clip(RoundedCornerShape(tokens.shapes.large)).background(background)
            .border(tokens.borders.default, borderColor, RoundedCornerShape(tokens.shapes.large))
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    )
}

@Composable
private fun AssistantEnhancedCard(content: String, isError: Boolean) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val accentColor = if (isError) colors.danger else colors.accent
    val copyRaw = rememberRawCopyAction()
    Column(
        modifier = Modifier.copyRawOnLongPress(content, copyRaw).clip(RoundedCornerShape(tokens.shapes.large))
            .background(colors.panel).border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.panelAlt).padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accentColor))
            Spacer(Modifier.size(tokens.spacing.xs))
            Text(text = if (isError) stringResource(R.string.oc_runtime_error) else stringResource(R.string.oc_assistant_response), color = colors.textSecondary, fontSize = typography.small, fontWeight = FontWeight.Medium)
        }
        AssistantMarkdownContent(content = content, textColor = if (isError) colors.danger else colors.textPrimary, modifier = Modifier.padding(start = tokens.spacing.md, end = tokens.spacing.md, top = tokens.spacing.xs, bottom = tokens.spacing.sm))
    }
}

@Composable
private fun AssistantMarkdownBubble(content: String, isError: Boolean) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val copyRaw = rememberRawCopyAction()
    Column(
        modifier = Modifier.copyRawOnLongPress(content, copyRaw).clip(RoundedCornerShape(tokens.shapes.large)).background(colors.panel)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large)).padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    ) {
        AssistantMarkdownContent(content = content, textColor = if (isError) colors.danger else colors.textPrimary)
    }
}

@Composable
private fun AssistantTypingBubble(label: String = "") {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val infinite = rememberInfiniteTransition(label = "typing")
    val alpha by infinite.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    Row(
        modifier = Modifier.clip(RoundedCornerShape(tokens.shapes.large)).background(colors.panel)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.large)).padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = if (label.isBlank()) stringResource(R.string.oc_thinking_in_depth) else label, color = colors.textSecondary.copy(alpha = alpha), fontSize = tokens.typography.small, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReasoningCard(reasoningContent: String, reasoningCompleted: Boolean) {
    if (reasoningContent.isBlank()) return
    val colors = conversationColors()
    val tokens = opencodeTokens()
    var expanded by rememberSaveable(reasoningContent) { mutableStateOf(true) }
    LaunchedEffect(reasoningCompleted) {
        if (reasoningCompleted) expanded = false
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.medium)).background(colors.panelAlt)
            .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(id = R.drawable.ic_chevron_down), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp).rotate(if (expanded) 0f else -90f))
            Spacer(Modifier.size(tokens.spacing.xs))
            Text(text = stringResource(R.string.oc_deep_thinking), color = colors.textSecondary, fontSize = tokens.typography.small, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(tokens.spacing.xs))
            Text(text = if (reasoningCompleted) stringResource(R.string.oc_completed) else stringResource(R.string.oc_thinking), color = colors.textSecondary, fontSize = tokens.typography.small)
        }
        if (expanded) {
            HorizontalDivider(color = colors.line)
            AssistantMarkdownContent(content = reasoningContent, textColor = colors.textSecondary, modifier = Modifier.padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs))
        }
    }
}

private data class MarkdownBlock(val isCode: Boolean, val language: String? = null, val content: String)

@Composable
private fun AssistantMarkdownContent(content: String, textColor: Color, modifier: Modifier = Modifier) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseMarkdownBlocks(content).forEach { block ->
            if (block.isCode) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.shapes.medium)).background(colors.codePanel)
                        .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
                ) {
                    block.language?.takeIf { it.isNotBlank() }?.let { lang ->
                        Text(text = lang, color = colors.textSecondary, fontSize = typography.small, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(text = block.content.trimEnd(), color = textColor, fontSize = typography.small, lineHeight = typography.baseLineHeight, fontFamily = FontFamily.Monospace)
                }
            } else {
                block.content.lines().forEach { rawLine ->
                    val line = rawLine.trimEnd()
                    if (line.isBlank()) {
                        Spacer(Modifier.height(2.dp))
                    } else {
                        val trimmed = line.trimStart()
                        when {
                            trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), color = textColor, fontSize = (typography.large.value - 1).sp, fontWeight = FontWeight.SemiBold)
                            trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), color = textColor, fontSize = typography.large, fontWeight = FontWeight.SemiBold)
                            trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), color = textColor, fontSize = (typography.large.value + 1).sp, fontWeight = FontWeight.Bold)
                            else -> {
                                val output = if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) "• ${trimmed.drop(2)}" else line
                                val styled = messageFormatter(text = output, primary = false)
                                ClickableText(text = styled, style = TextStyle(color = textColor, fontSize = typography.base, lineHeight = typography.largeLineHeight), onClick = { offset ->
                                    styled.getStringAnnotations(start = offset, end = offset).firstOrNull()?.takeIf { it.tag == SymbolAnnotationType.LINK.name }?.let { uriHandler.openUri(it.item) }
                                })
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
            blocks += MarkdownBlock(isCode = true, language = if (looksLikeLanguage) firstLine else null, content = code)
        }
    }
    return blocks.ifEmpty { listOf(MarkdownBlock(isCode = false, content = content)) }
}

private fun isEnhancedAssistantContent(content: String): Boolean = content.contains("```") || content.contains("    ") || content.count { it == '\n' } >= 2

@Preview(showBackground = true)
@Composable
private fun AssistantCardPreview() {
    OpenCodeTheme {
        AssistantCard(
            content = "已完成。\n\n```kotlin\nfun hello() = \"world\"\n```",
            time = System.currentTimeMillis(),
            isError = false,
            reasoningContent = "先解析输入，再渲染输出。",
            reasoningCompleted = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserCardPreview() {
    OpenCodeTheme {
        UserCard(content = "帮我把这个页面拆分成组件", time = System.currentTimeMillis())
    }
}
