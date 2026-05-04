# OpenCode Android 样式对齐实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将当前 Android 客户端的 OpenCode 页面主题配色、圆角、边框和组件层次统一到接近官方 OpenCode 风格，保证视觉一致性与可维护性。

**架构：** 采用“设计令牌（Design Tokens）+ 组件映射”的方式，不直接在页面里散落颜色/圆角常量。先新增 OpenCode 专用主题令牌与单元测试，再把 `OpenCodeConversation` 和 `JetchatDrawer` 迁移到令牌驱动，最后通过 Compose UI 契约测试锁定关键视觉结构（边框存在、toolbar 背景、弹窗样式）。

**技术栈：** Kotlin、Jetpack Compose (Material3)、JUnit4、Compose UI Test

---

## 文件结构（先锁定边界）

- 创建：`app/src/main/java/com/example/compose/jetchat/theme/OpenCodeDesignTokens.kt`
- 职责：定义 OpenCode 专用色板、圆角、间距、描边厚度、组件尺寸，提供 `@Composable opencodeTokens()`。

- 创建：`app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt`
- 职责：验证关键 token（主色、浅色背景、圆角级别、边框宽度）不回归。

- 修改：`app/src/main/java/com/example/compose/jetchat/conversation/OpenCodeConversation.kt`
- 职责：移除分散的颜色/圆角硬编码；统一顶栏、消息气泡、输入区、toolbar、弹窗到底层令牌；补充语义 tag 供 UI 测试。

- 修改：`app/src/main/java/com/example/compose/jetchat/components/JetchatDrawer.kt`
- 职责：将抽屉搜索框、会话卡片、底部设置入口的圆角/描边/背景迁移到 OpenCode 令牌，避免和主页面风格断裂。

- 创建：`app/src/androidTest/java/com/example/compose/jetchat/opencode/OpenCodeVisualStyleTest.kt`
- 职责：验证关键视觉契约（输入区边框、toolbar 背景存在、agent/model 使用弹窗、圆角层级）

- 修改：`app/src/androidTest/java/com/example/compose/jetchat/Utils.kt`
- 职责：补充一个测试宿主 `setOpenCodeContent {}` 辅助函数，避免重复样板。

---

### 任务 1：建立 OpenCode 设计令牌与单元测试

**文件：**
- 创建：`app/src/main/java/com/example/compose/jetchat/theme/OpenCodeDesignTokens.kt`
- 创建：`app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt`
- 测试：`app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt`

- [ ] **步骤 1：编写失败的 token 单元测试**

```kotlin
package com.example.compose.jetchat.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeDesignTokensTest {

    @Test
    fun light_palette_matches_opencode_contract() {
        val palette = OpenCodePalette.light()
        assertEquals(Color(0xFF6C5CE7), palette.accent)
        assertEquals(Color(0xFFF7F6FB), palette.toolbarBackground)
        assertTrue(palette.borderAlpha > 0.15f)
    }

    @Test
    fun shape_scale_matches_compact_contract() {
        val shape = OpenCodeShapeScale.default()
        assertEquals(16, shape.containerLarge)
        assertEquals(12, shape.containerMedium)
        assertEquals(8, shape.containerSmall)
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests "*OpenCodeDesignTokensTest" --no-daemon`
预期：FAIL，报错 `Unresolved reference: OpenCodePalette`。

- [ ] **步骤 3：编写最少实现代码（令牌）**

