package com.github.go_kenka.opencode.conversation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.go_kenka.opencode.R
import com.github.go_kenka.opencode.opencode.model.OpenCodeService
import com.github.go_kenka.opencode.theme.opencodeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ConversationColors(
    val bg: Color,
    val chatPanelBg: Color,
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

object ConversationTestTags {
    const val DrawerSearch = "opencode_drawer_search"
}

@Composable
internal fun conversationColors(): ConversationColors {
    val tokens = opencodeTokens()
    val palette = tokens.palette
    return ConversationColors(
        bg = palette.background,
        chatPanelBg = palette.surfaceInteractive,
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
internal fun rememberRawCopyAction(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedRawText = stringResource(R.string.oc_copied_raw)
    return remember(clipboard, context, copiedRawText) {
        { raw ->
            clipboard.setText(AnnotatedString(raw))
            Toast.makeText(context, copiedRawText, Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun Modifier.copyRawOnLongPress(
    raw: String,
    onCopy: (String) -> Unit,
): Modifier = pointerInput(raw) {
    detectTapGestures(onLongPress = { onCopy(raw) })
}

internal fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return "--:--"
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

internal fun parseServerInput(raw: String): Pair<String, Int>? {
    val normalized = raw.trim().ifBlank { return null }
    val withScheme = if (normalized.startsWith("http://") || normalized.startsWith("https://")) normalized else "http://$normalized"
    val run = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    val host = run.host?.takeIf { it.isNotBlank() } ?: return null
    val port = if (run.port > 0) run.port else if (run.scheme == "https") 443 else 80
    return host to port
}

@Composable
internal fun ServerEditorDialog(
    initial: OpenCodeService,
    onDismiss: () -> Unit,
    onSave: (OpenCodeService, String?) -> Unit,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val spacing = tokens.spacing
    var serverUrl by remember(initial) { mutableStateOf(initial.baseUrl.takeIf { initial.host.isNotBlank() } ?: "") }
    var serverName by remember(initial) { mutableStateOf(initial.serviceName.takeIf { initial.serviceName.isNotBlank() } ?: "") }
    var username by remember(initial) { mutableStateOf(initial.username.orEmpty()) }
    var password by remember(initial) { mutableStateOf(initial.password.orEmpty()) }
    val originalKey = initial.takeIf { it.host.isNotBlank() }?.let { "${it.host}:${it.port}" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textPrimary,
        title = { Text(if (originalKey == null) stringResource(R.string.oc_add_server) else stringResource(R.string.oc_edit_server)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.sm)) {
                ServerEditorTextField(serverUrl, { serverUrl = it }, stringResource(R.string.oc_server_url), "http://localhost:4096")
                ServerEditorTextField(serverName, { serverName = it }, stringResource(R.string.oc_server_name_optional), "Localhost")
                ServerEditorTextField(username, { username = it }, stringResource(R.string.oc_username_optional), "opencode")
                ServerEditorTextField(
                    password,
                    { password = it },
                    stringResource(R.string.oc_password_optional),
                    stringResource(R.string.oc_enter_password),
                    PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                parseServerInput(serverUrl)?.let { (host, port) ->
                    val normalizedName = serverName.ifBlank { host }
                    onSave(
                        OpenCodeService(
                            serviceName = normalizedName,
                            host = host,
                            port = port,
                            username = username.ifBlank { null },
                            password = password.ifBlank { null },
                        ),
                        originalKey,
                    )
                }
            }) { Text(stringResource(R.string.oc_save), color = colors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.oc_cancel), color = colors.textSecondary) } },
    )
}

@Composable
private fun ServerEditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = conversationColors()
    val tokens = opencodeTokens()
    val typography = tokens.typography
    val spacing = tokens.spacing
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing.xs)) {
        Text(text = label, color = colors.textSecondary, fontSize = typography.small)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = typography.base),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.shapes.medium))
                .background(colors.panelAlt)
                .border(tokens.borders.default, colors.line, RoundedCornerShape(tokens.shapes.medium))
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text(text = placeholder, color = colors.textSecondary, fontSize = typography.base)
                }
                inner()
            },
        )
    }
}

@Composable
internal fun ChatAvatar(
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
        Text(text = label, color = contentColor, fontSize = tokens.typography.small, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
internal fun PermissionActionButton(
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
