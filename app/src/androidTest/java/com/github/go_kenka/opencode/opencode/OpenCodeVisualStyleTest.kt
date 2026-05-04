package com.github.go_kenka.opencode.opencode

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.go_kenka.opencode.components.JetchatDrawerContent
import com.github.go_kenka.opencode.conversation.OpenCodeTestTags
import com.github.go_kenka.opencode.conversation.OpenCodeConversationScreen
import com.github.go_kenka.opencode.opencode.model.OpenCodeProject
import com.github.go_kenka.opencode.opencode.model.OpenCodeSession
import com.github.go_kenka.opencode.opencode.model.OpenCodeUiState
import com.github.go_kenka.opencode.theme.JetchatTheme
import org.junit.Rule
import org.junit.Test

class OpenCodeVisualStyleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keyComposerTags_exist() {
        val project = OpenCodeProject(
            id = "project-1",
            name = "OpenCode",
            directory = "/tmp/opencode",
        )
        composeTestRule.setContent {
            JetchatTheme {
                OpenCodeConversationScreen(
                    uiState = OpenCodeUiState(
                        projects = listOf(project),
                        selectedProject = project,
                        isConnecting = false,
                    ),
                    onProjectSelected = {},
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
                )
            }
        }

        composeTestRule.onNodeWithTag(OpenCodeTestTags.ComposerContainer).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OpenCodeTestTags.ComposerToolbar).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OpenCodeTestTags.AttachmentButton).assertIsDisplayed()
    }

    @Test
    fun drawerSearchAndSettingsTags_exist() {
        composeTestRule.setContent {
            JetchatTheme {
                JetchatDrawerContent(
                    sessions = listOf(
                        OpenCodeSession(
                            id = "session-1",
                            projectID = "project-1",
                            directory = "/tmp/opencode",
                            title = "会话一",
                            time = 1L,
                        ),
                    ),
                    selectedSessionId = "session-1",
                    onSessionClicked = {},
                    onSettingsClicked = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(OpenCodeTestTags.DrawerSearch).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OpenCodeTestTags.DrawerSettings).assertIsDisplayed()
    }
}