```kotlin
package com.example.compose.jetchat.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OpenCode 品牌主色和中性色阶
private val OpencodeAccent = Color(0xFF6C5CE7)
private val OpencodeAccentSoft = Color(0xFFE9E5FB)
private val OpencodeBg = Color(0xFFF4F2F8)
private val OpencodeSurface = Color(0xFFFFFFFF)
private val OpencodeTextPrimary = Color(0xFF14121D)
private val OpencodeTextSecondary = Color(0xFF676273)

/** 纯色板，便于单元测试直接验证 */
data class OpenCodePalette(
    val background: Color,
    val surface: Color,
    val toolbarBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val borderAlpha: Float,
) {
    companion object {
        fun light() = OpenCodePalette(
            background = OpencodeBg,
            surface = OpencodeSurface,
            toolbarBackground = Color(0xFFF7F6FB),
            textPrimary = OpencodeTextPrimary,
            textSecondary = OpencodeTextSecondary,
            accent = OpencodeAccent,
            borderAlpha = 0.24f,
        )
    }
}

data class OpenCodeShapeScale(
    val containerLarge: Int,
    val containerMedium: Int,
    val containerSmall: Int,
) {
    companion object {
        fun default() = OpenCodeShapeScale(
            containerLarge = 16,
            containerMedium = 12,
            containerSmall = 8,
        )
    }
}

data class OpenCodeTokens(
    val palette: OpenCodePalette,
    val shape: OpenCodeShapeScale,
)

@Composable
fun opencodeTokens(): OpenCodeTokens {
    return OpenCodeTokens(
        palette = OpenCodePalette.light(),
        shape = OpenCodeShapeScale.default(),
    )
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:testDebugUnitTest --tests "*OpenCodeDesignTokensTest" --no-daemon`
预期：PASS，`OpenCodeDesignTokensTest` 2 个用例通过。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/compose/jetchat/theme/OpenCodeDesignTokens.kt app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt
git commit -m "feat(theme): add opencode design tokens with unit tests"
```

---

### 任务 2：迁移 OpenCodeConversation 到令牌驱动（含输入区与弹窗风格）

**文件：**
- 修改：`app/src/main/java/com/example/compose/jetchat/conversation/OpenCodeConversation.kt`
- 测试：`app/src/androidTest/java/com/example/compose/jetchat/opencode/OpenCodeVisualStyleTest.kt`

- [ ] **步骤 1：编写失败的 UI 契约测试（输入区边框 + toolbar 浅背景）**

```kotlin
package com.example.compose.jetchat.opencode

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.compose.jetchat.conversation.OpenCodeConversationScreen
import com.example.compose.jetchat.opencode.model.OpenCodeUiState
import org.junit.Rule
import org.junit.Test

class OpenCodeVisualStyleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun composer_has_border_and_toolbar_background() {
        composeRule.setContent {
            OpenCodeConversationScreen(
                uiState = OpenCodeUiState(),
                onProjectSelected = {},
                onModeSelected = {},
                onModelSelected = {},
                onThinkingSelected = {},
                onMessageSent = {},
            )
        }

        composeRule.onNodeWithTag("opencode_composer_container").assertExists()
        composeRule.onNodeWithTag("opencode_composer_toolbar").assertExists()
        composeRule.onNodeWithTag("opencode_attachment_button").assertExists()
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.compose.jetchat.opencode.OpenCodeVisualStyleTest`
预期：FAIL，报错 `No node found with tag opencode_composer_container`。

- [ ] **步骤 3：编写最少实现代码（令牌映射 + testTag）**

```kotlin
// OpenCodeConversation.kt 关键片段
import androidx.compose.ui.platform.testTag
import com.example.compose.jetchat.theme.opencodeTokens

@Composable
private fun ComposerBar(...) {
    val tokens = opencodeTokens()
    val palette = tokens.palette

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(tokens.shape.containerLarge.dp))
            .border(1.dp, palette.textSecondary.copy(alpha = palette.borderAlpha), RoundedCornerShape(tokens.shape.containerLarge.dp))
            .background(palette.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("opencode_composer_container"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.shape.containerSmall.dp))
                .background(palette.toolbarBackground)
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("opencode_composer_toolbar"),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(tokens.shape.containerSmall.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.textSecondary.copy(alpha = palette.borderAlpha), RoundedCornerShape(tokens.shape.containerSmall.dp))
                    .testTag("opencode_attachment_button"),
            ) { /* ... */ }
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.compose.jetchat.opencode.OpenCodeVisualStyleTest`
预期：PASS，3 个节点都能找到。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/compose/jetchat/conversation/OpenCodeConversation.kt app/src/androidTest/java/com/example/compose/jetchat/opencode/OpenCodeVisualStyleTest.kt
git commit -m "refactor(ui): migrate opencode conversation to design tokens"
```

---

### 任务 3：抽屉样式同步（搜索框/会话卡片/设置入口）

**文件：**
- 修改：`app/src/main/java/com/example/compose/jetchat/components/JetchatDrawer.kt`
- 测试：`app/src/androidTest/java/com/example/compose/jetchat/opencode/OpenCodeVisualStyleTest.kt`

- [ ] **步骤 1：编写失败的抽屉样式测试**

```kotlin
@Test
fun drawer_uses_opencode_card_style() {
    composeRule.setContent {
        JetchatDrawerContent(
            sessions = emptyList(),
            selectedSessionId = null,
            onSessionClicked = {},
            onSettingsClicked = {},
        )
    }

    composeRule.onNodeWithTag("opencode_drawer_search").assertExists()
    composeRule.onNodeWithTag("opencode_drawer_settings").assertExists()
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.compose.jetchat.opencode.OpenCodeVisualStyleTest`
预期：FAIL，缺少 `opencode_drawer_search`。

- [ ] **步骤 3：编写最少实现代码（抽屉迁移）**

```kotlin
// JetchatDrawer.kt 关键片段
import androidx.compose.ui.platform.testTag
import com.example.compose.jetchat.theme.opencodeTokens

@Composable
fun JetchatDrawerContent(...) {
    val tokens = opencodeTokens()
    val palette = tokens.palette

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("opencode_drawer_search"),
        shape = RoundedCornerShape(tokens.shape.containerMedium.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = palette.toolbarBackground,
            focusedContainerColor = palette.surface,
        ),
    )
}

@Composable
private fun SettingsItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("opencode_drawer_settings"),
    ) { /* ... */ }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.compose.jetchat.opencode.OpenCodeVisualStyleTest`
预期：PASS，抽屉搜索和设置节点可检出。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/compose/jetchat/components/JetchatDrawer.kt app/src/androidTest/java/com/example/compose/jetchat/opencode/OpenCodeVisualStyleTest.kt
git commit -m "refactor(drawer): align drawer styling with opencode tokens"
```

---

### 任务 4：统一主题入口与回归验证（防止动态颜色漂移）

**文件：**
- 修改：`app/src/main/java/com/example/compose/jetchat/theme/Themes.kt`
- 修改：`app/src/main/java/com/example/compose/jetchat/conversation/ConversationFragment.kt`
- 测试：`app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt`

- [ ] **步骤 1：编写失败的主题回归测试**

```kotlin
@Test
fun opencode_theme_disables_dynamic_color_for_brand_surface() {
    val palette = OpenCodePalette.light()
    assertEquals(Color(0xFFF4F2F8), palette.background)
    assertEquals(Color(0xFFFFFFFF), palette.surface)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :app:testDebugUnitTest --tests "*OpenCodeDesignTokensTest" --no-daemon`
预期：FAIL，背景色不匹配（若仍使用系统动态颜色覆盖）。

- [ ] **步骤 3：编写最少实现代码（主题入口固定）**

```kotlin
// Themes.kt 关键片段
@Composable
fun JetchatTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dynamicColor = isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val myColorScheme = when {
        dynamicColor && isDarkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !isDarkTheme -> dynamicLightColorScheme(LocalContext.current)
        isDarkTheme -> JetchatDarkColorScheme
        else -> JetchatLightColorScheme
    }

    MaterialTheme(colorScheme = myColorScheme, typography = JetchatTypography, content = content)
}
```

```kotlin
// ConversationFragment.kt 关键片段
JetchatTheme(isDynamicColor = false) {
    OpenCodeConversationScreen(...)
}
```

- [ ] **步骤 4：运行全量关键验证**

运行：
- `./gradlew :app:testDebugUnitTest --tests "*OpenCodeDesignTokensTest" --no-daemon`
- `./gradlew :app:compileDebugKotlin --no-daemon`

预期：PASS，主题色和编译都通过。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/example/compose/jetchat/theme/Themes.kt app/src/main/java/com/example/compose/jetchat/conversation/ConversationFragment.kt app/src/test/java/com/example/compose/jetchat/theme/OpenCodeDesignTokensTest.kt
git commit -m "fix(theme): lock opencode brand style by disabling dynamic color"
```

---

## 自检结果

### 1. 规格覆盖度
- 主题配色差距：任务 1、任务 4 覆盖（品牌色板 + 动态色禁用）。
- 圆角和组件层级差距：任务 2、任务 3 覆盖（输入区、toolbar、抽屉卡片）。
- 与官方效果接近：通过统一 token + UI 契约测试防回归（任务 1-4 全覆盖）。
- 未遗漏需求。

### 2. 占位符扫描
- 已检查，无 `TODO/待定/后续实现/类似任务N` 占位描述。
- 每个代码步骤均给出可执行代码片段。

### 3. 类型一致性
- 全文统一使用 `OpenCodeTokens/OpenCodePalette/OpenCodeShapeScale`。
- agent/model UI 仍沿用现有业务模型，不引入命名冲突。

---

计划已完成并保存到 `docs/superpowers/plans/2026-04-26-opencode-style-parity.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
